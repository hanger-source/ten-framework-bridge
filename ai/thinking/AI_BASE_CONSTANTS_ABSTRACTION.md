# AI Base 常量抽象 (`ai_agents/agents/ten_packages/system/ten_ai_base/interface/ten_ai_base/const.py`)

`const.py` 文件定义了 `ten-framework` 中 `ten_ai_base` 接口所使用的核心常量。这些常量揭示了 AI 扩展内部命令和数据流的命名约定和语义，是理解其“命令和数据驱动”机制的关键。

## 命令常量 (`CMD_...`)

这些常量定义了 AI 扩展之间或 AI 扩展与 `ten_runtime` 之间通信时使用的命令名称。

*   **`CMD_TOOL_REGISTER = "tool_register"`**:
    *   **用途**: 用于工具扩展向 LLM 服务扩展（或其他需要工具发现的组件）注册其可用工具。LLM 服务可以利用此信息进行函数调用。
    *   **语义**: 表示一个工具的注册动作，通常伴随着工具的元数据（名称、描述、参数 schema 等）。

*   **`CMD_TOOL_CALL = "tool_call"`**:
    *   **用途**: LLM 服务扩展在决定调用某个工具时，向相应的工具扩展发送此命令。
    *   **语义**: 表示一个对特定工具的调用请求，通常包含工具名称和调用参数。

*   **`CMD_PROPERTY_TOOL = "tool"`**:
    *   **用途**: 可能是 `CmdResult` 或 `Cmd` 消息中的一个属性键，用于指示其关联的是工具相关信息。

*   **`CMD_PROPERTY_RESULT = "tool_result"`**:
    *   **用途**: 可能是 `CmdResult` 中的一个属性键，用于指示其携带的是工具调用的结果。

*   **`CMD_CHAT_COMPLETION_CALL = "chat_completion_call"`**:
    *   **用途**: LLM 服务扩展接收到文本输入或需要生成对话响应时，可能会使用此命令触发聊天补全逻辑。
    *   **语义**: 通常用于启动一个对话补全请求，可能包含对话历史、用户输入等。

*   **`CMD_GENERATE_IMAGE_CALL = "generate_image_call"`**:
    *   **用途**: 可能用于图像生成服务扩展，当需要根据文本描述生成图像时，会收到或发送此命令。
    *   **语义**: 触发一个图像生成请求。

*   **`CMD_IN_FLUSH = "flush"`**:
    *   **用途**: 作为**上游组件（例如，用户输入、LLM）向当前扩展发送的中断/清空指令**。它通知接收扩展立即清除其内部队列并取消当前正在进行的任务。在 `AsyncLLMBaseExtension` 和 `AsyncTTSBaseExtension` 中都有体现。
    *   **语义**: 表示一个强制性的数据流或处理流的清空操作。

*   **`CMD_OUT_FLUSH = "flush"`**:
    *   **用途**: 作为**当前扩展向下游组件发送的中断/清空指令**。它通知下游组件也执行清理和任务取消。与 `CMD_IN_FLUSH` 配合，形成完整的**中断传播链**。
    *   **语义**: 表示一个向下游传播的清空操作。

## 数据常量 (`DATA_...`)

这些常量定义了 AI 扩展之间或 AI 扩展与 `ten_runtime` 之间通信时使用的数据消息的名称和属性键。

*   **`DATA_OUT_NAME = "text_data"`**:
    *   **用途**: 定义一个通用的输出数据消息名称，通常用于文本输出。

*   **`CONTENT_DATA_OUT_NAME = "content_data"`**:
    *   **用途**: 可能用于更丰富的内容输出数据消息名称，除了纯文本还包含其他元数据或结构化内容。

*   **`DATA_OUT_PROPERTY_TEXT = "text"`**:
    *   **用途**: `DATA_OUT_NAME` 或 `CONTENT_DATA_OUT_NAME` 消息中的属性键，用于存储实际的文本内容。

*   **`DATA_OUT_PROPERTY_END_OF_SEGMENT = "end_of_segment"`**:
    *   **用途**: `DATA_OUT_NAME` 或 `CONTENT_DATA_OUT_NAME` 消息中的一个关键布尔属性。当此属性为 `true` 时，表示当前文本段（或流的某个部分）的**结束**。这对于实现**实时流式输出**（例如，LLM 文本生成）至关重要，允许消费者知道何时一个有意义的文本块已经完成，可以进行进一步处理（例如，语音合成）。这与 `is_final` 的概念高度相关。

