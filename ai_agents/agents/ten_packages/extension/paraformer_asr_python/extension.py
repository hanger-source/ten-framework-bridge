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

        self.connected = False
        self.stopped = False  # 添加停止状态跟踪
        self.config: ParaformerASRConfig
        self.recognition: Recognition

    async def on_init(self, ten_env: AsyncTenEnv) -> None:
        await super().on_init(ten_env)
        ten_env.log_debug("on_init")

        self.config = await ParaformerASRConfig.create_async(ten_env=ten_env)
        ten_env.log_info(f"config: {self.config}")

    async def _handle_reconnect(self):
        # 检查是否已经在重连
        if hasattr(self, '_reconnecting') and self._reconnecting:
            self.ten_env.log_debug("Reconnection already in progress, skipping")
            return

        self._reconnecting = True
        self.ten_env.log_info("Starting reconnection process")

        await asyncio.sleep(0.2)  # Adjust the sleep time as needed

    async def on_cmd(self, ten_env: AsyncTenEnv, cmd: Cmd) -> None:
        cmd_json = cmd.to_json()
        ten_env.log_info(f"on_cmd json: {cmd_json}")

        cmd_result = CmdResult.create(StatusCode.OK, cmd)
        cmd_result.set_property_string("detail", "success")
        await ten_env.return_result(cmd_result)

        try:
            await self.stop_connection()
        except Exception as e:
            self.ten_env.log_warn(f"Error during stop_connection: {e}")

        try:
            await self.start_connection()
            self.ten_env.log_info("Reconnection successful")
        except Exception as e:
            self.ten_env.log_error(f"Error during start_connection: {e}")
            # 如果重连失败，尝试再次重连
            if not self.stopped:
                asyncio.create_task(self._handle_reconnect())
        finally:
            self._reconnecting = False

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
        self.ten_env.log_info("start and listen paraformer_asr")

        if not self.config.api_key:
            raise ValueError("api_key is required")
        if not self.config.model:
            raise ValueError("model is required")
        dashscope.api_key = self.config.api_key
        try:
            extension:ParaformerASRExtension = self
            self._loop = asyncio.get_running_loop()  # 保存事件循环对象
            class Callback(RecognitionCallback):
                def on_open(self) -> None:
                    extension.ten_env.log_info('RecognitionCallback open.')
                    extension.connected = True

                def on_close(self) -> None:
                    extension.ten_env.log_info('RecognitionCallback close.')
                    extension.connected = False
                    # 只设置状态，不主动重连
                    # 重连将在下次发送音频时进行

                def on_event(self, result: RecognitionResult) -> None:
                    sentence = result.get_sentence()
                    extension.ten_env.log_info(f'RecognitionCallback sentence. {sentence}')
                    if sentence:
                        extension._loop.call_soon_threadsafe(
                            asyncio.create_task,
                            extension._send_sentence(
                                sentence['text'], sentence['sentence_end'],
                                sentence['begin_time'], sentence['end_time'])
                        )


            callback = Callback()
            self.recognition = Recognition(model=self.config.model,
                                    format='pcm',
                                    sample_rate=16000,
                                    callback=callback)
            self.recognition.start()
            self.connected = True
        except Exception as e:
            self.ten_env.log_error(f"Failed to start Paraformer ASR : {e}")
            error_message = ErrorMessage(
                code=1,
                message=str(e),
                turn_id=0,
                module=ModuleType.STT,
            )
            await self.send_asr_error(error_message, None)
            if not self.stopped:
                # If the extension is not stopped, attempt to reconnect
                await self._handle_reconnect()

    async def stop_connection(self) -> None:
        self.stopped = True  # 标记为主动停止
        if self.recognition and self.connected:
            try:
                await self.recognition.stop()
                self.ten_env.log_info("Recognition stopped successfully")
            except Exception as e:
                self.ten_env.log_warn(f"Error stopping recognition: {e}")
            finally:
                self.connected = False

    async def send_audio(
        self, frame: AudioFrame, session_id: str | None
    ) -> bool:
        self.session_id = session_id

        # 检查连接状态
        if not self.connected or not self.recognition:
            self.ten_env.log_warn("Paraformer ASR not connected, attempting to reconnect")
            # 尝试重连
            if not self.stopped:
                asyncio.create_task(self._handle_reconnect())
            return False

        try:
            self.recognition.send_audio_frame(frame.get_buf())
            return True
        except Exception as e:
            self.ten_env.log_error(f"Failed to Paraformer ASR send_audio : {e}")
            # 连接可能已断开，设置状态并尝试重连
            self.connected = False
            if not self.stopped:
                asyncio.create_task(self._handle_reconnect())
            return False

    async def finalize(self, session_id: str | None) -> None:
        raise NotImplementedError(
            "Paraformer ASR does not support finalize operation yet."
        )

    def is_connected(self) -> bool:
        return self.connected

    def input_audio_sample_rate(self) -> int:
        return 16000
