# Java 设计方案：`ten-framework` 核心组件对齐 (`App`, `Remote`, `Connection`, `Protocol`)

本设计方案旨在将 `ten-framework` (Python/C 版本) 中 `App`、`Engine`、`Connection`、`Protocol` 和 `Remote` 的核心设计理念，完整且语义对等地迁移至 Java 生态，并充分利用 Netty 框架进行网络通信。

#### 1. 核心模型与消息 (`Message`, `Location` 等)

在讨论具体的组件之前，必须确保基础消息模型能够表达 `ten-framework` 的核心语义。

- **`Location` 类 (`com.tenframework.core.message.Location.java`)**
  - **C/Python 对齐**：对应 `ten_loc_t` 结构体。
  - **设计原则**：封装消息的逻辑位置，由 `appUri`、`graphId`、`extensionName` 组成。应支持 Jackson 序列化和反序列化，并提供判断是否为空和调试输出的方法。
  - **关键字段**：`String appUri`, `String graphId`, `String extensionName`.

- **`Message` 接口/抽象类 (`com.tenframework.core.message.Message.java`)**
  - **C/Python 对齐**：对应 `ten_msg_t` 结构体。
  - **设计原则**：作为所有消息类型的基类或接口，定义消息的基本属性，如 `type` (`CMD`, `DATA`, `EVENT`), `id`, `srcLoc`, `destLoc` 等。它将是 `Protocol` 层编解码的核心对象。
  - **关键字段/方法**：
    - `MessageType type` (枚举，如 `CMD`, `DATA`, `EVENT`)
    - `String id` (消息的唯一ID)
    - `Location srcLoc` (消息来源)
    - `List<Location> destLocs` (消息目的地列表)
    - `Map<String, Object> properties` (消息属性)
    - 抽象方法 `toPayload()` 或具体实现，用于获取消息的实际内容。

- **具体消息类型**：
  - `CommandMessage` (继承 `Message`)：包含命令名称、参数等。
  - `DataMessage` (继承 `Message`)：包含原始数据字节数组或其他结构化数据。
  - `EventMessage` (继承 `Message`)：包含事件类型、事件数据等。

#### 2. `Protocol` 设计 (`com.tenframework.server.protocol.*`)

`Protocol` 层负责消息的序列化和反序列化，将外部字节流转换为内部 `Message` 对象，反之亦然。在 Netty 中，这通常通过 `ByteToMessageDecoder` 和 `MessageToByteEncoder` 实现。

- **`MessageDecoder` (`com.tenframework.server.message.MessageDecoder.java`)**
  - **C/Python 对齐**：`ten_protocol_on_input` 及其内部的 MsgPack 反序列化逻辑。
  - **设计原则**：继承 Netty 的 `ByteToMessageDecoder`。负责从 Netty 的 `ByteBuf` 中读取原始字节流，并将其反序列化为 `Message` 对象。需要处理 MsgPack 扩展类型 (`TEN_MSGPACK_EXT_TYPE_MSG`)。
  - **关键方法**：`decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)`。
  - **实现细节**：
    - 使用 `org.msgpack.jackson.dataformat.MessagePackFactory` 和 `com.fasterxml.jackson.databind.ObjectMapper` 进行 MsgPack 反序列化。
    - 特别处理 `TEN_MSGPACK_EXT_TYPE_MSG`，读取其内部字节流并反序列化为具体的 `Message` 子类（例如，通过 `ObjectMapper` 结合 `@JsonSubTypes` 和 `@JsonTypeName`）。
    - 解码成功后，将 `Message` 对象添加到 `out` 列表中，供后续 `ChannelInboundHandler` 处理。

- **`MessageEncoder` (`com.tenframework.server.message.MessageEncoder.java`)**
  - **C/Python 对齐**：`ten_protocol_on_output` 及其内部的 MsgPack 序列化逻辑。
  - **设计原则**：继承 Netty 的 `MessageToByteEncoder<Message>`。负责将 `Message` 对象序列化为 `ByteBuf`，以便通过网络发送。
  - **关键方法**：`encode(ChannelHandlerContext ctx, Message msg, ByteBuf out)`。
  - **实现细节**：
    - 使用 `ObjectMapper` 将 `Message` 对象序列化为 MsgPack 格式。
    - 对于 `Message` 对象，需要将其封装为 `TEN_MSGPACK_EXT_TYPE_MSG` 扩展类型。这意味着首先将 `Message` 对象序列化为内部字节流，然后将这个字节流作为 MsgPack 扩展类型的 Payload。
    - 序列化后的 `ByteBuf` 将通过 Netty 的管道发送出去。

