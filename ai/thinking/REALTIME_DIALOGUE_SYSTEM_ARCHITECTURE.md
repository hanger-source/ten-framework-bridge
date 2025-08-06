# 实时对话系统架构设计

本文档旨在厘清构建一个完整的、基于 `ten-framework` 思想的实时对话 Java 底座所需要的关键架构组件和设计决策。

---

## 1. 网络边界 (The Network Boundary)

网络边界是系统与外部世界（客户端、其他服务）交互的门户。`ten-framework` 通过 `Protocol` 和 `Connection` 两个核心抽象来定义这个边界。其设计中最核心、最精妙的部分，是对**连接的线程所有权迁移**的处理。

### 1.1 C 版本的核心抽象

- **`ten_protocol_t`**: **协议的“驱动程序”**
  - **职责**: 定义了一套标准的网络行为接口（通过函数指针实现），如 `listen` (监听)、`connect_to` (连接)、`on_output` (发送消息)。它负责将底层的**字节流**与 `ten-framework` 内部的 `ten_msg_t` 对象进行**双向转换**。
  - **角色**:
    - **监听协议 (`ROLE_LISTEN`)**: 附属于 `App`，在某个地址上监听，当新连接到来时，会创建一个“通信协议”实例来服务这个连接。
    - **通信协议 (`ROLE_IN_*`, `ROLE_OUT_*`)**: 附属于 `Connection`，负责实际的数据收发和协议编解码。

- **`ten_connection_t`**: **对话的“通道”**
  - **职责**: 抽象了一个端到端的通信会话。它是一个状态容器，持有 `uri`、状态（`state`）、以及一个 `protocol` 实例。
  - **核心复杂性**: **线程迁移 (Migration)**。一个 `Connection` 最初在 `App` 线程中被创建，但当它需要与一个运行在独立线程中的 `Engine` 通信时，它的所有权（以及所有相关的IO操作）必须被安全地、异步地从 `App` 线程迁移到 `Engine` 线程。`TEN_CONNECTION_MIGRATION_STATE` 这个复杂的枚举和相关逻辑，完全是为了解决这个跨线程所有权转移的难题。

### 1.2 Java 版本的等价实现与简化

在 Java 中，我们可以利用 `Netty` 这一成熟的高性能网络框架，来极大地简化 C 版本中复杂的线程手动管理，并实现语义上的完美对等。

- **`io.netty.channel.Channel` 等价于 `Connection`**: Netty 的 `Channel` 完美地代表了一个网络连接。它是一个线程安全的、高级的会话抽象。

- **`io.netty.channel.ChannelPipeline` 等价于 `Protocol`**: Netty 的 Pipeline 机制允许我们将一系列的处理器 (`ChannelHandler`) 串联起来，形成一个数据处理流水线。这正是 `Protocol` 的职责。一个典型的实时对话 Pipeline 可能如下：

  ```
  [ 客户端 ] <--> [ Channel ]
                     |
               [ ChannelPipeline ]
                     |
  +-------------------------------------------------+
  | INBOUND (数据入)                                |
  |    +----------------------------------------+   |
  |    | WebSocketFrameDecoder (处理WS握手和帧) |   |
  |    +----------------------------------------+   |
  |                     |                           |
  |    +----------------------------------------+   |
  |    | TenMessageDecoder (将字节解码为Message) |   |  <-- 我们的业务逻辑
  |    +----------------------------------------+   |
  |                     |                           |
  |    +----------------------------------------+   |
  |    | EngineIngressHandler (将Message喂给Engine) |  <-- 我们的业务逻辑
  |    +----------------------------------------+   |
  +-------------------------------------------------+
                     |
  +-------------------------------------------------+
  | OUTBOUND (数据出)                               |
  |    +----------------------------------------+   |
  |    | EngineEgressHandler (从Engine接收Message) | <-- 我们的业务逻辑
  |    +----------------------------------------+   |
  |                     |                           |
  |    +----------------------------------------+   |
  |    | TenMessageEncoder (将Message编码为字节) |   |  <-- 我们的业务逻辑
  |    +----------------------------------------+   |
  |                     |                           |
  |    +----------------------------------------+   |
  |    | WebSocketFrameEncoder (将字节编码为WS帧)|   |
  |    +----------------------------------------+   |
  +-------------------------------------------------+
  ```

- **用 `EventLoop` 简化线程迁移**:
  - 在 `Netty` 中，每一个 `Channel` 都会被注册到一个 `EventLoop` 上，这个 `EventLoop` 是一个单线程的执行器，负责处理该 `Channel` 所有的 IO 事件。
  - **这完美地解决了线程迁移问题**：当我们在 `App` 中接受一个新 `Channel` 后，我们只需要将这个 `Channel` **注册到目标 `Engine` 所绑定的那个 `EventLoop` (即 `SingleThreadExecutor`) 上**。
  - 从此以后，这个 `Channel` 上的所有 `Handler` 的代码都会在该 `Engine` 的线程中执行，完全避免了手动管理 `MIGRATION_STATE` 的复杂性和风险。Netty 为我们保证了这一切都是线程安全的。

