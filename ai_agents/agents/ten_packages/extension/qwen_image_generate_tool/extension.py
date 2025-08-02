#
# This file is part of TEN Framework, an open source project.
# Licensed under the Apache License, Version 2.0.
# See the LICENSE file for more information.
#
import asyncio
import json
import os
from ten_runtime import (
    Data,
    TenEnv,
    AsyncTenEnv,
)
from ten_ai_base.const import (
    DATA_OUT_PROPERTY_END_OF_SEGMENT,
    DATA_OUT_PROPERTY_TEXT,
    CONTENT_DATA_OUT_NAME,
)
from ten_ai_base.types import LLMToolMetadataParameter, LLMToolResultLLMResult
from ten_ai_base.llm_tool import (
    AsyncLLMToolBaseExtension,
    LLMToolMetadata,
    LLMToolResult,
)
from .qwen import QwenImageGenerateClient, QwenImageGenerateToolConfig


class QwenImageGenerateToolExtension(AsyncLLMToolBaseExtension):
    def __init__(self, name: str):
        super().__init__(name)
        self.config = None
        self.client = None

    async def on_start(self, ten_env: AsyncTenEnv) -> None:
        await super().on_start(ten_env)

        # initialize configuration
        self.config = await QwenImageGenerateToolConfig.create_async(
            ten_env=ten_env
        )
        ten_env.log_info(f"config: {self.config}")

        if not self.config.api_key:
            ten_env.log_error("API key is not set")
            return

        # initialize QwenImageGenerateClient
        self.client = QwenImageGenerateClient(ten_env, self.config)

    async def on_stop(self, ten_env: AsyncTenEnv) -> None:
        await super().on_stop(ten_env)

    def get_tool_metadata(self, ten_env: TenEnv) -> list[LLMToolMetadata]:
        return [
            LLMToolMetadata(
                name="generate_image",
                description="当用户想要生成图片时非常有用。可以根据用户需求生成图片。",
                parameters=[
                    LLMToolMetadataParameter(
                        name="prompt",
                        type="string",
                        description="正向提示词，用来描述生成图像中期望包含的元素和视觉特点。支持中英文，长度不超过800个字符。示例：一只坐着的橘黄色的猫，表情愉悦，活泼可爱，逼真准确。",
                        required=True,
                    ),
                    LLMToolMetadataParameter(
                        name="n",
                        type="integer",
                        description="生成图片的数量。取值范围为1~4张，默认为4；请甄别用户需求，注意该参数为图片张数，不是图片中的元素数量",
                        required=False,
                    ),
                    LLMToolMetadataParameter(
                        name="size",
                        type="string",
                        description="输出图像的分辨率。默认值是1024*1024。图像宽高边长的像素范围为：[512, 1440]，单位像素。",
                        required=False,
                    )
                ],
            )
        ]

    async def send_image(
        self, async_ten_env: AsyncTenEnv, image_url: str
    ) -> None:
        """发送图片到聊天界面"""
        async_ten_env.log_info(f"Sending image: {image_url}")
        try:
            sentence = json.dumps(
                {"data": {"image_url": image_url}, "type": "image_url"}
            )
            output_data = Data.create(CONTENT_DATA_OUT_NAME)
            output_data.set_property_string(DATA_OUT_PROPERTY_TEXT, sentence)
            output_data.set_property_bool(
                DATA_OUT_PROPERTY_END_OF_SEGMENT, True
            )
            asyncio.create_task(async_ten_env.send_data(output_data))
            async_ten_env.log_info(f"sent sentence [{sentence}]")
        except Exception as err:
            async_ten_env.log_warn(
                f"send sentence [{sentence}] failed, err: {err}"
            )

    async def run_tool(
        self, ten_env: AsyncTenEnv, name: str, args: dict
    ) -> LLMToolResult | None:
        ten_env.log_info(f"run_tool {name} {args}")
        ten_env.log_info(f"run_tool args type: {type(args)}")
        ten_env.log_info(f"run_tool args keys: {list(args.keys()) if isinstance(args, dict) else 'Not a dict'}")
        if name == "generate_image":
            prompt = args.get("prompt")
            n = args.get("n", 1)  # 默认生成1张图片
            size = args.get("size", "1024*1024")  # 默认尺寸

            if prompt:
                ten_env.log_info(f"Generating image with prompt: {prompt}")

                # 检查客户端是否已初始化
                if self.client is None:
                    ten_env.log_error("Client not initialized")
                    result = LLMToolResultLLMResult(
                        type="llmresult",
                        content=json.dumps({"success": False, "error": "Client not initialized"}),
                    )
                    return result

                # 使用回调方式处理异步图片生成
                def image_generation_callback(image_url, error):
                    if image_url:
                        ten_env.log_info(f"Generated image: {image_url}")
                        # 发送图片到聊天界面
                        asyncio.create_task(self.send_image(ten_env, image_url))
                    else:
                        ten_env.log_error(f"Failed to generate image: {error}")

                # 启动异步图片生成（使用协程方式）
                self.client.generate_images(prompt, n, size, image_generation_callback)

                # 立即返回成功结果，图片会在回调中发送
                result = LLMToolResultLLMResult(
                    type="llmresult",
                    content=json.dumps({"success": True, "message": "Image generation started"}),
                )
                return result
            else:
                ten_env.log_error("No prompt provided for image generation")
                result = LLMToolResultLLMResult(
                    type="llmresult",
                    content=json.dumps({"success": False, "error": "No prompt provided"}),
                )
                return result