*   **`DATA_OUT_PROPERTY_QUIET = "quiet"`**:
    *   **用途**: `DATA_OUT_NAME` 或 `CONTENT_DATA_OUT_NAME` 消息中的一个布尔属性。当此属性为 `true` 时，表示这段文本不应被朗读或用于语音合成。在 `AsyncTTSBaseExtension` 中有所体现。
    *   **语义**: 允许系统在文本流中插入非语音内容或提示。

*   **`DATA_OUT_PROPERTY_TURN_ID = "turn_id"`**:
    *   **用途**: 用于在多轮对话中标识一个特定的对话回合（turn）。这对于上下文管理、错误追踪和日志记录非常有用。

*   **`DATA_IN_PROPERTY_TEXT = "text"`**:
    *   **用途**: 与 `DATA_OUT_PROPERTY_TEXT` 对应，用于输入数据消息中的文本属性。

*   **`DATA_IN_PROPERTY_END_OF_SEGMENT = "end_of_segment"`**:
    *   **用途**: 与 `DATA_OUT_PROPERTY_END_OF_SEGMENT` 对应，用于输入数据消息中的结束分段属性。

*   **`DATA_IN_PROPERTY_QUIET = "quiet"`**:
    *   **用途**: 与 `DATA_OUT_PROPERTY_QUIET` 对应，用于输入数据消息中的安静属性。

*   **`DATA_INPUT_NAME = "text_data"`**:
    *   **用途**: 通用的输入数据消息名称，通常用于文本输入。与 `DATA_OUT_NAME` 相同，表明文本数据的输入和输出使用相同的名称。

*   **`CONTENT_DATA_INPUT_NAME = "content_data"`**:
    *   **用途**: 用于更丰富的内容输入数据消息名称。

## 音频帧常量

*   **`AUDIO_FRAME_OUTPUT_NAME = "pcm_frame"`**:
    *   **用途**: 定义音频帧输出消息的名称，明确指出是 PCM 格式的音频帧。

## 对 Java 迁移的启示

`const.py` 文件是理解 `ten-framework` 中 AI 扩展命令和数据语义的**核心字典**。它揭示了通信中使用的“协议”层。

1.  **Java 枚举/常量类**:
    *   所有这些字符串常量都应该在 Java 中被映射为 `public static final String` 常量，或者更优地，使用 Java 枚举 (`enum`) 来表示命令类型和数据属性类型，以提高类型安全性和可读性。例如：
        ```java
        public final class CommandNames {
            public static final String TOOL_REGISTER = "tool_register";
            public static final String FLUSH = "flush";
            // ...
        }

        public final class DataPropertyKeys {
            public static final String TEXT = "text";
            public static final String END_OF_SEGMENT = "end_of_segment";
            // ...
        }
        ```

2.  **命令和数据模型的完善**:
    *   在 Java 的 `Command` 和 `Data` 类中，将需要包含设置和获取这些特定属性的方法（例如，`cmd.setProperty(CommandProperty.TOOL, ...)` 或 `data.setEndOfSegment(true)`），确保类型安全和正确的 JSON 序列化/反序列化。
    *   `DATA_OUT_PROPERTY_END_OF_SEGMENT` 的重要性再次被强调，它在 Java 中的实现必须能够被 `Data` 对象携带，并被 LLM 和 TTS 扩展正确地发送和解释，以支持实时流式处理。

3.  **中断机制的统一**:
    *   `CMD_IN_FLUSH` 和 `CMD_OUT_FLUSH` 明确了中断信号的统一名称。在 Java 实现中，这两个常量将用于跨 `Extension` 的中断传播逻辑。

4.  **Java 工具集成**:
    *   `CMD_TOOL_REGISTER` 和 `CMD_TOOL_CALL` 再次强调了 `ten-framework` 对 LLM 工具集成的原生支持。在 Java 中，我们将需要设计一套类似的机制来发现、注册和调用工具，这可能涉及 Java 的 `ServiceLoader` 或自定义的工具注册表。

这个文件虽然简单，但对于理解 `ten-framework` 中 AI 扩展的**命令和数据驱动的本质**至关重要。它提供了通信的“词汇表”，确保了消息的语义化和互操作性。