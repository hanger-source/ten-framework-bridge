#
#
# Agora Real Time Engagement
# Created by Wei Hu in 2024-08.
# Copyright (c) 2024 Agora IO. All rights reserved.
#
#
import asyncio
import base64
import json
from enum import Enum
import traceback
import time
import numpy as np
from typing import Iterable, Literal

from ten_runtime import (
    AudioFrame,
    AsyncTenEnv,
    Cmd,
    StatusCode,
    CmdResult,
    Data,
)
from ten_runtime.audio_frame import AudioFrameDataFmt
from ten_ai_base.const import CMD_PROPERTY_RESULT, CMD_TOOL_CALL
from dataclasses import dataclass
from ten_ai_base.config import BaseConfig
from ten_ai_base.chat_memory import (
    ChatMemory,
    EVENT_MEMORY_EXPIRED,
    EVENT_MEMORY_APPENDED,
)
from ten_ai_base.usage import (
    LLMUsage,
    LLMCompletionTokensDetails,
    LLMPromptTokensDetails,
)
from ten_ai_base.types import (
    LLMToolMetadata,
    LLMToolResult,
    LLMChatCompletionContentPartParam,
)
from ten_ai_base.llm import AsyncLLMBaseExtension
from .realtime.connection import RealtimeApiConnection
from .realtime.struct import (
    ItemCreate,
    SessionCreated,
    ItemCreated,
    UserMessageItemParam,
    ResponseCreateParams,
    AssistantMessageItemParam,
    ItemInputAudioTranscriptionCompleted,
    ItemInputAudioTranscriptionFailed,
    ResponseCreated,
    ResponseDone,
    ResponseAudioTranscriptDelta,
    ResponseTextDelta,
    ResponseAudioTranscriptDone,
    ResponseTextDone,
    ResponseOutputItemDone,
    ResponseOutputItemAdded,
    ResponseAudioDelta,
    ResponseAudioDone,
    InputAudioBufferSpeechStarted,
    InputAudioBufferSpeechStopped,
    ResponseFunctionCallArgumentsDone,
    ErrorMessage,
    ItemDelete,
    ItemTruncate,
    SessionUpdate,
    SessionUpdateParams,
    InputAudioTranscription,
    ContentType,
    FunctionCallOutputItemParam,
    ResponseCreate,
    ServerVADUpdateParams,
    SemanticVADUpdateParams,
    AudioFormats
)

from PIL import Image
from io import BytesIO
from base64 import b64encode
import cv2

CMD_IN_FLUSH = "flush"
CMD_IN_ON_USER_JOINED = "on_user_joined"
CMD_IN_ON_USER_LEFT = "on_user_left"
CMD_OUT_FLUSH = "flush"


class Role(str, Enum):
    User = "user"
    Assistant = "assistant"

