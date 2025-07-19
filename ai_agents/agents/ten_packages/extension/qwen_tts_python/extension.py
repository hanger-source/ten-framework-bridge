#
# This file is part of TEN Framework, an open source project.
# Licensed under the Apache License, Version 2.0.
# See the LICENSE file for more information.
#
import dashscope
import requests
import traceback
import base64
from dataclasses import dataclass
from datetime import datetime
from typing import AsyncIterator
from ten_ai_base.transcription import AssistantTranscription
from ten_ai_base.tts import AsyncTTSBaseExtension
from ten_runtime import (
    AudioFrame,
    AsyncTenEnv,
)
from ten_runtime.audio_frame import AudioFrameDataFmt
from ten_ai_base.config import BaseConfig


@dataclass
class QwenTTSTTSConfig(BaseConfig):
    api_key: str = ""
    model: str = "qwen-tts"
    voice: str = "Cherry"


class QwenTTSExtension(AsyncTTSBaseExtension):
    def __init__(self, name: str):
        super().__init__(name)
        self.config: QwenTTSTTSConfig

    async def on_init(self, ten_env: AsyncTenEnv) -> None:
        await super().on_init(ten_env)
        ten_env.log_debug("on_init")

        self.config = await QwenTTSTTSConfig.create_async(ten_env=ten_env)

    async def on_start(self, ten_env: AsyncTenEnv) -> None:
        await super().on_start(ten_env)
        ten_env.log_debug("on_start")

        if not self.config.api_key:
            raise ValueError("api_key is required")

    async def on_stop(self, ten_env: AsyncTenEnv) -> None:
        await super().on_stop(ten_env)
        ten_env.log_debug("on_stop")

    async def on_deinit(self, ten_env: AsyncTenEnv) -> None:
        await super().on_deinit(ten_env)
        ten_env.log_debug("on_deinit")

    # Direction: OUT
    async def _on_audio_delta(self, ten_env: AsyncTenEnv, audio_data: bytes) -> None:
        ten_env.log_debug(
            f"on_audio_delta audio_data len {len(audio_data)} samples {len(audio_data) // 2}"
        )

        f = AudioFrame.create("pcm_frame")
        f.set_sample_rate(24000)
        f.set_bytes_per_sample(2)
        f.set_number_of_channels(1)
        f.set_sample_rate(24000)
        f.set_data_fmt(AudioFrameDataFmt.INTERLEAVE)
        f.set_samples_per_channel(len(audio_data) // 2)
        f.alloc_buf(len(audio_data))
        buff = f.lock_buf()
        buff[:] = audio_data
        f.unlock_buf(buff)
        await ten_env.send_audio_frame(f)

    async def on_request_tts(
        self, ten_env: AsyncTenEnv, t: AssistantTranscription
    ) -> None:
        try:
            stream=True

            if not t.text:
                return

            ten_env.log_debug(f"TTS text {t.text}")

            responses = dashscope.audio.qwen_tts.SpeechSynthesizer.call(
                model=self.config.model,
                api_key=self.config.api_key,
                text=t.text,
                voice=self.config.voice,
                stream=stream
            )

            if stream:
                for response in responses:
                    if response is None:
                        ten_env.log_error("TTS response is None, skip.")
                        continue
                    else:
                        audio_string = response["output"]["audio"]["data"]
                        if audio_string:
                            audio_data = base64.b64decode(audio_string.encode())
                            await self._on_audio_delta(ten_env, audio_data)
            else:
                if responses is None:
                    ten_env.log_error("TTS responses is None, skip.")
                else:
                    audio_url = responses["output"]["audio"]["url"]
                    response = requests.get(audio_url)
                    response.raise_for_status()
                    wav_data_bytes = response.content
                    if wav_data_bytes:
                        await self._on_audio_delta(ten_env, wav_data_bytes)

        except Exception:
            ten_env.log_error(
                f"on_request_tts failed: {traceback.format_exc()}"
            )

    async def on_cancel_tts(self, ten_env: AsyncTenEnv) -> None:
        return await super().on_cancel_tts(ten_env)
