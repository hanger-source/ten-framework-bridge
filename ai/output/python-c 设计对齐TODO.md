# `ten-framework` Python/C 设计到 Java 的对齐 TODO 列表

本 TODO 列表旨在指导 `ten-framework` 从 Python/C 版本到 Java 版本的完整设计对齐工作。鉴于任务的复杂性，本列表将采用分阶段、可嵌套的结构，以确保每一步都清晰明确，并最终实现一个在开发环境中可运行的、与原始设计完全相同的 Java 版本。

这是一项极其复杂的任务 它不是一蹴而就的

因此需要你构架一套独立的todo 来解决 用java 保证相同的设计

TODO:
给你的建议：建议你做一个庞大的繁杂的todo 而不是简单的线性的todo
你的todo 缺少了对于实际可运行的前瞻性 短期不需要考虑监控部署，开发环境有一个可运行的机制
没有所谓短中长期方案，只是繁杂todo分多个阶段 每个阶段又要拆分多个子阶段 子阶段有需要嵌套

每一项完成后在标题前 ✅，每一项完成必须保证 mvn clean compile -Dmaven.test.skip=true 通过

## 阶段 1: 基础架构与核心模型 (Phase 1: Foundation & Core Models)

### 1.1 项目结构与依赖管理 (Project Structure & Dependency Management)

✅ 1.1.1 定义Maven/Gradle多模块项目结构
✅ 1.1.1.1 创建父级Maven/Gradle项目
✅ 创建 `pom.xml` 或 `build.gradle` 文件。
✅ 定义项目基本信息、Java版本 (例如 JDK 17+)。
✅ 1.1.1.2 定义核心模块
✅ `ten-core-api` (存放核心消息、Location、Error等POJO和公共接口)
✅ 创建 `ten-core-api/pom.xml` 或 `ten-core-api/build.gradle`。
✅ 添加 Jackson Databind, Lombok 等依赖。
✅ 1.1.1.3 定义服务器模块
✅ `ten-server` (存放Netty相关、Connection、Remote、App等实现)
✅ 创建 `ten-server/pom.xml` 或 `ten-server/build.gradle`。
✅ 添加 Netty Core, Netty Codec HTTP, Netty Codec WebSocket, Netty Transport NIO, MsgPack Jackson Databind, Jackson Annotations, Lombok, SLF4J, Logback 等依赖。

### 1.2 核心消息模型对齐 (Core Message Model Alignment)

✅ 1.2.1 对齐 `Location` 类
✅ 1.2.1.1 确认 `Location.java` 与 `ten_loc_t` 完全对齐 - 字段名、类型 (`appUri`, `graphId`, `extensionName`) - Jackson 注解 (`@JsonProperty`) - 提供 `toDebugString()` 方法。

- **1.2.2 对齐 `MessageType` 枚举**
  - **1.2.2.1 确认 `MessageType.java` 与 `TEN_MSG_TYPE` 完全对齐**
    - 枚举值 (`INVALID`, `CMD`, `CMD_RESULT`, `CMD_CLOSE_APP`, `CMD_START_GRAPH`, `CMD_STOP_GRAPH`, `CMD_TIMER`, `CMD_TIMEOUT`, `DATA`, `VIDEO_FRAME`, `AUDIO_FRAME`)
    - 字节值映射 (`@JsonValue`, `@JsonCreator`)
- **1.2.3 对齐 `Message` 抽象基类**
  - **1.2.3.1 确认 `Message.java` 与 `ten_msg_t` 核心字段对齐**
    - `type`, `id`, `srcLoc`, `destLocs`, `properties`
    - Jackson 多态配置 (`@JsonTypeInfo`, `@JsonSubTypes`)
    - 定义 `toPayload()` 抽象方法。
- **1.2.4 对齐具体消息类型**
  - **1.2.4.1 创建/更新 `CommandMessage.java`**
    - 对齐 `TEN_MSG_TYPE_CMD` 及其相关属性 (`command_name`, `args`)
  - **1.2.4.2 创建/更新 `CommandResultMessage.java`**
    - 对齐 `TEN_MSG_TYPE_CMD_RESULT` 及其相关属性 (`command_id`, `result_code`, `result_message`, `payload`)
  - **1.2.4.3 创建/更新 `DataMessage.java`**
    - 对齐 `TEN_MSG_TYPE_DATA` 及其相关属性 (`data`)
  - **1.2.4.4 创建/更新 `VideoFrameMessage.java`**
    - 对齐 `TEN_MSG_TYPE_VIDEO_FRAME` 及其相关属性 (`frame_data`, `width`, `height`, `timestamp`)
  - **1.2.4.5 创建/更新 `AudioFrameMessage.java`**
    - 对齐 `TEN_MSG_TYPE_AUDIO_FRAME` 及其相关属性 (`frame_data`, `sample_rate`, `channels`, `timestamp`)
  - **1.2.4.6 处理其他特定命令消息 (如 `CMD_START_GRAPH`, `CMD_TIMER` 等)**
    - 分析其在 C/Python 中的 `Msg` 结构，决定是否需要独立的 Java 类或作为 `CommandMessage` 的子类型。
- **1.2.5 对齐 `Error` 类**
  - **1.2.5.1 创建/更新 `TenError.java`**
    - 对齐 `ten_error_t` (包含 `code`, `message`)
    - 提供 `isSuccess()`, `success()`, `failure()` 等辅助方法。

### 1.3 实用工具类对齐 (Utility Class Alignment)

- **1.3.1 创建/更新 `MessageUtils.java`**
  - **1.3.1.1 定义 `TEN_MSGPACK_EXT_TYPE_MSG` 常量**
  - **1.3.1.2 引入 MsgPack 配置辅助类**
    - 创建 `TenMessagePackMapperProvider.java`，统一 `ObjectMapper` 的创建和配置，确保 MsgPack 扩展类型注册正确。

## 阶段 2: 网络通信层 (Phase 2: Network Communication Layer)

### 2.1 `Protocol` 层对齐 (Protocol Layer Alignment)

- **2.1.1 对齐 `MessageDecoder`**
  - **2.1.1.1 更新 `WebSocketMessageDecoder.java`**
    - 确保正确处理 `TEN_MSGPACK_EXT_TYPE_MSG`。
    - 实现基于 `Message.type` 字段的多态反序列化，映射到所有具体消息类型。
    - 处理Netty `ByteBuf` 到 `ByteBuffer` 的转换，确保资源管理正确。
    - 完善错误处理和日志。
- **2.1.2 对齐 `MessageEncoder`**
  - \*\*2.1.2.1 更新 `MessageEncoder.java`