def rgb2jpegBytes(ten_env: AsyncTenEnv, rgb_data, width, height):
    expected_rgb = width * height * 3
    expected_rgba = width * height * 4
    actual_len = len(rgb_data)
    ten_env.log_info(
        f"rgb_data len={actual_len}, width={width}, height={height}, "
        f"expect RGB={expected_rgb}, expect RGBA={expected_rgba}"
    )
    # 自动判断格式
    if actual_len == width * height * 3:
        # RGB 格式
        pil_image = Image.frombytes("RGB", (width, height), bytes(rgb_data))
    elif actual_len == width * height * 4:
        # RGBA 格式
        pil_image = Image.frombytes("RGBA", (width, height), bytes(rgb_data)).convert("RGB")
    elif actual_len == int(width * height * 1.5):
        # YUV420 格式
        yuv = np.frombuffer(rgb_data, dtype=np.uint8)
        yuv = yuv.reshape((height * 3 // 2, width))
        rgb_array = cv2.cvtColor(yuv, cv2.COLOR_YUV2RGB_I420)
        pil_image = Image.fromarray(rgb_array)
    else:
        ten_env.log_error(f"Unknown image data format: len={actual_len}")
        return None

    pil_image = pil_image.convert("RGB")
    pil_image = resize_image_keep_aspect(pil_image, 512)
    buffered = BytesIO()
    pil_image.save(buffered, format="JPEG")
    jpeg_image_data = buffered.getvalue()
    return jpeg_image_data

def resize_image_keep_aspect(image, max_size=512):
    """
    Resize an image while maintaining its aspect ratio, ensuring the larger dimension is max_size.
    If both dimensions are smaller than max_size, the image is not resized.

    :param image: A PIL Image object
    :param max_size: The maximum size for the larger dimension (width or height)
    :return: A PIL Image object (resized or original)
    """
    # Get current width and height
    width, height = image.size

    # If both dimensions are already smaller than max_size, return the original image
    if width <= max_size and height <= max_size:
        return image

    # Calculate the aspect ratio
    aspect_ratio = width / height

    # Determine the new dimensions
    if width > height:
        new_width = max_size
        new_height = int(max_size / aspect_ratio)
    else:
        new_height = max_size
        new_width = int(max_size * aspect_ratio)

    # Resize the image with the new dimensions
    resized_image = image.resize((new_width, new_height))

    return resized_image

@dataclass
class QwenOmniRealtimeConfig(BaseConfig):
    base_uri: str = "wss://dashscope.aliyuncs.com/api-ws"
    api_key: str = ""
    path: str = "/v1/realtime"
    model: str = "qwen-omni-turbo-realtime"
    language: str = "zh-CN"
    prompt: str = '''我们正在进行一场实时的视频对话。如果你收到了照片，请你将我提供的所有视觉输入都视为一个连续的视频流，
    而不是孤立的静态图片。在你的回复中，请务必避免使用‘照片’、‘图片’或‘图像’这些词语来描述你所看到的内容。\n'''
    temperature: float = 0.5
    max_tokens: int = 1024
    voice: str = "alloy"
    server_vad: bool = True
    audio_out: bool = True
    input_transcript: bool = True
    sample_rate: int = 24000
    vad_type: Literal["server_vad", "semantic_vad"] = "server_vad"
    vad_eagerness: Literal["low", "medium", "high", "auto"] = "auto"
    vad_threshold: float = 0.5
    vad_prefix_padding_ms: int = 300
    vad_silence_duration_ms: int = 500
    vendor: str = ""
    stream_id: int = 0
    dump: bool = False
    greeting: str = ""
    max_history: int = 20
    enable_storage: bool = False

    def build_ctx(self) -> dict:
        return {
            "language": self.language,
            "model": self.model,
        }


class QwenOmniRealtimeExtension(AsyncLLMBaseExtension):

    def __init__(self, name: str):
        super().__init__(name)
        self.ten_env: AsyncTenEnv = None
        self.conn = None
        self.session = None
        self.session_id = None

        self.config: QwenOmniRealtimeConfig = None
        self.stopped: bool = False
        self.connected: bool = False
        self.buffer: bytearray = b""
        self.memory: ChatMemory = None
        self.total_usage: LLMUsage = LLMUsage()
        self.users_count = 0

        self.stream_id: int = 0
        self.remote_stream_id: int = 0
        self.channel_name: str = ""
        self.audio_len_threshold: int = 5120

        self.completion_times = []
        self.connect_times = []
        self.first_token_times = []

        self.buff: bytearray = b""
        self.transcript: str = ""
        self.ctx: dict = {}
        self.input_end = time.time()

        self.image_queue = asyncio.Queue(maxsize=5)

    async def on_init(self, ten_env: AsyncTenEnv) -> None:
        await super().on_init(ten_env)
        ten_env.log_debug("on_init")

    async def on_start(self, ten_env: AsyncTenEnv) -> None:
        await super().on_start(ten_env)
        ten_env.log_debug("on_start")
        self.ten_env = ten_env

        self.loop = asyncio.get_event_loop()

        self.config = await QwenOmniRealtimeConfig.create_async(ten_env=ten_env)
        ten_env.log_info(f"config: {self.config}")

        if not self.config.api_key:
            ten_env.log_error("api_key is required")
            return

        try:
            self.memory = ChatMemory(self.config.max_history)

            if self.config.enable_storage:
                [result, _] = await ten_env.send_cmd(Cmd.create("retrieve"))
                if result.get_status_code() == StatusCode.OK:
                    try:
                        response, _ = result.get_property_string("response")
                        history = json.loads(response)
                        for i in history:
                            self.memory.put(i)
                        ten_env.log_info(f"on retrieve context {history}")
                    except Exception as e:
                        ten_env.log_error(
                            f"Failed to handle retrieve result {e}"
                        )
                else:
                    ten_env.log_warn("Failed to retrieve content")

            self.memory.on(EVENT_MEMORY_EXPIRED, self._on_memory_expired)
            self.memory.on(EVENT_MEMORY_APPENDED, self._on_memory_appended)

            self.ctx = self.config.build_ctx()
            self.ctx["greeting"] = self.config.greeting

            self.conn = RealtimeApiConnection(
                ten_env=ten_env,
                base_uri=self.config.base_uri,
                path=self.config.path,
                api_key=self.config.api_key,
                model=self.config.model,
                vendor=self.config.vendor,
            )
            ten_env.log_info("Finish init client")

            self.loop.create_task(self._loop())
            self.loop.create_task(self._on_video(ten_env))
        except Exception as e:
            traceback.print_exc()
            self.ten_env.log_error(f"Failed to init client {e}")

    async def on_stop(self, ten_env: AsyncTenEnv) -> None:
        await super().on_stop(ten_env)
        ten_env.log_info("on_stop")

        self.stopped = True

    async def on_audio_frame(
        self, _: AsyncTenEnv, audio_frame: AudioFrame
    ) -> None:
        try:
            stream_id, _ = audio_frame.get_property_int("stream_id")
            if self.channel_name == "":
                self.channel_name, _ = audio_frame.get_property_string(
                    "channel"
                )

            if self.remote_stream_id == 0:
                self.remote_stream_id = stream_id

            frame_buf = audio_frame.get_buf()
            self._dump_audio_if_need(frame_buf, Role.User)

            await self._on_audio(frame_buf)
            if not self.config.server_vad:
                self.input_end = time.time()
        except Exception as e:
            traceback.print_exc()
            self.ten_env.log_error(
                f"QwenOmniV2VExtension on audio frame failed {e}"
            )

    async def on_cmd(self, ten_env: AsyncTenEnv, cmd: Cmd) -> None:
        cmd_name = cmd.get_name()
        ten_env.log_debug("on_cmd name {}".format(cmd_name))

        status = StatusCode.OK
        detail = "success"

        if cmd_name == CMD_IN_FLUSH:
            # Will only flush if it is client side vad
            await self._flush()
            await ten_env.send_cmd(Cmd.create(CMD_OUT_FLUSH))
            ten_env.log_info("on flush")
        elif cmd_name == CMD_IN_ON_USER_JOINED:
            self.users_count += 1
            # Send greeting when first user joined
            if self.users_count == 1:
                await self._greeting()
        elif cmd_name == CMD_IN_ON_USER_LEFT:
            self.users_count -= 1
        else:
            # Register tool
            await super().on_cmd(ten_env, cmd)
            return

        cmd_result = CmdResult.create(status, cmd)
        cmd_result.set_property_string("detail", detail)
        await ten_env.return_result(cmd_result)

    # Not support for now
    async def on_data(self, ten_env: AsyncTenEnv, data: Data) -> None:
        pass

    async def _loop(self):
        try:
            start_time = time.time()
            await self.conn.connect()
            self.connect_times.append(time.time() - start_time)
            item_id = ""  # For truncate
            response_id = ""
            content_index = 0
            session_start_ms = int(
                time.time() * 1000
            )  # Use proper timestamp in milliseconds
            flushed = set()

            self.ten_env.log_info("Client loop started")
            async for message in self.conn.listen():
                try:
                    self.ten_env.log_info(f"Received message: {message.type}")
                    match message:
                        case SessionCreated():
                            self.ten_env.log_info(
                                f"Session is created: {message.session}"
                            )
                            self.session_id = message.session.id
                            self.session = message.session
                            await self._update_session()

                            history = self.memory.get()
                            for h in history:
                                if h["role"] == "user":
                                    await self.conn.send_request(
                                        ItemCreate(
                                            item=UserMessageItemParam(
                                                content=[
                                                    {
                                                        "type": ContentType.InputText,
                                                        "text": h["content"],
                                                    }
                                                ]
                                            )
                                        )
                                    )
                                elif h["role"] == "assistant":
                                    await self.conn.send_request(
                                        ItemCreate(
                                            item=AssistantMessageItemParam(
                                                content=[
                                                    {
                                                        "type": ContentType.InputText,
                                                        "text": h["content"],
                                                    }
                                                ]
                                            )
                                        )
                                    )
                            self.ten_env.log_info(
                                f"Finish send history {history}"
                            )
                            self.memory.clear()

                            if not self.connected:
                                self.connected = True
                                await self._greeting()
                        case ItemInputAudioTranscriptionCompleted():
                            self.ten_env.log_info(
                                f"On request transcript {message.transcript}"
                            )
                            self._send_transcript(
                                message.transcript, Role.User, True
                            )
                            self.memory.put(
                                {
                                    "role": "user",
                                    "content": message.transcript,
                                    "id": message.item_id,
                                }
                            )
                        case ItemInputAudioTranscriptionFailed():
                            self.ten_env.log_warn(
                                f"On request transcript failed {message.item_id} {message.error}"
                            )
                        case ItemCreated():
                            self.ten_env.log_info(
                                f"On item created {message.item}"
                            )
                        case ResponseCreated():
                            response_id = message.response.id
                            self.ten_env.log_info(
                                f"On response created {response_id}"
                            )
                        case ResponseDone():
                            msg_resp_id = message.response.id
                            status = message.response.status
                            if msg_resp_id == response_id:
                                response_id = ""
                            self.ten_env.log_info(
                                f"On response done {msg_resp_id} {status} {message.response.usage}"
                            )
                            if message.response.usage:
                                pass
                                # await self._update_usage(message.response.usage)
                        case ResponseAudioTranscriptDelta():
                            self.ten_env.log_info(
                                f"On response transcript delta {message.response_id} {message.output_index} {message.content_index} {message.delta}"
                            )
                            if message.response_id in flushed:
                                self.ten_env.log_warn(
                                    f"On flushed transcript delta {message.response_id} {message.output_index} {message.content_index} {message.delta}"
                                )
                                continue
                            self._send_transcript(
                                message.delta, Role.Assistant, False
                            )
                        case ResponseTextDelta():
                            self.ten_env.log_info(
                                f"On response text delta {message.response_id} {message.output_index} {message.content_index} {message.delta}"
                            )
                            if message.response_id in flushed:
                                self.ten_env.log_warn(
                                    f"On flushed text delta {message.response_id} {message.output_index} {message.content_index} {message.delta}"
                                )
                                continue
                            if item_id != message.item_id:
                                item_id = message.item_id
                                self.first_token_times.append(
                                    time.time() - self.input_end
                                )
                            self._send_transcript(
                                message.delta, Role.Assistant, False
                            )
                        case ResponseAudioTranscriptDone():
                            self.ten_env.log_info(
                                f"On response transcript done {message.output_index} {message.content_index} {message.transcript}"
                            )
                            if message.response_id in flushed:
                                self.ten_env.log_warn(
                                    f"On flushed transcript done {message.response_id}"
                                )
                                continue
                            self.memory.put(
                                {
                                    "role": "assistant",
                                    "content": message.transcript,
                                    "id": message.item_id,
                                }
                            )
                            self.transcript = ""
                            self._send_transcript("", Role.Assistant, True)
                        case ResponseTextDone():
                            self.ten_env.log_info(
                                f"On response text done {message.output_index} {message.content_index} {message.text}"
                            )
                            if message.response_id in flushed:
                                self.ten_env.log_warn(
                                    f"On flushed text done {message.response_id}"
                                )
                                continue
                            self.completion_times.append(
                                time.time() - self.input_end
                            )
                            self.transcript = ""
                            self._send_transcript("", Role.Assistant, True)
                        case ResponseOutputItemDone():
                            self.ten_env.log_info(
                                f"Output item done {message.item}"
                            )
                        case ResponseOutputItemAdded():
                            self.ten_env.log_info(
                                f"Output item added {message.output_index} {message.item}"
                            )
                        case ResponseAudioDelta():
                            if message.response_id in flushed:
                                self.ten_env.log_warn(
                                    f"On flushed audio delta {message.response_id} {message.item_id} {message.content_index}"
                                )
                                continue
                            if item_id != message.item_id:
                                item_id = message.item_id
                                self.first_token_times.append(
                                    time.time() - self.input_end
                                )
                            content_index = message.content_index
                            await self._on_audio_delta(message.delta)
                        case ResponseAudioDone():
                            self.completion_times.append(
                                time.time() - self.input_end
                            )
                        case InputAudioBufferSpeechStarted():
                            self.ten_env.log_info(
                                f"On server listening, in response {response_id}, last item {item_id}"
                            )
                            # Calculate proper truncation time - elapsed milliseconds since session start
                            current_ms = int(time.time() * 1000)
                            end_ms = current_ms - session_start_ms
                            if (
                                item_id and end_ms > 0
                            ):  # Only truncate if we have a valid positive timestamp
                                truncate = ItemTruncate(
                                    item_id=item_id,
                                    content_index=content_index,
                                    audio_end_ms=end_ms,
                                )
                                await self.conn.send_request(truncate)
                            if self.config.server_vad:
                                await self._flush()
                            if response_id and self.transcript:
                                transcript = self.transcript + "[interrupted]"
                                self._send_transcript(
                                    transcript, Role.Assistant, True
                                )
                                self.transcript = ""
                                # memory leak, change to lru later
                                flushed.add(response_id)
                            item_id = ""
                        case InputAudioBufferSpeechStopped():
                            # Only for server vad
                            self.input_end = time.time()
                            # Update session start to properly track relative timing
                            session_start_ms = (
                                int(time.time() * 1000) - message.audio_end_ms
                            )
                            self.ten_env.log_info(
                                f"On server stop listening, audio_end_ms: {message.audio_end_ms}, session_start_ms updated to: {session_start_ms}"
                            )
                        case ResponseFunctionCallArgumentsDone():
                            tool_call_id = message.call_id
                            name = message.name
                            arguments = message.arguments
                            self.ten_env.log_info(f"need to call func {name}")
                            self.loop.create_task(
                                self._handle_tool_call(
                                    tool_call_id, name, arguments
                                )
                            )
                        case ErrorMessage():
                            self.ten_env.log_error(
                                f"Error message received: {message.error}"
                            )
                        case _:
                            self.ten_env.log_debug(
                                f"Not handled message {message}"
                            )
                except Exception as e:
                    traceback.print_exc()
                    self.ten_env.log_error(
                        f"Error processing message: {message} {e}"
                    )

            self.ten_env.log_info("Client loop finished")
        except Exception as e:
            traceback.print_exc()
            self.ten_env.log_error(f"Failed to handle loop {e}")

        # clear so that new session can be triggered
        self.connected = False
        self.remote_stream_id = 0

        if not self.stopped:
            await self.conn.close()
            await asyncio.sleep(0.5)
            self.ten_env.log_info("Reconnect")

            self.conn = RealtimeApiConnection(
                ten_env=self.ten_env,
                base_uri=self.config.base_uri,
                path=self.config.path,
                api_key=self.config.api_key,
                model=self.config.model,
                vendor=self.config.vendor,
            )

            self.loop.create_task(self._loop())

    async def _on_memory_expired(self, message: dict) -> None:
        self.ten_env.log_info(f"Memory expired: {message}")
        item_id = message.get("item_id")
        if item_id:
            await self.conn.send_request(ItemDelete(item_id=item_id))

    async def _on_memory_appended(self, message: dict) -> None:
        self.ten_env.log_info(f"Memory appended: {message}")
        if not self.config.enable_storage:
            return

        role = message.get("role")
        stream_id = self.remote_stream_id if role == Role.User else 0
        try:
            d = Data.create("append")
            d.set_property_string("text", message.get("content"))
            d.set_property_string("role", role)
            d.set_property_int("stream_id", stream_id)
            asyncio.create_task(self.ten_env.send_data(d))
        except Exception as e:
            self.ten_env.log_error(
                f"Error send append_context data {message} {e}"
            )

    async def on_video_frame(self, async_ten_env, video_frame):
        await super().on_video_frame(async_ten_env, video_frame)
        image_data = video_frame.get_buf()
        image_width = video_frame.get_width()
        image_height = video_frame.get_height()

        # Use non-blocking put to avoid memory buildup
        try:
            self.image_queue.put_nowait([image_data, image_width, image_height])
        except asyncio.QueueFull:
            # Drop frames if queue is full to maintain performance
            pass

    async def _on_video(self, _: AsyncTenEnv):
        while True:

            # Process the first frame from the queue
            [image_data, image_width, image_height] = (
                await self.image_queue.get()
            )
            self.video_buff = rgb2jpegBytes(
                self.ten_env, image_data, image_width, image_height
            )
            try:
                if self.video_buff is not None and self.connected:
                    self.ten_env.log_info(f"send image")
                    await self.conn.send_image_data(self.video_buff)
                else:
                    self.ten_env.log_error("Image encode failed, not sending image.")
            except Exception as e:
                self.ten_env.log_error(f"Failed to send image {e}")

            # Skip remaining frames for the second
            while not self.image_queue.empty():
                await self.image_queue.get()

            # Wait for 2 second before processing the next frame
            await asyncio.sleep(2)

    # Direction: IN
    async def _on_audio(self, buff: bytearray):
        self.buff += buff
        # Buffer audio
        if self.connected and len(self.buff) >= self.audio_len_threshold:
            await self.conn.send_audio_data(self.buff)
            self.buff = b""

    async def _update_session(self) -> None:
        tools = []

        def tool_dict(tool: LLMToolMetadata):
            t = {
                "type": "function",
                "name": tool.name,
                "description": tool.description,
                "parameters": {
                    "type": "object",
                    "properties": {},
                    "required": [],
                    "additionalProperties": False,
                },
            }

            for param in tool.parameters:
                t["parameters"]["properties"][param.name] = {
                    "type": param.type,
                    "description": param.description,
                }
                if param.required:
                    t["parameters"]["required"].append(param.name)

            return t

        if self.available_tools:
            tool_prompt = "You have several tools that you can get help from:\n"
            for t in self.available_tools:
                tool_prompt += f"- ***{t.name}***: {t.description}"
            self.ctx["tools"] = tool_prompt
            tools = [tool_dict(t) for t in self.available_tools]
        prompt = self._replace(self.config.prompt)

        self.ten_env.log_info(f"update session {prompt} {tools}")
        vad_params = ServerVADUpdateParams(
            threshold=self.config.vad_threshold,
            prefix_padding_ms=self.config.vad_prefix_padding_ms,
            silence_duration_ms=self.config.vad_silence_duration_ms,
        )
        su = SessionUpdate(
            session=SessionUpdateParams(
                instructions=prompt,
                model=self.config.model,
                tool_choice="auto",
                #   if self.available_tools else "none",
                tools=tools,
                turn_detection=vad_params,
                input_audio_format=AudioFormats.PCM16,
                output_audio_format=AudioFormats.PCM16
            )
        )
        if self.config.audio_out:
            su.session.voice = self.config.voice
            su.session.modalities = ["text", "audio"]
        else:
            su.session.modalities = ["text"]

        if self.config.input_transcript:
            su.session.input_audio_transcription = InputAudioTranscription(
                model='gummy-realtime-v1'
            )
        await self.conn.send_request(su)

    async def on_tools_update(
        self, _: AsyncTenEnv, tool: LLMToolMetadata
    ) -> None:
        """Called when a new tool is registered. Implement this method to process the new tool."""
        self.ten_env.log_info(f"on tools update {tool}")
        # await self._update_session()

    def _replace(self, prompt: str) -> str:
        result = prompt
        for token, value in self.ctx.items():
            result = result.replace("{" + token + "}", value)
        return result

    # Direction: OUT
    async def _on_audio_delta(self, delta: bytes) -> None:
        audio_data = base64.b64decode(delta)
        self.ten_env.log_debug(
            f"on_audio_delta audio_data len {len(audio_data)} samples {len(audio_data) // 2}"
        )
        self._dump_audio_if_need(audio_data, Role.Assistant)

        f = AudioFrame.create("pcm_frame")
        f.set_sample_rate(self.config.sample_rate)
        f.set_bytes_per_sample(2)
        f.set_number_of_channels(1)
        f.set_data_fmt(AudioFrameDataFmt.INTERLEAVE)
        f.set_samples_per_channel(len(audio_data) // 2)
        f.alloc_buf(len(audio_data))
        buff = f.lock_buf()
        buff[:] = audio_data
        f.unlock_buf(buff)
        await self.ten_env.send_audio_frame(f)

    def _send_transcript(
        self, content: str, role: Role, is_final: bool
    ) -> None:
        def is_punctuation(char):
            if char in [",", "，", ".", "。", "?", "？", "!", "！"]:
                return True
            return False

        def parse_sentences(sentence_fragment, content):
            sentences = []
            current_sentence = sentence_fragment
            for char in content:
                current_sentence += char
                if is_punctuation(char):
                    # Check if the current sentence contains non-punctuation characters
                    stripped_sentence = current_sentence
                    if any(c.isalnum() for c in stripped_sentence):
                        sentences.append(stripped_sentence)
                    current_sentence = ""  # Reset for the next sentence

            remain = current_sentence  # Any remaining characters form the incomplete sentence
            return sentences, remain

        def send_data(
            ten_env: AsyncTenEnv,
            sentence: str,
            stream_id: int,
            role: str,
            is_final: bool,
        ):
            try:
                d = Data.create("text_data")
                d.set_property_string("text", sentence)
                d.set_property_bool("end_of_segment", is_final)
                d.set_property_string("role", role)
                d.set_property_int("stream_id", stream_id)
                ten_env.log_info(
                    f"send transcript text [{sentence}] stream_id {stream_id} is_final {is_final} end_of_segment {is_final} role {role}"
                )
                asyncio.create_task(ten_env.send_data(d))
            except Exception as e:
                ten_env.log_error(
                    f"Error send text data {role}: {sentence} {is_final} {e}"
                )

        stream_id = self.remote_stream_id if role == Role.User else 0
        try:
            if role == Role.Assistant and not is_final:
                sentences, self.transcript = parse_sentences(
                    self.transcript, content
                )
                for s in sentences:
                    send_data(self.ten_env, s, stream_id, role, is_final)
            else:
                send_data(self.ten_env, content, stream_id, role, is_final)
        except Exception as e:
            self.ten_env.log_error(
                f"Error send text data {role}: {content} {is_final} {e}"
            )

    def _dump_audio_if_need(self, buf: bytearray, role: Role) -> None:
        if not self.config.dump:
            return

        with open(
            "{}_{}.pcm".format(role, self.channel_name), "ab"
        ) as dump_file:
            dump_file.write(buf)

    async def _handle_tool_call(
        self, tool_call_id: str, name: str, arguments: str
    ) -> None:
        self.ten_env.log_info(
            f"_handle_tool_call {tool_call_id} {name} {arguments}"
        )
        cmd: Cmd = Cmd.create(CMD_TOOL_CALL)
        cmd.set_property_string("name", name)
        cmd.set_property_from_json("arguments", arguments)
        [result, _] = await self.ten_env.send_cmd(cmd)

        tool_response = ItemCreate(
            item=FunctionCallOutputItemParam(
                call_id=tool_call_id,
                output='{"success":false}',
            )
        )
        if result.get_status_code() == StatusCode.OK:
            r, _ = result.get_property_to_json(CMD_PROPERTY_RESULT)
            tool_result: LLMToolResult = json.loads(r)

            result_content = tool_result["content"]
            tool_response.item.output = json.dumps(
                self._convert_to_content_parts(result_content)
            )
            self.ten_env.log_info(f"tool_result: {tool_call_id} {tool_result}")
        else:
            self.ten_env.log_error("Tool call failed")

        await self.conn.send_request(tool_response)
        await self.conn.send_request(ResponseCreate())
        self.ten_env.log_info(f"_remote_tool_call finish {name} {arguments}")

    def _greeting_text(self) -> str:
        text = "Hi, there."
        if self.config.language == "zh-CN":
            text = "你好。"
        elif self.config.language == "ja-JP":
            text = "こんにちは"
        elif self.config.language == "ko-KR":
            text = "안녕하세요"
        return text

    def _convert_tool_params_to_dict(self, tool: LLMToolMetadata):
        json_dict = {"type": "object", "properties": {}, "required": []}

        for param in tool.parameters:
            json_dict["properties"][param.name] = {
                "type": param.type,
                "description": param.description,
            }
            if param.required:
                json_dict["required"].append(param.name)

        return json_dict

    def _convert_to_content_parts(
        self, content: Iterable[LLMChatCompletionContentPartParam]
    ):
        content_parts = []

        if isinstance(content, str):
            content_parts.append({"type": "text", "text": content})
        else:
            for part in content:
                # Only text content is supported currently for v2v model
                if part["type"] == "text":
                    content_parts.append(part)
        return content_parts

    async def _greeting(self) -> None:
        if self.connected and self.users_count == 1:
            text = self._greeting_text()
            if self.config.greeting:
                text = "Say '" + self.config.greeting + "' to me."
            self.ten_env.log_info(f"send greeting {text}")
            # await self.conn.send_request(
            #     ItemCreate(
            #         item=UserMessageItemParam(
            #             content=[{"type": ContentType.InputText, "text": text}]
            #         )
            #     )
            # )
            await self.conn.send_request(ResponseCreate(
                response=ResponseCreateParams(
                    instructions=f"{self.config.prompt + text}",
                    modalities=["text", "audio"]
                )
            ))

    async def _flush(self) -> None:
        try:
            c = Cmd.create("flush")
            await self.ten_env.send_cmd(c)
        except Exception:
            self.ten_env.log_error("Error flush")

    async def _update_usage(self, usage: dict) -> None:
        self.total_usage.completion_tokens += usage.get("output_tokens") or 0
        self.total_usage.prompt_tokens += usage.get("input_tokens") or 0
        self.total_usage.total_tokens += usage.get("total_tokens") or 0
        if not self.total_usage.completion_tokens_details:
            self.total_usage.completion_tokens_details = (
                LLMCompletionTokensDetails()
            )
        if not self.total_usage.prompt_tokens_details:
            self.total_usage.prompt_tokens_details = LLMPromptTokensDetails()

        if usage.get("output_token_details"):
            self.total_usage.completion_tokens_details.accepted_prediction_tokens += usage[
                "output_token_details"
            ].get(
                "text_tokens"
            )
            self.total_usage.completion_tokens_details.audio_tokens += usage[
                "output_token_details"
            ].get("audio_tokens")

        if usage.get("input_token_details:"):
            self.total_usage.prompt_tokens_details.audio_tokens += usage[
                "input_token_details"
            ].get("audio_tokens")
            self.total_usage.prompt_tokens_details.cached_tokens += usage[
                "input_token_details"
            ].get("cached_tokens")
            self.total_usage.prompt_tokens_details.text_tokens += usage[
                "input_token_details"
            ].get("text_tokens")

        self.ten_env.log_info(f"total usage: {self.total_usage}")

        data = Data.create("llm_stat")
        data.set_property_from_json(
            "usage", json.dumps(self.total_usage.model_dump())
        )
        if (
            self.connect_times
            and self.completion_times
            and self.first_token_times
        ):
            data.set_property_from_json(
                "latency",
                json.dumps(
                    {
                        "connection_latency_95": np.percentile(
                            self.connect_times, 95
                        ),
                        "completion_latency_95": np.percentile(
                            self.completion_times, 95
                        ),
                        "first_token_latency_95": np.percentile(
                            self.first_token_times, 95
                        ),
                        "connection_latency_99": np.percentile(
                            self.connect_times, 99
                        ),
                        "completion_latency_99": np.percentile(
                            self.completion_times, 99
                        ),
                        "first_token_latency_99": np.percentile(
                            self.first_token_times, 99
                        ),
                    }
                ),
            )
        asyncio.create_task(self.ten_env.send_data(data))

    async def on_call_chat_completion(self, async_ten_env, **kargs):
        raise NotImplementedError

    async def on_data_chat_completion(self, async_ten_env, **kargs):
        raise NotImplementedError
