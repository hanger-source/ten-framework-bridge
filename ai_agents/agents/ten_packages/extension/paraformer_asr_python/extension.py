import asyncio
from ten_ai_base.asr import AsyncASRBaseExtension
from ten_ai_base.message import ErrorMessage, ModuleType
from ten_ai_base.transcription import UserTranscription
from ten_runtime import (
    AsyncTenEnv,
    Cmd,
    AudioFrame,
    StatusCode,
    CmdResult,
)
from ten_ai_base.config import BaseConfig
import dashscope
from dashscope.audio.asr import (Recognition, RecognitionCallback,
                                 RecognitionResult)
from dataclasses import dataclass, field


@dataclass
class ParaformerASRConfig(BaseConfig):
    api_key: str = ""
    model: str = "paraformer-realtime-v2"


class ParaformerASRExtension(AsyncASRBaseExtension):
    def __init__(self, name: str):
        super().__init__(name)

        self.stopped = False  # 添加停止状态跟踪
        self.config: ParaformerASRConfig
        self.recognition: Recognition

    async def on_init(self, ten_env: AsyncTenEnv) -> None:
        await super().on_init(ten_env)
        ten_env.log_debug("[paraformer_asr] on_init")

        self.config = await ParaformerASRConfig.create_async(ten_env=ten_env)
        ten_env.log_info(f"[paraformer_asr] config: {self.config}")

        # 添加初始化后的状态日志
        # ten_env.log_info(f"[paraformer_asr] Initial state - connected: {self.connected}, recognition: {self.recognition is not None}, stopped: {self.stopped}")

    async def _handle_reconnect(self):
        # 检查是否已经在重连
        if hasattr(self, '_reconnecting') and self._reconnecting:
            self.ten_env.log_debug("[paraformer_asr] Reconnection already in progress, skipping")
            return

        self._reconnecting = True
        self.ten_env.log_info("[paraformer_asr] Starting reconnection process")

        try:
            # 先停止现有连接
            if hasattr(self, 'recognition') and self.recognition:
                try:
                    await self.recognition.stop()
                except BaseException as e:
                    self.ten_env.log_warn(f"[paraformer_asr] Error stopping existing recognition: {e}")

            # 等待一下再重连
            await asyncio.sleep(0.5)

            # 重新建立连接
            await self.start_connection()
            self.ten_env.log_info("[paraformer_asr] Reconnection completed successfully")

            # 重连成功，重置状态
            self._reconnecting = False

        except BaseException as e:
            import traceback
            self.ten_env.log_error(f"[paraformer_asr] Reconnection failed: {e}")
            self.ten_env.log_error(f"[paraformer_asr] Reconnection traceback: {traceback.format_exc()}")
            # 如果重连失败，继续尝试
            if not self.stopped:
                await asyncio.sleep(1.0)  # 等待更长时间再重试
                try:
                    # 先重置状态，然后创建新的重连任务
                    self._reconnecting = False
                    asyncio.create_task(self._handle_reconnect())
                except BaseException as task_e:
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create retry reconnect task: {task_e}")
                    self._reconnecting = False
            else:
                # 扩展已停止，重置状态
                self._reconnecting = False

    async def on_cmd(self, ten_env: AsyncTenEnv, cmd: Cmd) -> None:
        cmd_json = cmd.to_json()
        ten_env.log_info(f"[paraformer_asr] on_cmd json: {cmd_json}")

        cmd_result = CmdResult.create(StatusCode.OK, cmd)
        cmd_result.set_property_string("detail", "success")
        await ten_env.return_result(cmd_result)

    async def _send_sentence(self, text:str, is_final:bool, begin_time:int, end_time:int):
        if begin_time is None or end_time is None:
            duration_ms = 0
        else:
            duration_ms = end_time - begin_time
        transcription = UserTranscription(
            text=text,
            final=is_final,
            start_ms=begin_time,
            duration_ms=duration_ms,
            language="zh-CN",
            metadata={
                "session_id": self.session_id,
            },
            words=[],
        )
        await self.send_asr_transcription(transcription)

    async def start_connection(self) -> None:
        self.ten_env.log_info("[paraformer_asr] start and listen paraformer_asr")

        if not self.config.api_key:
            raise ValueError("api_key is required")
        if not self.config.model:
            raise ValueError("model is required")

        self.ten_env.log_info(f"[paraformer_asr] Using model: {self.config.model}")
        dashscope.api_key = self.config.api_key

        try:
            extension:ParaformerASRExtension = self
            self._loop = asyncio.get_running_loop()  # 保存事件循环对象
            self.ten_env.log_info("[paraformer_asr] Got running loop")

            class Callback(RecognitionCallback):
                def on_open(self) -> None:
                    extension.ten_env.log_info('[paraformer_asr] RecognitionCallback open.')

                def on_close(self) -> None:
                    extension.ten_env.log_info('[paraformer_asr] RecognitionCallback close.')

                def on_event(self, result: RecognitionResult) -> None:
                    sentence = result.get_sentence()
                    extension.ten_env.log_info(f'[paraformer_asr] RecognitionCallback sentence. {sentence}')
                    if sentence:
                        extension._loop.call_soon_threadsafe(
                            asyncio.create_task,
                            extension._send_sentence(
                                sentence['text'], sentence['sentence_end'],
                                sentence['begin_time'], sentence['end_time'])
                        )

            callback = Callback()
            self.ten_env.log_info("[paraformer_asr] Creating Recognition object")
            self.recognition = Recognition(model=self.config.model,
                                    format='pcm',
                                    sample_rate=16000,
                                    callback=callback)

            self.ten_env.log_info("[paraformer_asr] Starting recognition")
            self.recognition.start()
            self.ten_env.log_info("[paraformer_asr] Recognition started successfully")

        except Exception as e:
            self.ten_env.log_error(f"[paraformer_asr] Failed to start Paraformer ASR : {e}")
            error_message = ErrorMessage(
                code=1,
                message=str(e),
                turn_id=0,
                module=ModuleType.STT,
            )
            await self.send_asr_error(error_message, None)
            if not self.stopped:
                # If the extension is not stopped, attempt to reconnect
                self.ten_env.log_info("[paraformer_asr] Attempting to reconnect after start failure")
                await self._handle_reconnect()

    async def stop_connection(self) -> None:
        self.stopped = True  # 标记为主动停止
        if self.recognition:
            try:
                await self.recognition.stop()
                self.ten_env.log_info("Recognition stopped successfully")
            except Exception as e:
                self.ten_env.log_warn(f"Error stopping recognition: {e}")

    async def send_audio(
        self, frame: AudioFrame, session_id: str | None
    ) -> bool:
        self.session_id = session_id

        # 简化逻辑：直接尝试发送音频，如果失败就重连
        if not self.recognition:
            self.ten_env.log_warn(f"[paraformer_asr] No recognition object, attempting to reconnect")
            if not self.stopped:
                try:
                    asyncio.create_task(self._handle_reconnect())
                except BaseException as e:
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create reconnect task: {e}")
            return False

        try:
            # 直接尝试发送音频，不依赖 is_connected 状态
            self.recognition.send_audio_frame(frame.get_buf())
            return True
        except BaseException as e:
            import traceback
            # 检查是否是 WebSocket 连接错误
            if "ClientConnectionResetError" in str(e) or "Cannot write to closing transport" in str(e):
                self.ten_env.log_warn(f"[paraformer_asr] WebSocket connection reset, will reconnect: {e}")
            elif "Speech recognition has stopped" in str(e):
                self.ten_env.log_warn(f"[paraformer_asr] Speech recognition stopped, will reconnect: {e}")
            else:
                self.ten_env.log_error(f"[paraformer_asr] Failed to Paraformer ASR send_audio : {e}")
                self.ten_env.log_error(f"[paraformer_asr] Exception traceback: {traceback.format_exc()}")

            # 连接已断开，尝试重连，但不改变 connected 状态（避免框架认为扩展失效）
            if not self.stopped:
                try:
                    asyncio.create_task(self._handle_reconnect())
                except BaseException as reconnect_e:
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create reconnect task after error: {reconnect_e}")
            else:
                self.ten_env.log_warn(f"[paraformer_asr] Extension is stopped, not attempting reconnect after error")
            return False

    async def finalize(self, session_id: str | None) -> None:
        raise NotImplementedError(
            "Paraformer ASR does not support finalize operation yet."
        )

    def is_connected(self) -> bool:
        # 基于 recognition 对象的存在性来判断连接状态
        return self.recognition is not None and not self.stopped

    def input_audio_sample_rate(self) -> int:
        return 16000
