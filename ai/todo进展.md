# 前情提要

最终目的：拆解出 ten-framework中 命令、数据驱动的部分，得到一个引擎能够支持实时对话的核心运行时，将一个非 Java 项目的核心功能迁移为一个结构清晰、语义对等、符合 Java 编程范式的 Java 项目，具备可读性、可维护性、开源质量。

关于最终目的：这是一项极其复杂的任务，你只需要记住它不是一蹴而就的

阅读目录：core
输出目录（最终目的的结果输出）: ai/output
思考目录: ai/thinking
关键方案目录: ai/thinking/output

实施角色：you（ai）

建议：

反复提醒你：每次新阶段的代码实施前和实施后 必须重新阅读温习思考输出目录中的文档，避免思路过于缥
你需要 阅读 ai/thinking中的 不只是output 检查上一阶段是否符合预期
反复提醒你：谨记禁止用删除来掩盖问题
反复提醒你：子阶段完成 需要回顾代码是否统一水准、避免荒腔走板，避免脱离设计，避免敷衍的代码实现

> todo可以随着进程的推荐进行 更新调整删减补充，因此如果发现阶段执行不合预期，及时补充修复todo
> 审查时 如果没有问题，可以快速跳过，不需要解释 提高效率

代码编写：
使用lombok减少公式代码，以及减少一些样板代码，使用中文注释，使用stream 等特性减少代码复杂性
写代码优先写接口、类型、方法签名、类型、枚举等 实现应该放在最终所有的组件结构成型后再决定

TODO:
给你的建议：建议你做一个庞大的繁杂的todo 而不是简单的线性的todo
你的todo 缺少了对于实际可运行的前瞻性 短期不需要考虑监控部署，开发环境有一个可运行的机制
没有所谓短中长期方案，只是繁杂todo分多个阶段 每个阶段又要拆分多个子阶段 子阶段有需要嵌套

阶段执行:
每个阶段执行前必须重新阅读并检索 思考目录、关键方案目录
每个阶段执行完之前让我纠错 如果我不满意阶段无法通过 例如可能遗漏核心依赖、核心接口、核心类导致后续阶段整体出现偏差

# TODO 进展

