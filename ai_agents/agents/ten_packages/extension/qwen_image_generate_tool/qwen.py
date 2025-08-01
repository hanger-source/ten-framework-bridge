#
# This file is part of TEN Framework, an open source project.
# Licensed under the Apache License, Version 2.0.
# See the LICENSE file for more information.
#
from dataclasses import dataclass
from http import HTTPStatus
from urllib.parse import urlparse, unquote
from pathlib import PurePosixPath
import requests
import asyncio
import os

# dashscope sdk >= 1.23.3
from dashscope import ImageSynthesis
from ten_runtime.async_ten_env import AsyncTenEnv
from ten_ai_base.config import BaseConfig


@dataclass
class QwenImageGenerateToolConfig(BaseConfig):
    api_key: str = ""
    model: str = "wan2.2-t2i-flash"
    size: str = "1024*1024"
    n: int = 1
    proxy_url: str = ""


class QwenImageGenerateClient:
    def __init__(
        self, ten_env: AsyncTenEnv, config: QwenImageGenerateToolConfig
    ):
        self.config = config
        self.ten_env = ten_env
        ten_env.log_info(
            f"QwenImageGenerateClient initialized with config: {config.api_key}"
        )

        # 设置API密钥
        os.environ["DASHSCOPE_API_KEY"] = config.api_key

    def generate_images(self, prompt: str, n: int = 1, size: str = "1024*1024", callback=None):
        """
        生成图片并返回图片URL
        使用回调方式处理异步任务
        """
        if not prompt:
            error_msg = "Prompt is required"
            self.ten_env.log_error(error_msg)
            if callback:
                callback(None, error_msg)
            return

        self.ten_env.log_info(f"Generating image with prompt: {prompt}, n: {n}, size: {size}")

        try:
            # 创建异步任务
            task_info = self._create_async_task(prompt, n, size)

            # 启动异步任务监控
            self._monitor_async_task(task_info, callback)

        except Exception as e:
            self.ten_env.log_error(f"Error generating image: {e}")
            if callback:
                callback(None, str(e))

    def _create_async_task(self, prompt: str, n: int, size: str):
        """
        创建异步图片生成任务
        """
        try:
            rsp = ImageSynthesis.async_call(
                api_key=self.config.api_key,
                model=self.config.model,
                prompt=prompt,
                n=n,
                size=size
            )

            self.ten_env.log_info(f"Created async task: {rsp}")

            if rsp.status_code == HTTPStatus.OK:
                self.ten_env.log_info(f"Task created successfully: {rsp.output}")
                return rsp
            else:
                error_msg = f'Failed to create task, status_code: {rsp.status_code}, code: {rsp.code}, message: {rsp.message}'
                self.ten_env.log_error(error_msg)
                raise Exception(error_msg)

        except Exception as e:
            self.ten_env.log_error(f"Error creating async task: {e}")
            raise e

    def _monitor_async_task(self, task, callback):
        """
        监控异步任务状态 - 使用协程方式
        """
        # 创建异步监控任务
        asyncio.create_task(self._async_monitor_task(task, callback))

    async def _async_monitor_task(self, task, callback):
        """
        异步监控任务状态
        """
        try:
            while True:
                # 使用线程池执行同步的fetch操作
                loop = asyncio.get_event_loop()
                status = await loop.run_in_executor(None, ImageSynthesis.fetch, task)

                if status.status_code == HTTPStatus.OK:
                    task_status = status.output.task_status
                    self.ten_env.log_info(f"Task status: {task_status}")

                    if task_status == "SUCCEEDED":
                        # 任务成功，获取结果
                        result = await loop.run_in_executor(None, ImageSynthesis.wait, task)

                        if result.status_code == HTTPStatus.OK:
                            if result.output.results and len(result.output.results) > 0:
                                image_url = result.output.results[0].url
                                self.ten_env.log_info(f"Generated image URL: {image_url}")
                                if callback:
                                    callback(image_url, None)
                            else:
                                if callback:
                                    callback(None, "No image generated")
                        else:
                            error_msg = f"Failed to get result, status_code: {result.status_code}, code: {result.code}, message: {result.message}"
                            self.ten_env.log_error(error_msg)
                            if callback:
                                callback(None, error_msg)
                        break

                    elif task_status == "FAILED":
                        error_msg = "Task failed"
                        self.ten_env.log_error(error_msg)
                        if callback:
                            callback(None, error_msg)
                        break

                    elif task_status == "CANCELLED":
                        error_msg = "Task cancelled"
                        self.ten_env.log_error(error_msg)
                        if callback:
                            callback(None, error_msg)
                        break

                    else:
                        # 任务还在进行中，继续等待
                        await asyncio.sleep(2)  # 使用asyncio.sleep替代time.sleep

                else:
                    error_msg = f"Failed to fetch task status, status_code: {status.status_code}, code: {status.code}, message: {status.message}"
                    self.ten_env.log_error(error_msg)
                    if callback:
                        callback(None, error_msg)
                    break

        except Exception as e:
            self.ten_env.log_error(f"Error monitoring task: {e}")
            if callback:
                callback(None, str(e))
