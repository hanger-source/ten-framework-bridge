# -- coding: utf-8 --

import asyncio
import base64
import json
import os
import aiohttp

from ten_runtime import AsyncTenEnv

from typing import Any, AsyncGenerator
from .struct import (
    InputImageBufferAppend,
    InputAudioBufferAppend,
    ClientToServerMessage,
    ServerToClientMessage,
    parse_server_message,
    to_json,
)

DEFAULT_VIRTUAL_MODEL = "qwen-omni-turbo-realtime"

VENDOR_QWEN = "qwen"


def smart_str(s: str, max_field_len: int = 128) -> str:
    """parse string as json, truncate data field to 128 characters, reserialize"""
    try:
        data = json.loads(s)
        if "delta" in data:
            key = "delta"
        elif "audio" in data:
            key = "audio"
        else:
            return s

        if len(data[key]) > max_field_len:
            data[key] = data[key][:max_field_len] + "..."
        return json.dumps(data)
    except json.JSONDecodeError:
        return s


class RealtimeApiConnection:
    def __init__(
        self,
        ten_env: AsyncTenEnv,
        base_uri: str,
        api_key: str | None = None,
        path: str = "/v1/realtime",
        model: str = DEFAULT_VIRTUAL_MODEL,
        vendor: str = "",
        verbose: bool = True,
    ):
        self.ten_env = ten_env
        self.vendor = vendor
        self.url = f"{base_uri}{path}"
        if "model=" not in self.url:
            self.url += f"?model={model}"

        self.api_key = api_key
        self.websocket: aiohttp.ClientWebSocketResponse | None = None
        self.verbose = verbose
        self.session = aiohttp.ClientSession()

    async def __aenter__(self) -> "RealtimeApiConnection":
        await self.connect()
        return self

    async def __aexit__(
        self, exc_type: Any, exc_value: Any, traceback: Any
    ) -> bool:
        await self.close()
        return False

    async def connect(self):
        headers = {}
        auth = None
        if self.vendor == VENDOR_QWEN:
            headers = {"Authorization": f"Bearer {self.api_key or ''}"}
        elif not self.vendor:
            auth = aiohttp.BasicAuth("", self.api_key) if self.api_key else None
            headers = {"OpenAI-Beta": "realtime=v1"}

        # 新增：打印 connect 请求参数
        if self.ten_env:
            self.ten_env.log_info(f"[connect] url: {self.url}")
            self.ten_env.log_info(f"[connect] headers: {json.dumps(headers, ensure_ascii=False)}")
            self.ten_env.log_info(f"[connect] auth: {auth}")

        self.websocket = await self.session.ws_connect(
            url=self.url,
            # auth=auth,
            headers=headers,
        )

    async def send_image_data(self, image_data: bytes):
        """image_data is assumed to be raw RGBA bytes, shape (width x height x 4), 8 bits per channel"""
        base64_image_data = base64.b64encode(image_data).decode("utf-8")
        message = InputImageBufferAppend(image=base64_image_data)
        await self.send_request(message)

    async def send_audio_data(self, audio_data: bytes):
        """audio_data is assumed to be pcm16 24kHz mono little-endian"""
        base64_audio_data = base64.b64encode(audio_data).decode("utf-8")
        message = InputAudioBufferAppend(audio=base64_audio_data)
        await self.send_request(message)

    async def send_request(self, message: ClientToServerMessage):
        assert self.websocket is not None
        message_str = to_json(message)
        if self.verbose:
            self.ten_env.log_info(f"-> {smart_str(message_str)}")
        await self.websocket.send_str(message_str)

    async def listen(self) -> AsyncGenerator[ServerToClientMessage, None]:
        assert self.websocket is not None
        if self.verbose:
            self.ten_env.log_info("Listening for realtimeapi messages")
        try:
            async for msg in self.websocket:
                try:
                    if msg.type == aiohttp.WSMsgType.TEXT:
                        if self.verbose:
                            self.ten_env.log_debug(
                                f"[WSMessage] type={msg.type} ({getattr(msg.type, 'name', msg.type)}), "
                                f"data={repr(msg.data)}, extra={repr(msg.extra)}"
                            )
                        yield self.handle_server_message(msg.data)
                    elif msg.type == aiohttp.WSMsgType.ERROR:
                        self.ten_env.log_error(
                            f"[listen] WSMsgType.ERROR: {self.websocket.exception()}"
                        )
                        break
                    elif msg.type == aiohttp.WSMsgType.CLOSE:
                        self.ten_env.log_info(f"[listen] WSMsgType.CLOSE: code={msg.data}, reason={msg.extra}")
                        break
                    elif msg.type == aiohttp.WSMsgType.CLOSED:
                        self.ten_env.log_info("[listen] WSMsgType.CLOSED")
                        break
                    elif msg.type == aiohttp.WSMsgType.CLOSING:
                        self.ten_env.log_info("[listen] WSMsgType.CLOSING")
                        break
                    else:
                        self.ten_env.log_info(f"[listen] Unhandled msg type: {msg.type}")
                except Exception as inner_e:
                    import traceback
                    self.ten_env.log_error(f"[listen] Exception in message loop: {inner_e}\n{traceback.format_exc()}")
        except asyncio.CancelledError:
            self.ten_env.log_info("[listen] Receive messages task cancelled")
        except Exception as e:
            import traceback
            self.ten_env.log_error(f"[listen] Outer exception: {e}\n{traceback.format_exc()}")
        finally:
            closed = getattr(self.websocket, "closed", None)
            close_code = getattr(self.websocket, "close_code", None)
            close_reason = getattr(self.websocket, "close_reason", None)
            ws_exception = self.websocket.exception() if self.websocket else None
            self.ten_env.log_info(
                f"[listen] Exiting listen generator (WebSocket closed or error). "
                f"closed={closed}, close_code={close_code}, close_reason={close_reason}, exception={ws_exception}"
            )

    def handle_server_message(self, message: str) -> ServerToClientMessage:
        try:
            return parse_server_message(message)
        except Exception as e:
            self.ten_env.log_info(f"Error handling message {e}")

    async def close(self):
        # Close the websocket connection if it exists
        if self.websocket:
            await self.websocket.close()
            self.websocket = None
