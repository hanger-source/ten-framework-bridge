# TEN Framework 核心架构分析与 Java 迁移设计

## 1. 核心组件分析

基于对 `core/` 目录的深入分析，TEN Framework 的核心运行时由以下关键组件构成：

### 1.1 消息系统（Message System）
- **消息类型枚举**：`TEN_MSG_TYPE`
  - `TEN_MSG_TYPE_CMD`: 命令消息
  - `TEN_MSG_TYPE_CMD_RESULT`: 命令结果消息  
  - `TEN_MSG_TYPE_DATA`: 数据消息
  - `TEN_MSG_TYPE_AUDIO_FRAME`: 音频帧消息
  - `TEN_MSG_TYPE_VIDEO_FRAME`: 视频帧消息

- **核心 API**：
  - `ten_msg_clone()`: 消息深拷贝，用于多路分发
  - `ten_msg_clear_and_set_dest()`: 动态设置消息目的地
  - `ten_msg_set_property()` / `ten_msg_peek_property()`: 属性管理

### 1.2 引擎系统（Engine System）
- **单线程事件循环**：基于 `libuv` 的 `ten_runloop`
- **消息队列**：严格 FIFO 的 `in_msgs` 队列保证消息顺序
- **路径表管理**：`ten_path_table_t` 管理命令路径和结果回溯
- **生命周期管理**：管理 Extension 的加载、启动、停止

### 1.3 扩展系统（Extension System）
- **生命周期回调**：
  - `on_configure`, `on_init`, `on_start`, `on_stop`, `on_deinit`
- **消息处理回调**：
  - `on_cmd`, `on_data`, `on_audio_frame`, `on_video_frame`
- **独立线程模型**：每个 Extension 运行在独立的 `libuv` 事件循环中

### 1.4 路径表系统（Path Table System）
- **路径类型**：
  - `TEN_PATH_IN`: 命令流入路径，用于结果回溯
  - `TEN_PATH_OUT`: 命令流出路径，存储结果处理器
- **命令回溯机制**：通过 `cmd_id` 和 `parent_cmd_id` 实现精确回溯
- **流式结果策略**：
  - `TEN_RESULT_RETURN_POLICY_FIRST_ERROR_OR_LAST_OK`: 错误优先策略
  - `TEN_RESULT_RETURN_POLICY_EACH_OK_AND_ERROR`: 流式结果策略

## 2. 命令、数据驱动机制的核心本质

### 2.1 动态图路由
- **运行时路由**：通过 `set_dest()` 实现消息的动态路由
- **图拓扑定义**：通过 `property.json` 定义节点和连接
- **逐跳转发**：Extension 作为智能路由器，决定消息下一跳

### 2.2 异步流式 RPC
- **命令链**：通过 `cmd_id` 和 `parent_cmd_id` 构建命令调用链
- **结果回溯**：利用路径表实现异步结果的精确回溯
- **流式处理**：支持一个命令对应多次连续结果返回

### 2.3 并发安全模型
- **Engine 单线程**：保证消息处理的严格顺序性
- **Extension 隔离**：每个 Extension 独立线程，避免相互阻塞
- **消息克隆**：深拷贝确保多路分发时的数据独立性

## 3. Java 迁移架构设计

### 3.1 核心消息系统 Java 设计

```java
// 使用 sealed interface 定义消息类型层次
public sealed interface Message 
    permits Command, CommandResult, Data, AudioFrame, VideoFrame {
    
    String getName();
    void setName(String name);
    
    Location getSourceLocation();
    Location getDestinationLocation();
    void setDestination(String appUri, String graphId, String extensionName);
    
    Map<String, Object> getProperties();
    void setProperty(String path, Object value);
    Object getProperty(String path);
    
    Message clone();
}

// 命令消息
public record Command(
    UUID cmdId,
    UUID parentCmdId,
    String name,
    Map<String, Object> args,
    Location sourceLocation,
    Location destinationLocation,
    Map<String, Object> properties
) implements Message {
    // 实现具体方法
}

// 命令结果消息
public record CommandResult(
    UUID cmdId,
    String originalCmdName,
    Map<String, Object> result,
    boolean isFinal,
    String error,
    Location sourceLocation,
    Location destinationLocation,
    Map<String, Object> properties
) implements Message {
    // 实现具体方法
}

// 音视频帧消息（使用 Netty ByteBuf 实现零拷贝）
public record AudioFrame(
    String name,
    io.netty.buffer.ByteBuf data,
    long timestamp,
    boolean isEOF,
    int sampleRate,
    int channels,
    AudioFrameDataFormat format,
    Location sourceLocation,
    Location destinationLocation,
    Map<String, Object> properties
) implements Message {
    // 实现具体方法
}
```

### 3.2 Engine 核心调度器设计

```java
public class Engine {
    // 单线程执行器保证消息顺序处理
    private final ExecutorService singleThreadExecutor = 
        Executors.newSingleThreadExecutor();
    
    // 消息队列
    private final BlockingQueue<Message> messageQueue = 
        new LinkedBlockingQueue<>();
    
    // 路径表管理
    private final PathTable pathTable = new PathTable();
    
    // Extension 管理
    private final Map<String, ExtensionContext> extensions = 
        new ConcurrentHashMap<>();
    
    // 图配置
    private GraphConfig graphConfig;
    
    public void start() {
        singleThreadExecutor.submit(this::eventLoop);
    }
    
    private void eventLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Message message = messageQueue.take();
                processMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processMessage(Message message) {
        switch (message) {
            case Command cmd -> handleCommand(cmd);
            case CommandResult result -> handleCommandResult(result);
            case Data data -> handleData(data);
            case AudioFrame frame -> handleAudioFrame(frame);
            case VideoFrame frame -> handleVideoFrame(frame);
        }
    }
    
    // 命令处理逻辑
    private void handleCommand(Command cmd) {
        // 添加入路径
        pathTable.addInPath(cmd);
        
        // 路由到目标 Extension
        ExtensionContext targetExtension = findTargetExtension(cmd);
        if (targetExtension != null) {
            targetExtension.onCommand(cmd);
        }
    }
    
    // 命令结果处理逻辑
    private void handleCommandResult(CommandResult result) {
        // 通过路径表处理结果回溯
        CommandResult processedResult = pathTable.processCommandResult(result);
        if (processedResult != null) {
            // 回溯到原始调用者
            routeCommandResult(processedResult);
        }
    }
}
```

