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
    CMD_IN_FLUSH,
    CMD_OUT_FLUSH,
)
from ten_ai_base.types import (
    LLMToolMetadata,
    LLMDataCompletionArgs,
    LLMCallCompletionArgs,
    LLMChatCompletionUserMessageParam,
)
from ten_ai_base.llm import AsyncLLMBaseExtension
from typing import List, Any, AsyncGenerator
import dashscope
import queue
import json
from datetime import datetime
import re
from http import HTTPStatus
import random
import asyncio

DATA_OUT_TEXT_DATA_PROPERTY_TEXT = "text"
DATA_OUT_TEXT_DATA_PROPERTY_TEXT_END_OF_SEGMENT = "end_of_segment"

# 命令常量
CMD_IN_ON_USER_JOINED = "on_user_joined"
CMD_IN_ON_USER_LEFT = "on_user_left"


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

    async def on_data(self, ten: AsyncTenEnv, data: Data) -> None:
        # 移除频繁的调试日志
        is_final, _ = data.get_property_bool("is_final")
        if not is_final:
            return

        input_text, _ = data.get_property_string("text")
        if len(input_text) == 0:
            return

        # 移除时间戳日志，减少字符串格式化开销
        # ts = datetime.now()
        # ten.log_info(f"on data {input_text}, {ts}")

        # 使用父类的异步队列
        message = LLMChatCompletionUserMessageParam(
            role="user", content=input_text
        )
        await self.queue_input_item(False, messages=[message])

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

        # 移除详细的输入文本日志，只保留简单计数
        # ten.log_info(f"[qwen_llm] Starting data chat completion: {input_text}")

        # 添加到历史记录
        self.on_msg("user", input_text)

        # 直接使用 _stream_chat_internal 生成回复，避免双重输出
        messages = self.get_messages()
        messages.append({"role": "user", "content": input_text})
        # ten.log_info(f"[qwen_llm] Calling _stream_chat_internal with {len(messages)} messages")

        # 等待流式处理完成
        await self._stream_chat_internal(ten, messages)

    async def on_call_chat_completion(
        self, ten: AsyncTenEnv, **kargs: LLMCallCompletionArgs
    ) -> Any:
        """处理调用聊天完成"""
        messages = kargs.get("messages", [])
        stream = kargs.get("stream", False)

        # ten.log_info(f"[qwen_llm] Call chat completion: {len(messages)} messages, stream={stream}")

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
        # ten.log_info(f"[qwen_llm] Tool updated: {tool.name}")
        pass

    async def _generate_response(self, ten: AsyncTenEnv, input_text: str) -> str:
        """生成回复"""
        messages = self.get_messages()
        messages.append({"role": "user", "content": input_text})

        # 等待流式处理完成
        total = await self._stream_chat_internal(ten, messages)
        return total

    def _accumulate_tool_calls(self, message, accumulated_tool_calls):
        """累积流式工具调用的参数"""
        for tool_call in message["tool_calls"]:
            index = tool_call.get("index", 0)

            # 初始化累积的 tool_call（只在第一次）
            if index not in accumulated_tool_calls:
                accumulated_tool_calls[index] = {
                    "id": tool_call.get("id", ""),
                    "type": tool_call.get("type", "function"),
                    "function": {
                        "name": tool_call["function"].get("name", ""),
                        "arguments": ""
                    }
                }

            # 累积 arguments
            if "function" in tool_call and "arguments" in tool_call["function"]:
                current_args = tool_call["function"]["arguments"]
                if current_args:
                    accumulated_tool_calls[index]["function"]["arguments"] += current_args

    async def _process_completed_tool_calls(self, ten: AsyncTenEnv, accumulated_tool_calls, messages):
        """处理完整的工具调用"""
        tool_calls = list(accumulated_tool_calls.values())
        ten.log_info(f"[qwen_llm] Tool calls completed: {len(tool_calls)}")

        # 添加助手消息，包含工具调用
        messages.append({
            "role": "assistant",
            "content": None,
            "tool_calls": tool_calls
        })

        # 处理每个工具调用
        for tool_call in tool_calls:
            # 移除详细的工具调用日志，只保留函数名
            # ten.log_info(f"[qwen_llm] Tool call: {tool_call['function']['name']} - {tool_call}")
            tool_result = await self.handle_tool_call(ten, tool_call, messages)

            # 将工具结果添加到消息历史
            messages.append({
                "role": "tool",
                "content": tool_result,
                "tool_call_id": tool_call["id"]
            })
            # 移除详细的结果日志
            # ten.log_info(f"[qwen_llm] Tool result added: {tool_result[:50]}...")

        return tool_calls

    async def _stream_chat_internal(
        self, ten: AsyncTenEnv, messages: List[Any]
    ) -> str:
        """内部流式聊天实现"""
        # ten.log_info(f"[qwen_llm] Available tools count: {len(self.available_tools)}")

        tools = None
        if len(self.available_tools) > 0:
            tools = []
            tool_names = [tool.name for tool in self.available_tools]
            # ten.log_info(f"[qwen_llm] Registered tools: {tool_names}")
            for tool in self.available_tools:
                tool_dict = self._convert_tools_to_dict(tool)
                # ten.log_info(f"[qwen_llm] Tool format: {json.dumps(tool_dict, ensure_ascii=False)}")
                tools.append(tool_dict)
        else:
            ten.log_warn("[qwen_llm] No tools available for LLM")

        # ten.log_info(f"[qwen_llm] Calling LLM: model={self.model}, tools={tools is not None}")
        # ten.log_info(f"[qwen_llm] Tools: {json.dumps(tools, ensure_ascii=False)}")
        # ten.log_info(f"[qwen_llm] Messages: {json.dumps(messages, ensure_ascii=False)}")

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
            # 添加流式工具调用累积变量
            accumulated_tool_calls = {}
            # ten.log_info(f"[qwen_llm] Starting new conversation, reset variables")

            for response in responses:
                # 检查中断
                if response.status_code == HTTPStatus.OK:
                    message = response.output.choices[0]["message"]

                    # 检查是否有工具调用
                    if "tool_calls" in message and message["tool_calls"]:
                        # 处理流式工具调用，累积参数
                        self._accumulate_tool_calls(message, accumulated_tool_calls)

                        # 检查是否完成工具调用
                        if response.output.choices[0].get("finish_reason") == "tool_calls":
                            tool_calls = await self._process_completed_tool_calls(ten, accumulated_tool_calls, messages)

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
                            # 让出控制权给事件循环
                            await asyncio.sleep(0.1)

                else:
                    ten.log_warn(
                        f"request_id: {response.request_id}, status_code: {response.status_code}, error code: {response.code}, error message: {response.message}"
                    )
                    break

            # 发送最后的文本
            if partial:
                self.send_text_output(ten, partial, True)

            # ten.log_info(f"[qwen_llm] stream_chat full_answer: {total}")
            return total
        except Exception as e:
            ten.log_error(f"[qwen_llm] Error in _stream_chat_internal: {e}")
            return ""
        finally:
            # 确保发送结束信号，防止文本累积
            # ten.log_info(f"[qwen_llm] Sending final end_of_segment signal")
            self.send_text_output(ten, "", True)

    async def _stream_chat(
        self, ten: AsyncTenEnv, messages: List[Any]
    ) -> AsyncGenerator[dict, None]:
        """流式聊天生成器"""
        # 等待流式处理完成
        total = await self._stream_chat_internal(ten, messages)
        # 返回完整内容
        yield {"content": total, "end_of_segment": True}

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
                },
            },
        }

        return json_dict

    async def handle_tool_call(self, ten: AsyncTenEnv, tool_call, messages=None):
        """处理工具调用"""
        function_name = tool_call["function"]["name"]
        arguments_raw = tool_call["function"]["arguments"]

        # ten.log_info(f"[qwen_llm] Starting tool call: {function_name}, args: {arguments_raw}")

        # 解析 arguments JSON 字符串
        try:
            if arguments_raw and arguments_raw.strip():
                arguments = json.loads(arguments_raw)
                # ten.log_info(f"[qwen_llm] Parsed arguments: {arguments}")
            else:
                ten.log_error(f"[qwen_llm] Empty arguments received for function: {function_name}")
                return f"Error: No arguments provided for {function_name}"
        except json.JSONDecodeError as e:
            ten.log_error(f"[qwen_llm] Failed to parse arguments JSON: {e}")
            ten.log_error(f"[qwen_llm] Raw arguments: '{arguments_raw}'")
            return f"Error: Invalid JSON arguments for {function_name}: {e}"

        # 发送工具调用命令
        cmd: Cmd = Cmd.create(CMD_TOOL_CALL)
        cmd.set_property_string("name", function_name)

        # 发送解析后的参数
        arguments_json = json.dumps(arguments)
        cmd.set_property_from_json("arguments", arguments_json)

        # ten.log_info(f"[qwen_llm] Sending CMD_TOOL_CALL for: {function_name}")

        try:
            # 发送命令并等待结果
            [result, _] = await ten.send_cmd(cmd)

            if result and result.get_status_code() == StatusCode.OK:
                # ten.log_info(f"[qwen_llm] Command executed successfully")
                r, _ = result.get_property_to_json(CMD_PROPERTY_RESULT)
                tool_result = json.loads(r)
                result_content = tool_result["content"] if isinstance(tool_result, dict) else str(tool_result)
                # ten.log_info(f"[qwen_llm] Tool {function_name} result: {result_content[:100]}...")
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
        cmd_name = cmd.get_name()
        # ten.log_info(f"on_cmd name: {cmd_name}")

        if cmd_name == CMD_IN_FLUSH:
            await self.flush_input_items(ten)
            # ten.log_info("on_cmd flush input items")
            await ten.send_cmd(Cmd.create(CMD_OUT_FLUSH))
            # ten.log_info("on_cmd sent flush")
            status_code, detail = StatusCode.OK, "success"
            cmd_result = CmdResult.create(status_code, cmd)
            cmd_result.set_property_string("detail", detail)
            await ten.return_result(cmd_result)
        elif cmd_name == CMD_IN_ON_USER_JOINED:
            greeting, _ = await ten.get_property_string("greeting")
            if greeting:
                try:
                    self.send_text_output(ten, greeting, True)
                    # ten.log_info(f"greeting [{greeting}] sent")
                except Exception as e:
                    ten.log_error(f"greeting [{greeting}] send failed, err: {e}")

            status_code, detail = StatusCode.OK, "success"
            cmd_result = CmdResult.create(status_code, cmd)
            cmd_result.set_property_string("detail", detail)
            await ten.return_result(cmd_result)
        elif cmd_name == CMD_IN_ON_USER_LEFT:
            status_code, detail = StatusCode.OK, "success"
            cmd_result = CmdResult.create(status_code, cmd)
            cmd_result.set_property_string("detail", detail)
            await ten.return_result(cmd_result)
        else:
            await super().on_cmd(ten, cmd)