✅ 1.1.1 确认 `Message` (sealed interface) 及其子类型 (`Command`, `CommandResult`, `Data`, `AudioFrame`, `VideoFrame`)、`AbstractMessage`、`MessageType`、`Location`、`MessageUtils` 等核心消息类已完整定义。
✅ 1.1.2 验证所有消息类支持正确的深拷贝机制，确保消息多路分发时的独立性。
✅ 1.1.3 确保 `Location` 类能够准确表示 `(app_uri, graph_id, extension_name)` 的 Java 等价物，并支持其**正确的 Jackson 序列化和反序列化**。
✅ 1.1.4 为所有消息类实现 Jackson JSON 序列化/反序列化，确保它们能被 `ObjectMapper` 正确处理（特别是多态序列化）。
✅ 1.2.1 验证 `Engine` 核心类 (`Engine.java`) 的单线程事件循环、Agrona 队列及消息入队 (`submitMessage`) 机制的健壮性。
✅ 1.2.2 验证 `Engine` 生命周期方法 (`start()`, `stop()`) 的正确性，并确保其在启动/停止时能正确管理内部资源（如关闭 `ExecutorService`）。
✅ 1.2.3 验证 `Engine` 的 `processMessage()` 方法能够正确根据消息类型分发，并调用相应的处理逻辑 (`processCommand()`, `processData()`, `processCommandResult()`)。
✅ 1.2.4 确认 `handleQueueFullback()` 策略（丢弃数据消息，拒绝命令消息）已按设计实现。
✅ 1.2.4.1 `handleQueueFullback()` 改进：在命令消息被拒绝时，生成 `CommandResult` 错误并回溯给上游。
✅ 1.3.1 验证 `PathIn` 和 `PathOut` 数据结构能够有效跟踪消息路径，包括 `commandId`、`parentCommandId`、`sourceLocation`、`destinationLocation` 等。
✅ 1.3.2 验证 `PathTable` (`Long2ObjectHashMap`) 对 `PathIn` 和 `PathOut` 的管理（增、删、查）是高效且线程安全的。
✅ 1.3.3 验证命令结果回溯机制，确保 `CommandResult` 能够正确地被关联到 `PathOut` 并触发 `CompletableFuture` 完成。
✅ 1.3.4 验证 `TEN_RESULT_RETURN_POLICY` 枚举（`FIRST_ERROR_OR_LAST_OK`, `EACH_OK_AND_ERROR`）已正确定义和使用。
✅ 1.3.5 验证 `handleResultReturnPolicy()` 能够根据不同的策略处理命令结果（即时返回错误，或等待最终成功/流式返回）。
✅ 1.3.6 验证 `completeCommandResult()` 方法在完成命令结果后能够正确清理 `PathOut` 资源，并进行回溯。
✅ 1.4.1 验证 `Extension` 接口 (`onCommand`, `onData`, `onAudioFrame`, `onVideoFrame`, 生命周期方法) 的完整性和语义对等性。
✅ 1.4.2 验证 `ExtensionContext` 接口 (`sendMessage`, `sendResult`, `getProperty`, `getVirtualThreadExecutor`) 的功能性和与 `Engine` 的正确交互。
✅ 1.4.3 验证虚拟线程集成 (`Executors.newVirtualThreadPerTaskExecutor()`) 在 `ExtensionContext` 中是否能够有效隔离阻塞操作，并通过 `CompletableFuture` 异步返回结果。
✅ 1.4.4 `BaseExtension` 基类：验证其自动生命周期管理、内置异步处理、错误处理和重试、性能监控、资源管理、便捷配置获取、健康检查机制等功能是开箱即用且符合预期。
✅ 1.4.5 `ExtensionMetrics`：集成 `Dropwizard Metrics`，确认能够准确收集和记录各种消息、命令、数据、音视频帧的接收、处理、错误信息和时间戳。这包括定义和使用计数器、计时器、仪表等指标类型。
✅ 1.4.5.1 `ExtensionMetrics` Gauge 动态数据源集成（活跃任务数）。
✅ 1.4.5.2 `ExtensionMetrics` Gauge 动态数据源集成（队列大小）：需 `Engine` 或更全局层面支持，后续在 `Engine` 监控任务中实现。
✅ 1.4.5.3 `ExtensionMetrics` Gauge 动态数据源集成（内存使用量）：需 `Engine` 或更全局层面支持，后续在 `Engine` 监控任务中实现。
✅ 1.4.6 实现一个功能最小化但完整的 `SimpleEchoExtension`，能够接收任何 `Data` 或 `Command` 并将其原样回显，用于端到端测试。
1.5.1 MsgPack 协议实现
✅ 1.5.1.1 引入 `msgpack-java` 依赖。
✅ 1.5.1.2 `MessageEncoder`：确认其能够将 `Message` 对象序列化为**顶层为 `MsgPack EXT` 类型 (`TEN_MSGPACK_EXT_TYPE_MSG`) 的字节流**。
✅ 1.5.1.3 `MessageDecoder`：确认其能够从**顶层为 `MsgPack EXT` 类型 (`TEN_MSGPACK_EXT_TYPE_MSG`) 的字节流**中提取原始消息数据，并进行二次反序列化为 `Message` 对象。
✅ 1.5.1.4 实现 `MsgPack EXT` 类型的定制处理：确保 `MessageEncoder` 和 `MessageDecoder` 能够正确处理 `TEN` 框架自定义的 `MsgPack EXT` 类型（`TEN_MSGPACK_EXT_TYPE_MSG`），实现消息的**两阶段序列化/反序列化**，以达到语义对等。
✅ 1.5.1.5 编写 `MsgPack` 编解码的**单元测试**，确保其能正确地对所有 `Message` 子类型进行往返序列化和反序列化，特别是涉及 `ByteBuf` 的 `AudioFrame`/`VideoFrame`。
1.5.2 基础 Netty TCP 服务器构建
✅ 1.5.2.1 搭建一个基本的 Netty TCP Server (`ServerBootstrap`)，监听特定端口，用于接收 `TEN` 框架的二进制 MsgPack 消息。
✅ 1.5.2.2 定义 Netty `ChannelInitializer` 和 `ChannelPipeline`，其中包含 `LengthFieldBasedFrameDecoder` (解决 TCP 粘包/半包问题)、`MessageDecoder` 和 `MessageEncoder`。
✅ 1.5.2.3 实现一个 `EngineChannelInboundHandler` (或类似名称)，负责将 Netty `Channel` 中解码后的 `Message` 对象提交到 `Engine` 的 `inboundMessageQueue`。
✅ 1.5.2.4 实现 `Engine` 能够通过 Netty `Channel` 将 `Extension` 发送的 `Message` （尤其是 `CommandResult` 或流式 `Data`）回传给客户端。这需要建立 `Engine` 到 `Channel` 的可靠映射关系 (`ChannelRegistry`)。
✅ 1.5.2.5 实现连接管理 (`ChannelActive`/`ChannelInactive`)，包括跟踪活跃连接 (`Channel` 引用)、处理连接断开事件（例如，通知 `Engine` 相关的 `Command` 需要清理）。
1.5.3 简化的 HTTP/JSON RPC 入口
✅ 1.5.3.1 搭建一个简化的 Netty HTTP Server，支持 `POST` 请求。
✅ 1.5.3.2 实现一个 HTTP `ChannelHandler`，将特定的 HTTP 请求 (例如，`POST /start_graph` 携带 JSON Payload) 解析为 `TEN Command` 对象，并提交到 `Engine`。
✅ 1.5.3.3 实现将 `Engine` 返回的 `CommandResult` 转换为 HTTP 响应 (JSON 格式) 返回给请求方。
✅ 1.5.3.3.1 改进 `HttpCommandResultOutboundHandler`：根据 `CommandResult` 的业务状态（例如，成功/失败）动态设置 HTTP 响应的状态码（例如，4xx 错误码而非统一 500）。
✅ 1.6.1 编写一个**可运行的最小示例** (`Main` 类或 `Application` 类)，能够： 启动 `Engine`。 启动基础的 Netty Websocket/TCP（预留支持扩展） Server 和简化的 HTTP Server。 注册一个 `SimpleEchoExtension`。 通过 HTTP 接口发送一个 `start_graph` 命令（定义包含 `SimpleEchoExtension` 的简单图，JSON 格式）。 通过 TCP/MsgPack 接口发送一个 `Data` 消息给 `SimpleEchoExtension`。 通过 TCP/MsgPack 接口接收 `SimpleEchoExtension` 的回显 `Data` 消息。 通过 HTTP 接口发送 `stop_graph` 命令。
✅ 1.6.2 编写集成测试用例，自动化验证上述端到端流程。
✅ 1.6.2.1 改进集成测试清理：确保 `sendTcpDataMessage` 中的 Netty `EventLoopGroup` 在测试完成后被正确关闭，避免资源泄露。
✅ 1.6.3 验证消息在网络层、`Engine`、`Extension` 之间的正确发送、路由、处理和结果回溯。
✅ 1.6.4 验证 `Extension` 的生命周期和基础健康状态。
2.1.1 定义图配置 DSL（例如，基于JSON/YAML），能够完整描述 `nodes` 和 `connections`。
✅ 2.1.2 实现图配置解析器 (`GraphLoader` / `GraphParser`)，能够加载和解析图定义为 Java 对象模型。
✅ 2.1.3 动态创建和销毁消息处理图实例（`graphId` 的概念）。
✅ 2.1.4 支持运行时动态添加、移除和更新 Extension 实例（通过管理命令）。
✅ 2.1.5 实现图内消息的复杂路由逻辑（例如，基于消息内容、条件路由、广播、多目标）。
✅ 2.1.6.1 Engine重构，独立routeManager [in_progress]
✅ 2.1.6.2 Engine重构，独立PatheManager [in_progress]
✅ 2.1.6.3 Engine重构，独立InternalCommnadHandler
✅ 2.1.6.4 完善pathIn、pathOut
✅ 2.1.6.5 ExtensionContext 升级为 AsyncExtensionEnv 对齐 python/c 的ten-framework
✅ 2.1.6.6 重新深度理解并重构了 cmd、cmdResult 异步回调的支持
2.2.1 实现基于消息内容、优先级或来源的高级路由策略。
2.2.2 在 `Engine` 内部引入优先级队列，确保高优先级消息优先处理。
2.2.3 设计和实现消息截止时间/超时机制，对过期消息进行处理。
2.3.1 实现 WebSocket Server，用于支持 Web 客户端的实时音视频流和大数据传输。
2.3.2 优化 HTTP/JSON RPC 接口，支持批量请求和长连接。
2.3.3 为不同的消息类型和API路径配置不同的 Netty `ChannelPipeline`。
2.3.4 实现消息到 HTTP/WebSocket 协议的自动转换和适配。
2.4.1 设计和实现跨 `Engine` 实例的连接迁移机制（如果需要，模拟 `C` 语言 `TEN_CONNECTION_MIGRATION_STATE` 的语义）。
2.4.2 实现会话状态持久化和恢复（例如，`LLM` 对话历史）。【待定】
2.4.3 管理客户端连接的生命周期，包括心跳检测和空闲连接关闭。
2.5.1 引入 Circuit Breaker 模式，隔离故障 `Extension`。
2.5.2 实现消息重试策略（除了 `Extension` 内置的，考虑 `Engine` 级别的）。
2.5.3 实现死信队列（Dead Letter Queue），处理无法路由或处理失败的消息。
2.5.4 异常报告和分析：集成更详细的异常栈跟踪和错误上下文。
3.1.1 选择一个典型的 C++ Extension 进行分析和迁移。
3.1.2 翻译 C++ 核心逻辑到 Java 代码。
3.1.3 处理 C++ 特有的内存管理和裸指针操作（例如，通过 `java.nio.ByteBuffer` 或 JNI 封装）。
3.1.4 适配 C++ Extension 的性能特点，寻找 Java 对等优化。
3.2.1 选择一个典型的 Go Extension 进行分析和迁移。
3.2.2 翻译 Go 并发模型和协程到 Java 虚拟线程。
3.2.3 适配 Go Extension 的特定库和数据结构。
3.3.1 选择一个典型的 Python Extension（特别是 `AI_BASE` 相关的）进行分析和迁移。
3.3.2 翻译 Python 动态特性到 Java 静态类型。
3.3.3 适配 Python 特定的 `LLM`、`Tool`、`ASR` 等集成模式。
3.3.4 考虑 Python 科学计算库的替代方案（例如，ND4J、TensorFlow for Java）。
3.4.1 在 `Engine` 的 `runMessageLoop` 中集成 Agrona `IdleStrategy` 接口，优化空闲 CPU 使用。
3.4.2 利用 Agrona 队列的 `drain()` 方法重构批量消息处理，提高吞吐量。
3.4.3 PathTable 优化：利用 `Long2ObjectHashMap` 优化 `PathTable` 的核心存储，消除 `UUID` 对象的性能开销。
3.5.1 使用 Netty `ByteBufAllocator` 进行高效的 `ByteBuf` 分配和回收。
3.5.2 在音视频帧传输中实现零拷贝，减少数据复制。
3.5.3 优化 `Message` 序列化和反序列化过程中的内存分配。
3.6.1 分析和优化 Extension 中虚拟线程的使用模式。
3.6.2 监控虚拟线程的创建、调度和销毁，识别潜在 bottlenecks。
3.6.3 调整虚拟线程池参数，以适配不同的 Extension 负载。
4.1.1 集成服务发现框架（如 Spring Cloud Eureka, HashiCorp Consul 或 Kubernetes DNS）。【待定】
4.1.2 实现 Engine 实例的自动注册和发现。
4.1.3 设计和实现集群成员管理和健康状态同步机制。
4.1.4 考虑引入集群协调服务（如 Apache ZooKeeper, etcd）。
4.2.1 实现客户端侧或服务端侧的负载均衡策略。
4.2.2 设计和实现 Engine 实例的故障转移和容灾机制。
4.2.3 消息的可靠投递和幂等性保证。
4.3.1 在整个系统中引入并配置Dropwizard Metrics进行全面的系统监控。
4.3.2 将 Dropwizard Metrics 指标暴露给 Prometheus。
4.3.3 配置 Grafana 仪表盘，可视化 Engine 和 Extension 的性能指标。
4.3.4 设置关键指标的告警规则。
4.3.5 收集 JVM 指标和系统资源使用情况。
4.4.1 实现基于 Token 的身份认证机制（如 JWT）。【待定】
4.4.2 实现基于角色的访问控制（RBAC）。【待定】
4.4.3 保护内部 API 和通信渠道。
4.5.1 编写 Dockerfile，构建 Java Runtime 的 Docker 镜像。
4.5.2 编写 Kubernetes 部署清单（Deployment, Service, Ingress）。
4.5.3 建立 CI/CD 流水线，实现自动化构建、测试和部署。
4.5.4 编写运维脚本和工具，简化日常管理。
