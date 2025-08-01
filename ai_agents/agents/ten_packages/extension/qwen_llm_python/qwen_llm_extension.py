#
#
# Agora Real Time Engagement
# Created by Wei Hu in 2024-05.
# Copyright (c) 2024 Agora IO. All rights reserved.
#
#
from ten_runtime import (
    TenEnv,
    Cmd,
    Data,
    StatusCode,
    CmdResult,
    AsyncTenEnv,
)
from ten_ai_base.const import (
    CMD_PROPERTY_RESULT,
    CMD_TOOL_CALL,
    DATA_OUT_PROPERTY_END_OF_SEGMENT,
    DATA_OUT_PROPERTY_TEXT,
)
from ten_ai_base.types import (
    LLMToolMetadata,
)
from ten_ai_base.llm import AsyncLLMBaseExtension
from ten_ai_base.types import LLMDataCompletionArgs, LLMCallCompletionArgs
from typing import List, Any, AsyncGenerator
import dashscope
import queue
import json
from datetime import datetime
import threading
import re
from http import HTTPStatus
import random

DATA_OUT_TEXT_DATA_PROPERTY_TEXT = "text"
DATA_OUT_TEXT_DATA_PROPERTY_TEXT_END_OF_SEGMENT = "end_of_segment"


class QWenLLMExtension(AsyncLLMBaseExtension):
    def __init__(self, name: str):
        super().__init__(name)
        self.history = []
        self.api_key = ""
        self.model = ""
        self.prompt = ""
        self.max_history = 10
        self.stopped = False
        self.sentence_expr = re.compile(r".+?[,，.。!！?？:：]", re.DOTALL)

        self.outdate_ts = datetime.now()
        self.outdate_ts_lock = threading.Lock()

    def on_msg(self, role: str, content: str) -> None:
        self.history.append({"role": role, "content": content})
        if len(self.history) > self.max_history:
            self.history = self.history[1:]

    def get_messages(self) -> List[Any]:
        messages = []
        if len(self.prompt) > 0:
            messages.append({"role": "system", "content": self.prompt})
        for h in self.history:
            messages.append(h)
        return messages

    def need_interrupt(self, ts: datetime) -> bool:
        with self.outdate_ts_lock:
            return self.outdate_ts > ts

    def get_outdate_ts(self) -> datetime:
        with self.outdate_ts_lock:
            return self.outdate_ts

    async def on_start(self, ten: AsyncTenEnv) -> None:
        await super().on_start(ten)

        ten.log_info("[qwen_llm] Extension starting")
        self.api_key, _ = await ten.get_property_string("api_key")
        self.model, _ = await ten.get_property_string("model")
        self.prompt, _ = await ten.get_property_string("prompt")
        self.max_history, _ = await ten.get_property_int("max_memory_length")

        ten.log_info(f"[qwen_llm] Config: model={self.model}, api_key={'*' * 10 if self.api_key else 'NOT_SET'}, max_history={self.max_history}")

        dashscope.api_key = self.api_key
        ten.log_info("[qwen_llm] DashScope API configured")

    async def on_stop(self, ten: AsyncTenEnv) -> None:
        await super().on_stop(ten)
        ten.log_info("[qwen_llm] Extension stopping")
        self.stopped = True

    def flush(self):
        with self.outdate_ts_lock:
            self.outdate_ts = datetime.now()

    async def on_data(self, ten: AsyncTenEnv, data: Data) -> None:
        ten.log_info("on_data")
        is_final, _ = data.get_property_bool("is_final")
        if not is_final:
            ten.log_info("ignore non final")
            return

        input_text, _ = data.get_property_string("text")
        if len(input_text) == 0:
            ten.log_info("ignore empty text")
            return

        ts = datetime.now()
        ten.log_info(f"on data {input_text}, {ts}")

        # 使用父类的异步队列
        await self.queue_input_item(messages=[{"role": "user", "content": input_text}])

    async def on_data_chat_completion(
        self, ten: AsyncTenEnv, **kargs: LLMDataCompletionArgs
    ) -> None:
        """处理数据聊天完成"""
        messages = kargs.get("messages", [])
        if not messages:
            return

        # 获取用户消息
        user_message = messages[-1]
        input_text = user_message.get("content", "")

        if not input_text:
            return

        ten.log_info(f"[qwen_llm] Starting data chat completion: {input_text}")

        # 添加到历史记录
        self.on_msg("user", input_text)

        # 直接使用 _stream_chat_internal 生成回复，避免双重输出
        messages = self.get_messages()
        messages.append({"role": "user", "content": input_text})
        ten.log_info(f"[qwen_llm] Calling _stream_chat_internal with {len(messages)} messages")
        total = await self._stream_chat_internal(ten, messages)

        if total:
            self.on_msg("assistant", total)
            ten.log_info(f"[qwen_llm] Data chat completion finished: {total[:50]}...")
        else:
            ten.log_info(f"[qwen_llm] Data chat completion finished: no total")

        # 确保发送结束信号，防止文本累积
        ten.log_info(f"[qwen_llm] Sending final end_of_segment signal")
        self.send_text_output(ten, "", True)

    async def on_call_chat_completion(
        self, ten: AsyncTenEnv, **kargs: LLMCallCompletionArgs
    ) -> Any:
        """处理调用聊天完成"""
        messages = kargs.get("messages", [])
        stream = kargs.get("stream", False)

        ten.log_info(f"[qwen_llm] Call chat completion: {len(messages)} messages, stream={stream}")

        if stream:
            # 流式响应
            async for chunk in self._stream_chat(ten, messages):
                yield chunk
        else:
            # 非流式响应
            response = await self._generate_response(ten, messages[-1].get("content", ""))
            yield {"content": response, "end_of_segment": True}

    async def on_tools_update(
        self, ten: AsyncTenEnv, tool: LLMToolMetadata
    ) -> None:
        """处理工具更新"""
        ten.log_info(f"[qwen_llm] Tool updated: {tool.name}")

    async def _generate_response(self, ten: AsyncTenEnv, input_text: str) -> str:
        """生成回复"""
        messages = self.get_messages()
        messages.append({"role": "user", "content": input_text})

        total = await self._stream_chat_internal(ten, messages)

        if total:
            self.on_msg("assistant", total)

        return total

    async def _stream_chat_internal(
        self, ten: AsyncTenEnv, messages: List[Any]
    ) -> str:
        """内部流式聊天实现"""
        ten.log_info(f"[qwen_llm] Available tools count: {len(self.available_tools)}")

        tools = None
        if len(self.available_tools) > 0:
            tools = []
            tool_names = [tool.name for tool in self.available_tools]
            ten.log_info(f"[qwen_llm] Registered tools: {tool_names}")
            for tool in self.available_tools:
                tool_dict = self._convert_tools_to_dict(tool)
                ten.log_info(f"[qwen_llm] Tool format: {json.dumps(tool_dict, ensure_ascii=False)}")
                tools.append(tool_dict)
        else:
            ten.log_warn("[qwen_llm] No tools available for LLM")

        ten.log_info(f"[qwen_llm] Calling LLM: model={self.model}, tools={tools is not None}")
        if tools:
            ten.log_info(f"[qwen_llm] Tools: {json.dumps(tools, ensure_ascii=False)}")
        ten.log_info(f"[qwen_llm] Messages: {json.dumps(messages, ensure_ascii=False)}")

        try:
            responses = dashscope.Generation.call(
                self.model,
                messages=messages,
                tools=tools,
                result_format="message",
                stream=True,
                incremental_output=True,
                enable_search=False,  # 禁用搜索，启用 Function Calling
                seed=random.randint(1, 10000),
            )

            # 重置变量，避免累积之前对话的内容
            total = ""
            partial = ""
            tool_calls = []
            # ten.log_info(f"[qwen_llm] Starting new conversation, reset variables")

            for response in responses:
                if response.status_code == HTTPStatus.OK:
                    message = response.output.choices[0]["message"]

                    # 检查是否有工具调用
                    if "tool_calls" in message and message["tool_calls"]:
                        tool_calls = message["tool_calls"]
                        ten.log_info(f"[qwen_llm] Tool calls detected: {len(tool_calls)}")

                        # 添加助手消息，包含工具调用
                        assistant_message = {
                            "role": "assistant",
                            "content": None,  # Content is None for tool_calls message
                            "tool_calls": tool_calls
                        }
                        messages.append(assistant_message)
                        ten.log_info(f"[qwen_llm] Assistant message with tool_calls added")

                        # 处理每个工具调用
                        for tool_call in tool_calls:
                            ten.log_info(f"[qwen_llm] Tool call: {tool_call['function']['name']} - {tool_call}")

                            # 使用异步方式处理工具调用，通过TEN Framework注册机制
                            tool_result = await self.handle_tool_call(ten, tool_call, messages)

                            # 将工具结果添加到消息历史
                            tool_message = {
                                "role": "tool",
                                "content": tool_result,
                                "tool_call_id": tool_call["id"]
                            }
                            messages.append(tool_message)
                            ten.log_info(f"[qwen_llm] Tool result added: {tool_result[:50]}...")

                        # 如果有工具调用，进行第二轮对话
                        if tool_calls:
                            ten.log_info("[qwen_llm] Making second round call with tool results")
                            return await self._stream_chat_internal(ten, messages)

                    # 处理普通文本内容
                    temp = message.get("content", "")
                    if len(temp) == 0:
                        continue
                    partial += temp
                    total += temp

                    # 发送文本输出
                    if partial:
                        m = self.sentence_expr.match(partial)
                        if m is not None:
                            sentence = m.group(0)
                            partial = partial[m.end(0) :]
                            self.send_text_output(ten, sentence, False)

                else:
                    ten.log_warn(
                        f"request_id: {response.request_id}, status_code: {response.status_code}, error code: {response.code}, error message: {response.message}"
                    )
                    break

            # 发送最后的文本
            if partial:
                self.send_text_output(ten, partial, True)

            ten.log_info(f"[qwen_llm] stream_chat full_answer: {total}")
            return total
        except Exception as e:
            ten.log_error(f"[qwen_llm] Error in _stream_chat_internal: {e}")
            return ""
        finally:
            # 确保发送结束信号，防止文本累积
            ten.log_info(f"[qwen_llm] Sending final end_of_segment signal")
            self.send_text_output(ten, "", True)

    async def _stream_chat(
        self, ten: AsyncTenEnv, messages: List[Any]
    ) -> AsyncGenerator[dict, None]:
        """流式聊天生成器"""
        total = await self._stream_chat_internal(ten, messages)
        # 不在这里发送 end_of_segment，因为 _stream_chat_internal 已经通过 send_text_output 发送了
        yield {"content": total, "end_of_segment": False}

    def _convert_tools_to_dict(self, tool: LLMToolMetadata):
        """将工具元数据转换为字典格式"""
        # 构建 properties 对象
        properties = {}
        required = []

        for param in tool.parameters:
            properties[param.name] = {
                "type": param.type,
                "description": param.description,
            }
            if param.required:
                required.append(param.name)
            if param.type == "array":
                properties[param.name]["items"] = param.items

        json_dict = {
            "type": "function",
            "function": {
                "name": tool.name,
                "description": tool.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                    "additionalProperties": False,  # 添加这个字段
                },
            },
        }

        return json_dict

    def _get_last_user_input(self, messages):
        """从消息历史中获取最后一个用户输入"""
        if not messages:
            return None

        # 从后往前查找最后一个用户消息
        for message in reversed(messages):
            if message.get("role") == "user":
                content = message.get("content", "")
                if content and content.strip():
                    return content.strip()

        return None

    async def handle_tool_call(self, ten: AsyncTenEnv, tool_call, messages=None):
        """处理工具调用"""
        function_name = tool_call["function"]["name"]
        arguments_raw = tool_call["function"]["arguments"]

        ten.log_info(f"[qwen_llm] Starting tool call: {function_name}, args: {arguments_raw}")

        # 解析 arguments JSON 字符串
        try:
            if arguments_raw and arguments_raw.strip():
                arguments = json.loads(arguments_raw)
                ten.log_info(f"[qwen_llm] Parsed arguments: {arguments}")
            else:
                ten.log_warn(f"[qwen_llm] Empty arguments received, using user input as default")
                # TODO: 工具参数问题 - LLM 经常返回空的 arguments，需要进一步调查：
                # 1. 检查工具定义格式是否符合阿里云要求
                # 2. 检查模型版本和参数设置
                # 3. 可能需要调整系统提示词来引导 LLM 正确生成参数
                # 4. 考虑使用不同的模型或参数组合
                # 当前临时解决方案：使用用户输入作为默认参数
                if function_name == "generate_image":
                    # 从消息历史中获取用户的输入作为默认 prompt
                    user_input = self._get_last_user_input(messages) if messages else None
                    arguments = {"prompt": user_input if user_input else "生成一张图片"}
                    ten.log_info(f"[qwen_llm] Using user input as default prompt: {user_input}")
                else:
                    arguments = {}
        except json.JSONDecodeError as e:
            ten.log_error(f"[qwen_llm] Failed to parse arguments JSON: {e}")
            if function_name == "generate_image":
                # 从消息历史中获取用户的输入作为默认 prompt
                user_input = self._get_last_user_input(messages) if messages else None
                arguments = {"prompt": user_input if user_input else "生成一张图片"}
                ten.log_info(f"[qwen_llm] Using user input as default prompt after JSON error: {user_input}")
            else:
                arguments = {}

                # 发送工具调用命令
        cmd: Cmd = Cmd.create(CMD_TOOL_CALL)
        cmd.set_property_string("name", function_name)

        # 发送解析后的参数
        arguments_json = json.dumps(arguments)
        cmd.set_property_from_json("arguments", arguments_json)

        ten.log_info(f"[qwen_llm] Sending CMD_TOOL_CALL for: {function_name}")

        try:
            # 发送命令并等待结果
            [result, _] = await ten.send_cmd(cmd)

            if result and result.get_status_code() == StatusCode.OK:
                ten.log_info(f"[qwen_llm] Command executed successfully")
                r, _ = result.get_property_to_json(CMD_PROPERTY_RESULT)
                tool_result = json.loads(r)
                result_content = tool_result["content"] if isinstance(tool_result, dict) else str(tool_result)
                ten.log_info(f"[qwen_llm] Tool {function_name} result: {result_content[:100]}...")
                return result_content
            else:
                status_code = result.get_status_code() if result else 'No result'
                error_msg = f"[qwen_llm] Tool call failed: {status_code}"
                ten.log_error(error_msg)
                return error_msg
        except Exception as e:
            error_msg = f"[qwen_llm] Error handling tool call: {e}"
            ten.log_error(error_msg)
            return error_msg

    async def on_cmd(self, ten: AsyncTenEnv, cmd: Cmd) -> None:
        ts = datetime.now()
        cmd_name = cmd.get_name()
        ten.log_info(f"on_cmd {cmd_name}, {ts}")

        if cmd_name == "flush":
            self.flush()
            cmd_out = Cmd.create("flush")
            await ten.send_cmd(cmd_out)
            ten.log_info("send_cmd flush done")
        elif cmd_name == 'on_user_joined':
            greeting, _ = await ten.get_property_string("greeting")
            if greeting:
                try:
                    output_data = Data.create("text_data")
                    output_data.set_property_string(
                        DATA_OUT_TEXT_DATA_PROPERTY_TEXT, greeting
                    )
                    output_data.set_property_bool(
                        DATA_OUT_TEXT_DATA_PROPERTY_TEXT_END_OF_SEGMENT, True
                    )
                    await ten.send_data(output_data)
                    ten.log_info(f"greeting [{greeting}] sent")
                except Exception as e:
                    ten.log_error(f"greeting [{greeting}] send failed, err: {e}")
        else:
            await super().on_cmd(ten, cmd)

        cmd_result = CmdResult.create(StatusCode.OK, cmd)
        await ten.return_result(cmd_result)