#### 3. `Connection` 设计 (`com.tenframework.server.connection.Connection.java`)

`Connection` 代表一个与外部客户端的活跃网络连接。它管理连接的生命周期，并作为 `Protocol` 层与 `Engine` 层之间的桥梁。

- **C/Python 对齐**：`ten_connection_t` 结构体。
- **设计原则**：
  - **封装 Netty `Channel`**: 每个 `Connection` 实例持有一个 Netty `Channel` 引用，用于实际的网络通信。
  - **消息转发**: 将从 `Protocol` 层解码的 `Message` 转发到 `Engine` 的消息队列。
  - **状态管理**: 管理连接的状态（例如，连接中、已连接、断开连接），以及可能的迁移状态。
  - **ID 管理**: 可能需要生成或持有连接的唯一 ID。
- **关键字段**：
  - `Channel channel` (Netty 的 Channel 实例)
  - `String connectionId` (连接的唯一标识，类似于 C/Python 中的 `connection_id`)
  - `Engine engine` (关联的 `Engine` 实例，可能通过构造函数注入)
  - `Location remoteLocation` (如果已知，表示远程连接的 `Location`)
- **关键方法**：
  - `onMessageReceived(Message message)`: 由 `ConnectionHandler` 调用，接收解码后的 `Message`。此方法会将消息提交到关联 `Engine` 的输入队列。
  - `sendMessage(Message message)`: 将 `Message` 序列化并通过 `channel` 发送到远程客户端。
  - `close()`: 关闭底层 Netty `Channel` 并清理资源。
  - `bindToEngine(Engine engine)`: 将此 `Connection` 绑定到特定的 `Engine` 实例（类似于 C/Python 中的 `ten_connection_bind_to_engine`）。

#### 4. `Remote` 设计 (`com.tenframework.server.remote.Remote.java`)

`Remote` 封装了一个远程 `Engine` 实例（可以是同一个进程中的另一个 `App` 的 `Engine`，也可以是另一个物理机器上的 `Engine`）。它提供了一种通过 `Connection` 将消息发送到远程 `Engine` 的机制。

- **C/Python 对齐**：`ten_remote_t` 结构体。
- **设计原则**：
  - **逻辑表示**: `Remote` 不直接处理网络通信，而是作为远程 `Engine` 的逻辑代表。
  - **消息代理**: 负责将本地 `Engine` 产生的发往远程的消息，通过 `Connection` 转发出去。
  - **生命周期管理**: 管理与远程 `Engine` 的连接（一个 `Remote` 可以管理多个底层 `Connection`）。
- **关键字段**：
  - `Location remoteEngineLocation` (远程 `Engine` 的 `Location`)
  - `List<Connection> connections` (管理与此 `Remote` 关联的所有 `Connection` 实例)
  - `Engine localEngine` (本地 `Engine` 的引用)
- **关键方法**：
  - `createConnection(String host, int port)`: 建立到远程 `Engine` 的新 `Connection`。
  - `sendMessage(Message message)`: 将消息通过可用的 `Connection` 发送到远程 `Engine`。需要根据 `Message` 的 `destLoc` 找到合适的 `Connection`。
  - `onConnectionClosed(Connection connection)`: 当一个 `Connection` 关闭时，`Remote` 更新其连接列表。

#### 5. `App` 设计 (`com.tenframework.server.app.App.java`)

`App` 是 `ten-framework` 应用的入口点和宿主，它负责初始化 `Engine`，启动网络服务（监听端口），并管理所有 `Connection` 和 `Remote`。

- **C/Python 对齐**：`ten_app_t` 结构体。
- **设计原则**：
  - **应用初始化**: 配置并启动 `Engine` 实例。
  - **网络服务**: 启动 Netty 服务器，监听传入连接。
  - **连接管理**: 维护所有活跃的 `Connection` 实例，并根据需要创建 `Remote` 实例。
  - **消息路由**: 作为外部消息进入 `Engine` 的主要路径，以及 `Engine` 内部消息发送到外部的协调者。
- **关键字段**：
  - `Engine engine` (关联的 `Engine` 实例)
  - `ServerBootstrap serverBootstrap` (Netty 服务器启动器)
  - `Map<String, Connection> activeConnections` (管理所有活跃的连接)
  - `Map<Location, Remote> remotes` (管理所有远程 `Engine` 实例)
