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
            if hasattr(self, 'recognition') and self.recognition and self.connected:
                try:
                    await self.recognition.stop()
                    # self.ten_env.log_info("[paraformer_asr] Stopped existing recognition connection")
                except Exception as e:
                    self.ten_env.log_warn(f"[paraformer_asr] Error stopping existing recognition: {e}")
                finally:
                    self.connected = False

            # 等待一下再重连
            await asyncio.sleep(0.5)

            # 重新建立连接
            # self.ten_env.log_info("[paraformer_asr] Attempting to start new connection")
            await self.start_connection()
            self.ten_env.log_info("[paraformer_asr] Reconnection completed successfully")

        except Exception as e:
            self.ten_env.log_error(f"[paraformer_asr] Reconnection failed: {e}")
            # 如果重连失败，继续尝试
            if not self.stopped:
                await asyncio.sleep(1.0)  # 等待更长时间再重试
                try:
                    asyncio.create_task(self._handle_reconnect())
                    # self.ten_env.log_info("[paraformer_asr] Retry reconnect task created")
                except Exception as task_e:
                    print(f'Error creating retry reconnect task: {task_e}')
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create retry reconnect task: {task_e}")
        finally:
            self._reconnecting = False

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
        self.ten_env.log_info("[paraformer_asr] start and listen paraformer_asr")

        if not self.config.api_key:
            raise ValueError("api_key is required")
        if not self.config.model:
            raise ValueError("model is required")

        # self.ten_env.log_info(f"[paraformer_asr] Using model: {self.config.model}")
        dashscope.api_key = self.config.api_key

        try:
            extension:ParaformerASRExtension = self
            self._loop = asyncio.get_running_loop()  # 保存事件循环对象
            # self.ten_env.log_info("[paraformer_asr] Got running loop")

            class Callback(RecognitionCallback):
                def on_open(self) -> None:
                    extension.ten_env.log_info('[paraformer_asr] RecognitionCallback open.')
                    extension.connected = True

                def on_close(self) -> None:
                    extension.ten_env.log_info('[paraformer_asr] RecognitionCallback close.')
                    extension.connected = False
                    # 只设置状态，不主动重连
                    # 重连将在下次发送音频时进行

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
            # self.ten_env.log_info("[paraformer_asr] Creating Recognition object")
            self.recognition = Recognition(model=self.config.model,
                                    format='pcm',
                                    sample_rate=16000,
                                    callback=callback)

            # self.ten_env.log_info("[paraformer_asr] Starting recognition")
            self.recognition.start()
            self.connected = True
            # self.ten_env.log_info("[paraformer_asr] Recognition started successfully")

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
                # self.ten_env.log_info("[paraformer_asr] Attempting to reconnect after start failure")
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

        # 只在连接状态异常时记录详细日志
        if not self.connected or not self.recognition:
            self.ten_env.log_info(f"[paraformer_asr] send_audio called - connected: {self.connected}, recognition: {self.recognition is not None}, stopped: {self.stopped}")
            self.ten_env.log_info(f"[paraformer_asr] send_audio - frame size: {len(frame.get_buf())}, session_id: {session_id}")

        # 检查连接状态
        if not self.connected or not self.recognition:
            self.ten_env.log_warn(f"[paraformer_asr] ASR not connected - connected: {self.connected}, recognition: {self.recognition is not None}, attempting to reconnect")
            # 尝试重连
            if not self.stopped:
                try:
                    asyncio.create_task(self._handle_reconnect())
                    self.ten_env.log_info(f"[paraformer_asr] Reconnect task created")
                except Exception as e:
                    print(f'Error creating reconnect task: {e}')
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create reconnect task: {e}")
            else:
                self.ten_env.log_warn(f"[paraformer_asr] Extension is stopped, not attempting reconnect")
            return False

        try:
            self.recognition.send_audio_frame(frame.get_buf())
            return True
        except Exception as e:
            # 检查是否是 WebSocket 连接错误
            if "ClientConnectionResetError" in str(e) or "Cannot write to closing transport" in str(e):
                self.ten_env.log_warn(f"[paraformer_asr] WebSocket connection reset, will reconnect: {e}")
            else:
                self.ten_env.log_error(f"[paraformer_asr] Failed to Paraformer ASR send_audio : {e}")

            # 连接可能已断开，设置状态并尝试重连
            self.connected = False
            if not self.stopped:
                try:
                    asyncio.create_task(self._handle_reconnect())
                    self.ten_env.log_info(f"[paraformer_asr] Reconnect task created after error")
                except Exception as reconnect_e:
                    print(f'Error creating reconnect task in send_audio: {reconnect_e}')
                    self.ten_env.log_error(f"[paraformer_asr] Failed to create reconnect task after error: {reconnect_e}")
            else:
                self.ten_env.log_warn(f"[paraformer_asr] Extension is stopped, not attempting reconnect after error")
            return False

    async def finalize(self, session_id: str | None) -> None:
        raise NotImplementedError(
            "Paraformer ASR does not support finalize operation yet."
        )

    def is_connected(self) -> bool:
        return self.connected

    def input_audio_sample_rate(self) -> int:
        return 16000