### 1.3 设计决策 (网络边界)

1.  **技术选型**: 采用 **Netty** 作为底层网络框架。
2.  **抽象设计**:
    - 定义 `Connection` 接口，其实现将内部持有 Netty 的 `Channel`。
    - 不再需要 `Protocol` 接口。它的功能被分解为一系列可重用的 `ChannelHandler`。我们将创建 `TenMessageEncoder` 和 `TenMessageDecoder` 来完成核心的业务协议转换。
3.  **核心流程**:
    - `App` 负责创建和配置 Netty 的 `ServerBootstrap`。
    - 当一个新的 `Channel` 被 `accept` 后，`App` 会根据初始消息（例如 `start_graph` 命令）来决定这个 `Channel` 应该由哪个 `Engine` 处理。
    - `App` 随后将这个 `Channel` 注册到目标 `Engine` 的 `EventLoop` 上。
    - `Channel` 的 Pipeline 中最后的 `EngineIngressHandler` 会负责调用 `engine.postMessage()`，将解码后的消息送入 `Engine` 的内部队列。

---

## 2. 图的组装与生命周期 (Graph Composition & Lifecycle)

一个“实时对话”流程本质上是多个 `Extension` 节点构成的数据处理图。理解这个图如何定义、实例化和启动，是构建整个系统的核心。

### 2.1 C 版本的核心抽象 (`start_graph` 命令)

`ten-framework` 通过一个名为 `start_graph` 的特殊命令来完成图的定义和启动。该命令的核心载荷是一个 **JSON 对象**，这个 JSON 定义了图的完整拓扑。

- **`nodes` 数组 (定义参与者)**:
  - 这是图定义的核心，它列出了所有需要被实例化的 `Extension` 节点。
  - 每个 `node` 对象包含：
    - `name` (string): `Extension` 的**实例名**，在图内唯一，是路由的目标。
    - `addon` (string): `Extension` 的**类型名**，指向一个已注册的 `Addon` 插件工厂。
    - `app` (string): 指定此 `Extension` 实例在哪个 `App` 进程中运行。

- **`connections` 数组 (定义静态路由)**:
  - 它定义了图中节点之间**默认的、静态的**连接关系。
  - 每个 `connection` 对象包含：
    - `app` & `extension`: 定义了连接的**起点**。
    - `cmd`: 一个数组，定义了从该起点发出的特定**命令**的默认路由规则。
      - `name`: 命令名。
      - `dest`: 一个目标数组，定义了该命令的默认**终点**。

### 2.2 App 与 Engine 的角色

1.  **`App` 是“接待员”和“Engine 容器”**: `App` 负责监听网络端口，接收外部命令。当它收到一个 `start_graph` 命令时，它会创建一个新的 `Engine` 实例来承载这个图的运行。
2.  **`Engine` 是“图构建者”和“生命周期管理者”**: `Engine` 在收到 `start_graph` 命令后，负责执行以下操作：
    - **解析 `nodes`**: 遍历 `nodes` 数组，通过 `addon` 类型名查找 `Addon` 工厂，创建出 `name` 指定的 `Extension` 实例。
    - **解析 `connections`**: 遍历 `connections` 数组，将这些静态路由规则加载到其内部的路由表（`path_table`）中。
    - **管理生命周期**: 依次调用所有已实例化 `Extension` 的 `on_init`, `on_start` 等生命周期方法。

### 2.3 路由机制：静态与动态的结合

`ten-framework` 提供了一套极为灵活的混合路由机制：

- **静态路由**: 由 `start_graph` 中的 `connections` 数组预先定义。当 `Extension` 调用 `send_cmd` 但未指定目的地时，`Engine` 会查询此路由表来决定消息的去向。
- **动态路由**: 由 `Extension` 在运行时调用 `_Msg.set_dest()` 方法即时决定。**动态路由的优先级高于静态路由**。

### 2.4 Java 版本的等价实现

1.  **图定义**: 我们可以同样采用 **JSON** 作为图的定义语言，其语法与 C 版本保持一致。这使得配置和代码可以分离。
2.  **图构建器 (`GraphBuilder`)**: 创建一个 `GraphBuilder` 类，负责解析图定义的 JSON。
    - `build(json)` 方法将返回一个 fully-configured 的 `Engine` 实例。
3.  **`App` 的职责**:
    - 持有一个 `AddonRegistry`，用于注册所有可用的 `Extension` 工厂（Addon）。
    - 它的 `startGraph(json)` 方法将：
        a. 调用 `GraphBuilder` 解析 JSON 并创建一个 `Engine` 实例。
        b. 将 `Engine` 实例存储起来进行管理。
        c. 启动 `Engine` 的主循环。
