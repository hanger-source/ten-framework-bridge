### `ten-framework` Java 命令与结果处理的语义对齐实现

本文档旨在详细阐述 `ten-framework` 在 Java 端如何实现与 Python/C 核心在命令（`Cmd`）和命令结果（`CmdResult`）处理方面保持同等语义支持。我们将重点关注其异步机制、回调处理以及与核心设计理念（如“即发即忘”）的对齐。

#### 1. 核心设计理念在 Java 端的体现

`ten-framework` 的 Python/C 核心以单线程事件循环（`runloop`）为基础，强调消息的 FIFO 顺序、非阻塞操作、线程安全及可预测性。在 Java 端，我们通过以下方式来对齐这些核心设计：

- **异步编程模型**: Java 8 引入的 `CompletableFuture` 提供了一种强大的异步编程工具，用于处理命令结果的异步返回。这与 Python `ten_env.send_cmd` 通过注册 `result_handler` 异步回调的模式在语义上保持一致。
- **虚拟线程 (Virtual Threads)**: 利用 Java 21+ 的虚拟线程，可以高效地处理大量并发的非阻塞 I/O 操作，而无需管理传统线程池的复杂性，从而更好地模拟 `runloop` 的高效事件处理能力。
- **“即发即忘” (Fire-and-Forget) 消息提交**: 核心设计中，消息的提交（包括命令和命令结果）是“即发即忘”的，即发送方不直接等待提交操作的成功或失败反馈（Python 端返回 `Optional[TenError]`，表示仅在提交操作本身发生即时错误时才返回错误对象，并非等待消息处理结果）。Java 端也应遵循此原则，通过 `void` 返回类型和异步执行器实现。

- **Engine 的职责**: `Engine` 的核心职责是底层的消息提交 (`submitMessage`) 和调度，它不直接返回命令结果的 `CompletableFuture`。

#### 2. Java `Cmd` 与 `CmdResult` 的数据结构

Java 端对 `Cmd` 和 `CmdResult` 的定义与 Python/C 核心保持一致，确保数据结构能够承载所有必要的语义信息。

- **`Command` (对应 Python `_Cmd`)**:
  - 继承自 `Message`。
  - 包含 `commandId` 和 `parentCommandId` (类型为 `long`)，用于唯一标识命令及其父子关系。这与 C 核心使用 `uint64_t` 来高效处理命令 ID 保持一致。
  - 通过 `properties` (`Map<String, Object>`) 存储灵活的键值对，例如命令名称、参数、客户端上下文（如 `clientAppUri`, `clientGraphId`）。这模拟了 Python/C 核心中 `ten_value_kv_t` 或 JSON 序列化属性的使用。
  - `generateCommandId()` 方法使用 `UUID.randomUUID().getMostSignificantBits()` 生成 `long` 类型的 `commandId`，确保唯一性。

- **`CommandResult` (对应 Python `_CmdResult`)**:
  - 继承自 `Message`。
  - 包含 `commandId` 和 `parentCommandId` (`long`)，以及 `statusCode` (`StatusCode` 枚举)。
  - 关键是其 `properties` (`Map<String, Object>`) 用于承载实际的命令执行结果负载。这与 Python/C 核心中 `_CmdResult` 将实际 payload 放入 `properties` 的设计一致。
  - `result` 字段 (类型为 `Object`) 用于直接存储反序列化后的命令结果负载，方便 Java 端直接访问。

#### 3. 命令的发送与结果的异步返回 (`AsyncExtensionEnv.sendCommand` 与 `ten_env.send_cmd` 的对齐)

在 Java 端，`AsyncExtensionEnv` (作为 Extension 与 Engine 交互的接口) 提供的 `sendCommand` 方法是 Extension 发起命令并异步获取结果的核心入口，它与 Python `ten_env.send_cmd` 的语义高度对齐。

- **`AsyncExtensionEnv.sendCommand(Command command)`**:
  - **返回类型**: `CompletableFuture<Object>`。此 `CompletableFuture` 不代表命令提交的成功，而是代表未来命令执行结果（即 `CmdResult` 中包含的实际业务数据）的异步通知。这与 Python `ten_env.send_cmd` 通过 `result_handler` 异步回调来传递 `CmdResult` 的机制完全对齐。
  - **内部机制 (在 `EngineAsyncExtensionEnv` 中实现)**:
    1.  创建一个 `CompletableFuture<Object>` 实例。
    2.  将该 `CompletableFuture` 与 `command.getCommandId()` 关联，并由 `EngineAsyncExtensionEnv` 内部进行管理（例如，存储在一个 `ConcurrentMap<Long, CompletableFuture<Object>>` 中）。
    3.  将 `Command` 提交给 `Engine` 的 `submitMessage` 方法。`submitMessage` 返回 `void`，遵循“即发即忘”原则。
    4.  `EngineAsyncExtensionEnv` 返回 `CompletableFuture<Object>` 给调用方 (`Extension`)，调用方可以异步地等待或链式处理这个 Future。

#### 4. `MessageSubmitter` 的语义对齐

`MessageSubmitter` 接口在 Java 端扮演着将消息（包括 `Command` 和 `CommandResult`）提交到 Engine 内部队列的角色。

