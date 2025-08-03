#
# This file is part of TEN Framework, an open source project.
# Licensed under the Apache License, Version 2.0.
# See the LICENSE file for more information.
#
import dashscope
import requests
import traceback
import base64
import emoji
import markdown # pip install markdown
from bs4 import BeautifulSoup # pip install beautifulsoup4

from dataclasses import dataclass
from datetime import datetime
from typing import AsyncIterator
from ten_ai_base.transcription import AssistantTranscription
from ten_ai_base.tts import AsyncTTSBaseExtension
from ten_runtime import (
    AudioFrame,
    Data,
    AsyncTenEnv,
)
from ten_runtime.audio_frame import AudioFrameDataFmt
from ten_ai_base.config import BaseConfig

# 配置连接池
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 配置连接池参数
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

def md_to_text(md):
    html = markdown.markdown(md)
    soup = BeautifulSoup(html, features='html.parser')
    return soup.get_text()

@dataclass
class QwenTTSTTSConfig(BaseConfig):
    api_key: str = ""
    model: str = "qwen-tts"
    voice: str = "Cherry"


class QwenTTSExtension(AsyncTTSBaseExtension):
    def __init__(self, name: str):
        super().__init__(name)
        self.config: QwenTTSTTSConfig
        self.session = None

    async def on_init(self, ten_env: AsyncTenEnv) -> None:
        await super().on_init(ten_env)
        # ten_env.log_debug("on_init")

        self.config = await QwenTTSTTSConfig.create_async(ten_env=ten_env)

    async def on_start(self, ten_env: AsyncTenEnv) -> None:
        await super().on_start(ten_env)
        # ten_env.log_debug("on_start")

        if not self.config.api_key:
            raise ValueError("api_key is required")

        # 配置连接池
        self.session = requests.Session()
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
        )
        adapter = HTTPAdapter(
            max_retries=retry_strategy,
            pool_connections=10,
            pool_maxsize=20
        )
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    async def on_stop(self, ten_env: AsyncTenEnv) -> None:
        await super().on_stop(ten_env)
        # ten_env.log_debug("on_stop")

        # 清理连接池
        if self.session:
            self.session.close()
            self.session = None

    async def on_deinit(self, ten_env: AsyncTenEnv) -> None:
        await super().on_deinit(ten_env)
        # ten_env.log_debug("on_deinit")

    # Direction: OUT
    async def _on_audio_delta(self, ten_env: AsyncTenEnv, audio_data: bytes) -> None:
        # ten_env.log_debug(
        #     f"on_audio_delta audio_data len {len(audio_data)} samples {len(audio_data) // 2}"
        # )

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

            clean_text = emoji.replace_emoji(t.text, replace='')
            clean_text=md_to_text(clean_text)
            ten_env.log_debug(f"TTS text {t.text} {clean_text}")

            if not clean_text:
                return

            responses = dashscope.audio.qwen_tts.SpeechSynthesizer.call(
                model=self.config.model,
                api_key=self.config.api_key,
                text=clean_text,
                voice=self.config.voice,
                stream=stream
            )

            if stream:
                for response in responses:
                    if response is None:
                        ten_env.log_error("TTS response is None, skip.")
                        continue

                    # 检查response结构
                    if not isinstance(response, dict):
                        ten_env.log_error(f"TTS response is not dict: {type(response)}")
                        continue

                    # 安全地访问嵌套字典
                    try:
                        output = response.get("output")
                        if output is None:
                            ten_env.log_error("TTS response missing 'output' field")
                            continue

                        audio = output.get("audio")
                        if audio is None:
                            ten_env.log_error("TTS response missing 'audio' field")
                            continue

                        audio_string = audio.get("data")
                        if audio_string:
                            audio_data = base64.b64decode(audio_string.encode())
                            await self._on_audio_delta(ten_env, audio_data)
                        else:
                            ten_env.log_debug("TTS audio data is empty")
                    except Exception as e:
                        ten_env.log_error(f"Error processing TTS response: {e}")
                        continue
            else:
                if responses is None:
                    ten_env.log_error("TTS responses is None, skip.")
                    return

                # 安全地访问非流式响应
                try:
                    if not isinstance(responses, dict):
                        ten_env.log_error(f"TTS responses is not dict: {type(responses)}")
                        return

                    output = responses.get("output")
                    if output is None:
                        ten_env.log_error("TTS responses missing 'output' field")
                        return

                    audio = output.get("audio")
                    if audio is None:
                        ten_env.log_error("TTS responses missing 'audio' field")
                        return

                    audio_url = audio.get("url")
                    if not audio_url:
                        ten_env.log_error("TTS responses missing 'url' field")
                        return

                    # 使用配置的 session 而不是直接使用 requests
                    if self.session:
                        response = self.session.get(audio_url, timeout=30)
                    else:
                        response = requests.get(audio_url, timeout=30)
                    response.raise_for_status()
                    wav_data_bytes = response.content
                    if wav_data_bytes:
                        await self._on_audio_delta(ten_env, wav_data_bytes)
                    else:
                        ten_env.log_error("Downloaded audio data is empty")
                except Exception as e:
                    ten_env.log_error(f"Error processing non-streaming TTS response: {e}")

        except Exception:
            ten_env.log_error(
                f"on_request_tts failed: {traceback.format_exc()}"
            )

    async def on_cancel_tts(self, ten_env: AsyncTenEnv) -> None:
        return await super().on_cancel_tts(ten_env)