### 3.3 Extension 框架设计

```java
public interface Extension {
    // 生命周期回调
    void onConfigure(ExtensionContext context);
    void onInit(ExtensionContext context);
    void onStart(ExtensionContext context);
    void onStop(ExtensionContext context);
    void onDeinit(ExtensionContext context);
    
    // 消息处理回调
    void onCommand(Command cmd);
    void onData(Data data);
    void onAudioFrame(AudioFrame frame);
    void onVideoFrame(VideoFrame frame);
}

public class ExtensionContext {
    private final Engine engine;
    private final String extensionName;
    private final ExecutorService virtualThreadExecutor;
    
    // 发送消息到 Engine
    public void sendMessage(Message message) {
        engine.enqueueMessage(message);
    }
    
    // 发送命令并注册结果处理器
    public CompletableFuture<CommandResult> sendCommand(Command cmd) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        
        // 在路径表中注册结果处理器
        engine.getPathTable().addOutPath(cmd, future::complete);
        
        // 发送命令
        sendMessage(cmd);
        
        return future;
    }
    
    // 返回命令结果
    public void returnResult(CommandResult result) {
        sendMessage(result);
    }
    
    // 获取虚拟线程执行器用于异步操作
    public ExecutorService getVirtualThreadExecutor() {
        return virtualThreadExecutor;
    }
}
```

### 3.4 路径表系统设计

```java
public class PathTable {
    private final Map<UUID, PathIn> inPaths = new ConcurrentHashMap<>();
    private final Map<UUID, PathOut> outPaths = new ConcurrentHashMap<>();
    
    public void addInPath(Command cmd) {
        PathIn inPath = new PathIn(
            cmd.cmdId(),
            cmd.parentCmdId(),
            cmd.name(),
            cmd.sourceLocation()
        );
        inPaths.put(cmd.cmdId(), inPath);
    }
    
    public void addOutPath(Command cmd, Consumer<CommandResult> resultHandler) {
        PathOut outPath = new PathOut(
            cmd.cmdId(),
            cmd.parentCmdId(),
            cmd.name(),
            cmd.sourceLocation(),
            resultHandler
        );
        outPaths.put(cmd.cmdId(), outPath);
    }
    
    public CommandResult processCommandResult(CommandResult result) {
        PathOut outPath = outPaths.get(result.cmdId());
        if (outPath != null) {
            // 重构结果信息
            CommandResult processedResult = reconstructResult(result, outPath);
            
            // 调用结果处理器
            outPath.resultHandler().accept(processedResult);
            
            // 如果是最终结果，移除路径
            if (result.isFinal()) {
                outPaths.remove(result.cmdId());
            }
            
            return processedResult;
        }
        return null;
    }
    
    private CommandResult reconstructResult(CommandResult result, PathOut path) {
        // 根据路径信息重构结果的目的地和命令ID
        return new CommandResult(
            path.parentCmdId(), // 使用父命令ID进行回溯
            path.cmdName(),
            result.result(),
            result.isFinal(),
            result.error(),
            result.sourceLocation(),
            path.sourceLocation(), // 回溯到原始调用位置
            result.properties()
        );
    }
}
```

## 4. 关键技术决策

### 4.1 并发模型
- **Engine 单线程**：使用 `Executors.newSingleThreadExecutor()` 确保消息顺序处理
- **Extension 虚拟线程**：利用 Java 21 的虚拟线程处理阻塞 I/O 操作
- **异步回调**：使用 `CompletableFuture` 实现异步结果处理

### 4.2 消息传递
- **零拷贝**：使用 Netty `ByteBuf` 处理音视频数据
- **深拷贝**：实现 `Message.clone()` 确保多路分发安全
- **类型安全**：使用 sealed interface 和 record 确保编译时类型检查

### 4.3 网络通信
- **Netty 统一入口**：处理 HTTP/JSON RPC、WebSocket、MsgPack over TCP
- **协议编解码**：实现 MsgPack 的两阶段编解码机制
- **连接管理**：抽象 `Connection` 和 `Protocol` 接口

### 4.4 可扩展性
- **ServiceLoader 机制**：实现 Extension 的动态加载
- **依赖注入**：支持配置的自动绑定和注入
- **模块化设计**：清晰的接口分离和职责划分

## 5. 下一步实施计划

1. **创建 Java 项目结构**：建立 Maven 多模块项目
2. **实现核心消息系统**：定义消息类型和基础 API
3. **实现 Engine 核心**：单线程调度器和事件循环
4. **实现 Extension 框架**：生命周期管理和消息处理
5. **实现路径表系统**：命令回溯和结果处理
6. **集成 Netty 通信层**：网络协议处理
7. **完善测试和文档**：确保代码质量和可维护性

这个设计充分保持了 TEN Framework 原有的命令、数据驱动特性，同时充分利用了 Java 的类型安全、虚拟线程、函数式编程等现代特性，为构建高性能的实时对话引擎奠定了坚实的基础。