- **`submitMessage(Message message, String channelId)` / `submitMessage(Message message)`**:
  - **返回类型**: `void`。这严格遵循 Python/C 核心的“即发即忘”语义。提交消息操作本身不提供即时的是否成功反馈。Python `ten_env.send_data`, `send_video_frame`, `send_audio_frame` 等方法都返回 `Optional[TenError]`，表示提交操作本身可能出错，但不等待消息处理结果。Java 的 `void` 返回和内部错误处理与之对齐。
  - **错误处理**: 如果内部队列满或其他提交问题发生，Engine 会在内部处理这些情况（例如日志记录），而不是通过返回 `boolean` 来强制调用方处理。

#### 5. `AsyncExtensionEnv` 与扩展的交互 (`TenEnv` 在 Extension 侧的对齐)

`AsyncExtensionEnv` 是 Java 端 Extension 与 Engine 异步交互的关键接口，它与 Python `ten_env.py` 中定义的 `TenEnv` 实例在 Extension 内部被使用的方式保持高度一致。

- **`AsyncExtensionEnv` (对应 Python `TenEnv`)**:
  - 所有消息发送方法（如 `sendResult`, `sendData`, `sendVideoFrame`, `sendAudioFrame`）都应返回 `void`。这体现了“即发即忘”的原则。Extension 将消息或结果提交给 Engine，但不等待其即时处理结果。这与 Python `ten_env.return_result` 返回 `Optional[TenError]` 的语义一致。
  - 内部实现 (`EngineAsyncExtensionEnv`) 通过 `ExecutorService`（例如使用 `Executors.newVirtualThreadPerTaskExecutor()` 创建的虚拟线程池）来异步执行实际的提交逻辑，确保 Extension 线程不被阻塞。
  - **属性操作**: `AsyncExtensionEnv` 应提供与 Python `TenEnv` 中 `get_property_*` 和 `set_property_*` 家族方法（如 `get_property_to_json`, `set_property_from_json`, `is_property_exist`, `get_property_int`, `set_property_int` 等）语义一致的方法，以允许 Extension 访问和修改运行时属性。

#### 6. 命令结果的回溯与消费

Java 端 `CmdResult` 的回溯和最终消费与 Python/C 核心的 `PathTable` 机制保持高度一致。

- **`PathOut` (对应 C `ten_path_out_t`)**:
  - 新增 `private transient CompletableFuture<Object> resultFuture;` 字段，用于存储与出站命令关联的 `CompletableFuture`。
  - 当 `Cmd` 从 `AsyncExtensionEnv.sendCommand` 发出时，其对应的 `CompletableFuture` 会被传入 `PathOut`（在 `EngineAsyncExtensionEnv` 中负责创建和关联）。

- **`PathManager.completeCommandResult(CommandResult commandResult)`**:
  - 当 Engine 收到一个 `CommandResult` 时，`PathManager` 会根据 `CmdResult` 的 `commandId` 查找对应的 `PathOut` 实例。
  - **关键**: 从 `PathOut` 中获取 `resultFuture`。
  - 如果 `CmdResult` 表示成功，则调用 `resultFuture.complete(commandResult.getResult())`，用 `CmdResult` 中实际的 `result` payload 完成 `CompletableFuture`。
  - 如果 `CmdResult` 表示失败，则调用 `resultFuture.completeExceptionally(new RuntimeException(commandResult.getError()))`，以异常方式完成 `CompletableFuture`。
  - 完成后的 `PathOut` 会被清理，并且 `CmdResult` 会被重新构造（修改 `commandId` 和翻转 `sourceLocation/destinationLocation`）以进行进一步的回溯，直到回到原始发起者或最终被处理。

- **`Engine.processCommandResult(CommandResult commandResult)`**:
  - 这是 Engine 内部处理 `CommandResult` 的核心方法。
  - 它会获取 `PathOut`，并调用 `pathOut.getResultFuture().complete(...)` 或 `completeExceptionally(...)` 来完成对应的 `CompletableFuture`。
  - `Engine` 不再管理 `commandFutures`。所有 `CompletableFuture` 的管理和完成都通过 `PathOut` 进行。

#### 7. 内部命令处理器的语义对齐

为了支持内部命令（如 `StartGraphCommand`）也能够以异步方式返回结果，`InternalCommandHandler` 接口需要进行调整。

- **`InternalCommandHandler.handle(Command command, Engine engine, CompletableFuture<Object> resultFuture)`**:
  - 新增 `CompletableFuture<Object> resultFuture` 参数。
  - 内部命令处理器在执行完逻辑后，将不再直接返回 `CommandResult`，而是通过调用传入的 `resultFuture.complete(result)` 或 `resultFuture.completeExceptionally(exception)` 来通知结果。
  - 对于那些不期望直接返回结果给 `AsyncExtensionEnv.sendCommand` 调用者的内部命令，仍然可以继续使用 `engine.submitMessage(result)` 来进行“即发即忘”的消息提交（例如，用于客户端上下文的路由）。

#### 总结

通过上述设计和实现，Java `ten-framework` 能够在语义上与 Python/C 核心保持一致，特别是在异步命令发送、结果回调和“即发即忘”的消息提交方面。利用 `CompletableFuture` 和虚拟线程，Java 端能够高效地处理复杂的异步操作，同时保持与核心 `runloop` 设计理念的对齐。