- **关键方法**：
  - `start()`: 初始化 `Engine`，启动 Netty 服务器，并开始监听端口。
  - `stop()`: 关闭 Netty 服务器，停止 `Engine`，并关闭所有活跃连接。
  - `onNewConnection(Channel channel)`: 当 Netty 接收到新的客户端连接时，创建并注册一个新的 `Connection` 实例。
  - `handleInboundMessage(Message message)`: 从 `Connection` 接收到消息后，将消息提交给 `Engine` 的输入队列。
  - `sendOutboundMessage(Message message)`: `Engine` 内部产生需要发送到外部的消息时，通过此方法路由到对应的 `Connection` 或 `Remote`。

#### 6. Netty 集成

Netty 将作为底层的网络通信框架，与上述组件紧密集成。

- **服务器端 (Inbound)**：
  - **`ServerBootstrap`**: `App` 启动时配置 `ServerBootstrap` 监听端口。
  - **`ChannelInitializer`**: 配置 `ChannelPipeline`。
    - `MessageDecoder` (`ByteToMessageDecoder`)：将 `ByteBuf` 解码为 `Message`。
    - `MessageEncoder` (`MessageToByteEncoder`)：将 `Message` 编码为 `ByteBuf` (用于出站消息)。
    - `ConnectionHandler` (`SimpleChannelInboundHandler<Message>`)：核心处理器。当接收到解码后的 `Message` 时，它会：
      - 获取或创建对应的 `Connection` 实例（如果尚未创建）。
      - 将 `Message` 传递给 `Connection` 的 `onMessageReceived` 方法。
      - 处理连接的生命周期事件（激活、非激活、异常）。

- **客户端端 (Outbound/Inbound for Remote)**：
  - **`Bootstrap`**: `Remote` 需要连接到远程 `Engine` 时，使用 `Bootstrap`。
  - **`ChannelInitializer`**: 同样配置 `ChannelPipeline`。
    - `MessageEncoder`：用于将本地 `Message` 编码发送出去。
    - `MessageDecoder`：用于解码远程 `Engine` 返回的消息。
    - `RemoteConnectionHandler`：处理与远程 `Engine` 的消息交互，并将其转发回本地 `Engine`。

#### 7. 消息链路与协作 (`Engine` 核心)

现在，我们将这些组件整合到完整的消息链路中，强调 `Engine` 在其中的核心作用。

- **`Engine` 类 (`com.tenframework.core.engine.Engine.java`)**
  - **C/Python 对齐**：`ten_engine_t` 结构体。
  - **设计原则**：`ten-framework` 的核心调度器和消息处理器。它维护消息队列，负责消息的分发、图调度和生命周期管理。
  - **关键字段**：
    - `Queue<Message> inboundQueue` (或类似 `ManyToOneConcurrentArrayQueue` 的并发队列，用于接收来自 `Connection` 和 `Remote` 的消息)。
    - `GraphManager graphManager` (管理图的节点和连接，负责消息路由到正确的 `Extension` 实例)。
    - `Map<String, Extension> loadedExtensions` (加载的 `Extension` 实例)。
    - `Map<Location, Connection> orphanConnections` (在 `App` 层面统一管理，但 `Engine` 可能需要感知)。
  - **关键方法**：
    - `submitMessage(Message message)`: `App`、`Connection` 或 `Remote` 将外部消息提交给 `Engine` 的入口。消息被放入 `inboundQueue`。
    - `run()`: `Engine` 的主循环，从 `inboundQueue` 轮询消息，并根据消息的 `destLoc` 进行路由。
      - **内部路由**：如果 `destLoc` 指向本地 `Extension`，则将消息派发给相应的 `Extension` 实例进行处理。
      - **外部路由**：如果 `destLoc` 指向远程 `Engine` (`Remote`) 或需要通过特定 `Connection` 发送给外部客户端，则将消息转发给 `App` 或 `Remote` 的 `sendMessage` 方法。
    - `onExtensionMessage(Message message)`: `Extension` 处理完消息后，将结果消息提交回 `Engine`。`Engine` 再根据 `destLoc` 决定是内部路由还是外部发送。

---

#### 完整消息链路 (以外部 CMD 消息为例)

1.  **外部客户端发送 CMD 消息**:
    - 外部客户端通过 Netty 连接到 Java `App` 监听的端口。
    - 发送 MsgPack 编码的 `CMD` 消息字节流。

2.  **`Protocol` 层解码 (Inbound)**:
    - Netty 收到字节流，通过 `MessageDecoder` 将其反序列化为 `CommandMessage` 对象。