4.  **`Engine` 的职责**:
    - 在其构造函数或 `init` 方法中，接收 `GraphBuilder` 解析出的 `Extension` 实例列表和静态路由表。
    - 持有一个 `Map<String, Extension>` 用于实例名到实例的映射。
    - 持有一个 `RoutingTable` 对象用于存储静态路由信息。
    - 在 `dispatch` 消息时，如果消息没有动态目的地，则查询 `RoutingTable`。

这个设计将图的“定义”（JSON）、“构建”（`GraphBuilder`）和“执行”（`Engine`）清晰地分离开来，使得系统具有良好的扩展性和可维护性。

---

## 3. 端到端的完整流程 (End-to-End Flow)

将所有组件串联起来，我们可以描绘一个完整的端到端语音对话流程，以验证架构的完备性。

### 3.1 C 版本的核心抽象 (`Remote`)

`ten-framework` 使用 `ten_remote_t` 结构体作为 `Engine` 内部对一个外部连接的**代理**或**句柄**。

- **职责**: `remote` 是 `engine` 和 `connection` 之间的桥梁。`engine` 不直接持有 `connection` 的引用，而是通过 `remote` 来间接管理和通信。
- **生命周期**: 当一个 `connection` 成功迁移到 `engine` 线程后，`engine` 会为其创建一个对应的 `remote` 对象，并以 `uri` 为键，存储在一个哈希表中。
- **作用**: `remote` 对象解决了“如何将消息发回给源头客户端”这一关键问题。

### 3.2 流程示例：语音助手交互

1.  **连接与图的启动**:
    - 客户端通过 WebSocket 连接到 `App` 的监听端口。
    - 客户端发送第一条消息：一个 `start_graph` 命令，其 JSON 定义了包含 ASR, LLM, TTS 等 `Extension` 的图。
    - `App` 创建一个 `Engine` 实例，并将该 `Connection` 迁移到 `Engine` 的线程。
    - `Engine` 为此 `Connection` 创建一个 `Remote` 对象（我们称之为 `Remote_Client`），并启动图。

2.  **上行数据流 (语音输入)**:
    - 客户端通过 `Connection` 发送 `AudioFrame`。
    - `Connection` 的 `Protocol` 将其解码，并通过 `Remote_Client` 代理，调用 `engine.postMessage()`。
    - `Engine` 将 `AudioFrame` 投递给图的入口 `ASR_Extension`。

3.  **图内处理与服务调用**:
    - `ASR_Extension` 处理音频，输出包含文本的 `Data` 消息，并 `set_dest` 到 `LLM_Extension`。
    - `LLM_Extension` 收到文本，构造 prompt，然后向 `OpenAI_Extension` 发起**流式 RPC 调用**。

4.  **下行数据流 (语音与文本输出)**:
    - `LLM_Extension` 在 RPC 回调中，收到了 `OpenAI_Extension` 返回的**流式**文本。
    - **并行处理**:
        - **(a) 文本字幕**: `LLM_Extension` 将收到的每一段文本，包装成一个新的 `Data` 消息。然后，它调用 `set_dest`，将目的地设置为**源 `Remote_Client` 的 URI**。`Engine` 查表找到 `Remote_Client`，将文本消息通过 `Connection` 发回客户端。
        - **(b) 语音合成**: 同时，`LLM_Extension` 将同样的文本 `Data` 消息，`set_dest` 到图中的 `TTS_Extension`。
    - `TTS_Extension` 接收文本，合成出 `AudioFrame`。
    - `TTS_Extension` 也调用 `set_dest`，将这个 `AudioFrame` 的目的地也设置为**源 `Remote_Client` 的 URI**。
    - `Engine` 再次查表找到 `Remote_Client`，将合成的音频流通过 `Connection` 发回客户端。

### 3.3 Java 版本的等价实现

在我们的 Java 设计中，`Remote` 的概念可以被一个更简单的机制取代。

- **`Connection` 即 `Remote`**: `Engine` 可以直接持有一个 `Map<String, Connection>`，其中 `String` 可以是 `ChannelId` 或其他唯一会话标识。
- **上下文注入**: 当一个消息从某个 `Connection` 进入 `Engine` 时，`Engine` 在调用 `Extension` 的 `onMessage` (等价)方法时，可以将这个“源 `Connection`”的引用（或其 ID）作为一个参数，或者通过一个 `Context` 对象传递进去。
- **回传**: 当 `Extension` 需要回传数据时，它只需从上下文中获取到源 `Connection` 的引用，然后调用 `connection.send(message)` 即可。Netty 会处理好底层的发送逻辑。

这个设计避免了 `Remote` 这一层额外的间接性，使得逻辑更直接，更符合 Java 开发者的直觉。至此，一个完整的、健壮的、可扩展的实时对话底座的架构蓝图已经全部完成。