3.  **`Connection` 层接收**:
    - `ConnectionHandler` 接收 `CommandMessage`，找到或创建对应的 `Connection` 实例。
    - `Connection.onMessageReceived(commandMessage)` 被调用，将 `commandMessage` 提交到 `App` 的处理队列，或者直接提交到 `Engine` 的 `inboundQueue`。

4.  **`App` 层协调**:
    - `App.handleInboundMessage(commandMessage)` 接收到消息。
    - `App` 将消息通过 `engine.submitMessage(commandMessage)` 提交给内部 `Engine`。

5.  **`Engine` 层处理 (内部流转)**:
    - `Engine` 从 `inboundQueue` 轮询到 `commandMessage`。
    - `Engine` 根据 `commandMessage.getDestLoc()` 决定消息路由。
      - 如果 `destLoc` 指向本地的某个 `Extension` (例如，一个 `command_executor` Extension)，`Engine` 会调用 `Extension` 的处理方法。
      - `Extension` 处理命令，并可能生成新的 `DATA` 或 `EVENT` 消息。

6.  **`Extension` 生成响应消息**:
    - `Extension` 将处理结果封装成一个新的 `Message` (例如 `ResponseMessage` 或 `DataMessage`)。
    - `Extension` 调用 `engine.onExtensionMessage(responseMessage)` 将结果提交回 `Engine`。

7.  **`Engine` 再次路由**:
    - `Engine` 再次从 `inboundQueue` 轮询到 `responseMessage`。
    - 如果 `responseMessage.getDestLoc()` 指向原始的外部客户端 (`srcLoc` 回传)，`Engine` 会将消息转发给 `App` 的 `sendOutboundMessage` 方法。

8.  **`App` 层协调 (Outbound)**:
    - `App.sendOutboundMessage(responseMessage)` 接收到需要发送出去的响应消息。
    - `App` 根据 `responseMessage.getDestLoc()` 找到对应的 `Connection` 实例。
    - `App` 调用 `Connection.sendMessage(responseMessage)`。

9.  **`Connection` 层发送**:
    - `Connection.sendMessage(responseMessage)` 将 `responseMessage` 写入其持有的 Netty `Channel`。

10. **`Protocol` 层编码 (Outbound)**:
    - Netty `ChannelPipeline` 中的 `MessageEncoder` 将 `responseMessage` 序列化为 MsgPack 字节流。

11. **外部客户端接收**:
    - Netty 将字节流发送到网络，外部客户端接收并处理响应。

---

### 我的思考

这种基于 Netty 的 Java 设计方案能够高度对齐 `ten-framework` 的 C/C++ 版本，主要得益于以下几点：

1.  **清晰的职责分离**：
    - `Protocol` 专注于编解码，与 Netty `Decoder`/`Encoder` 天然契合。
    - `Connection` 管理单个网络连接，承载消息进出。
    - `Remote` 抽象远程 `Engine`，处理跨 `Engine` 的消息路由。
    - `App` 作为宿主，整合所有组件，提供统一的生命周期管理。
    - `Engine` 保持其核心调度和路由能力，不直接涉及网络 I/O。

2.  **利用 Netty 的异步和事件驱动特性**：
    - Netty 的 `EventLoopGroup`、`ChannelPipeline` 能够高效处理并发网络连接和事件，与 `ten-framework` 的高性能设计理念相符。
    - 避免了传统 Java BIO 的阻塞问题，更适合实时通信。

3.  **消息模型的一致性**：
    - `Location` 和 `Message` 模型的精确对齐是实现语义一致性的基础。通过 Java 类、 Lombok 和 Jackson 注解，可以方便地实现与 C/C++ 结构体和 JSON/MsgPack 格式的映射。

4.  **可扩展性**：
    - `Protocol` 层可以很容易地扩展支持其他协议（如 HTTP/2、MQTT）。
    - `Extension` 机制在 Java 中可以以接口和插件化的方式实现，方便第三方功能集成。
    - `Remote` 的设计使得 `ten-framework` 应用可以方便地构建分布式系统，不同 `Engine` 实例之间可以无缝通信。

5.  **工程实践考量**：
    - 引入 Lombok 简化 POJO 代码。
    - 使用 Jackson 处理 JSON/MsgPack 序列化/反序列化。
    - 采用并发队列（如 `ConcurrentLinkedQueue` 或更高级的 `ManyToOneConcurrentArrayQueue` 实现）来优化 `Engine` 的消息处理。
    - 日志框架 (如 SLF4J + Logback) 对于调试和监控至关重要。

通过这样的设计，Java 版本将不仅在功能上与 C/Python 版本对齐，而且在架构和性能特性上也能保持高度一致，为后续的 Java 迁移提供坚实的基础。
