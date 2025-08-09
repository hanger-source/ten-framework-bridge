package com.tenframework.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.app.App;
import com.tenframework.core.command.EngineCommandHandler;
import com.tenframework.core.command.engine.TimeoutCommandHandler;
import com.tenframework.core.command.engine.TimerCommandHandler;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.extension.ExtensionContext;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.path.PathTable;
import com.tenframework.core.remote.DummyRemote;
import com.tenframework.core.remote.Remote;
import com.tenframework.core.runloop.Runloop;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import static com.tenframework.core.message.MessageType.CMD_TIMEOUT;
import static com.tenframework.core.message.MessageType.CMD_TIMER;

/**
 * `Engine` 类代表 Ten 框架中一个独立的执行单元，负责管理和运行一个 Graph。
 * 它对应 C 语言中的 `ten_engine_t` 结构体。
 */
@Slf4j
@Getter
public class Engine implements Agent, MessageSubmitter, CommandSubmitter {

    private final String engineId; // 对应 graph_id
    private final GraphDefinition graphDefinition; // 引擎所加载的 Graph 的定义
    private final Runloop runloop; // 引擎自身的运行循环
    private final PathTable pathTable; // 消息路由表
    private final ExtensionContext extensionContext; // 扩展上下文管理器
    private final ExtensionMessageDispatcher messageDispatcher; // 消息派发器
    private final ConcurrentMap<Long, CompletableFuture<Object>> commandFutures; // 用于跟踪命令结果的 CompletableFuture
    private final Map<MessageType, EngineCommandHandler> commandHandlers; // 新增命令处理器映射
    private final ManyToOneConcurrentArrayQueue<Message> inMsgs; // 消息输入队列
    private final boolean hasOwnLoop; // 是否拥有自己的 Runloop
    private final List<Connection> orphanConnections; // 存储未被 Remote 认领的 Connection
    private final Map<String, Remote> remotes; // 管理 Remote 实例
    private final App app; // 引用所属的 App 实例
    private volatile boolean isReadyToHandleMsg = false;

    private volatile boolean isClosing = false;

    public Engine(String engineId, GraphDefinition graphDefinition, Runloop appRunloop, App app) {
        this.engineId = engineId;
        this.graphDefinition = graphDefinition;
        this.app = app;
        runloop = new Runloop(engineId + "-runloop"); // 每个 Engine 都有自己的 Runloop
        pathTable = new PathTable(graphDefinition); // PathTable 依赖 GraphDefinition

        // 创建 EngineAsyncExtensionEnv 实例，将其作为 Extension 与 Engine 交互的接口
        Map<String, Object> initialProperties = new HashMap<>(); // 示例属性
        AsyncExtensionEnv engineAsyncExtensionEnv = new EngineAsyncExtensionEnv(
                graphDefinition.getGraphId(), // ExtensionName 暂时使用 graphId，后续可根据具体 Extension 调整
                graphDefinition.getGraphId(),
                app.getAppUri(),
                this, // MessageSubmitter
                this, // CommandSubmitter
                initialProperties);

        extensionContext = new ExtensionContext(this, app, pathTable, engineAsyncExtensionEnv); // ExtensionContext
        // PathTable
        commandFutures = new ConcurrentHashMap<>();
        messageDispatcher = new DefaultExtensionMessageDispatcher(extensionContext, commandFutures); // 消息派发器依赖
        // ExtensionContext
        commandHandlers = new HashMap<>(); // 初始化命令处理器映射
        registerCommandHandlers(); // 注册命令处理器

        inMsgs = new ManyToOneConcurrentArrayQueue<>(1024); // 恢复 inMsgs 初始化
        hasOwnLoop = true; // 假设 Engine 默认有自己的 Runloop
        orphanConnections = Collections.synchronizedList(new ArrayList<>()); // 恢复 orphanConnections 初始化
        remotes = new ConcurrentHashMap<>(); // 恢复 remotes 初始化
        isReadyToHandleMsg = true; // 恢复 isReadyToHandleMsg 初始化

        log.info("Engine {}: 已创建，关联 Graph: {}", engineId, graphDefinition.getName());
    }

    private void registerCommandHandlers() {
        // 注册所有 Engine 级别的命令处理器
        // 移除 CMD_ADD_EXTENSION_TO_GRAPH 和 CMD_REMOVE_EXTENSION_FROM_GRAPH
        commandHandlers.put(CMD_TIMER, new TimerCommandHandler()); // 使用新的 TimerCommandHandler 实例
        commandHandlers.put(CMD_TIMEOUT, new TimeoutCommandHandler()); // 使用新的 TimeoutCommandHandler 实例
    }

    @Override
    public int doWork() throws Exception {
        // 从输入队列中排水并处理消息
        return inMsgs.drain(this::processMessage);
    }

    @Override
    public String roleName() {
        return "Engine-" + engineId;
    }

    /**
     * 启动 Engine 的消息处理循环。
     * 如果 Engine 有自己的 Runloop，则启动它并注册消息队列。
     */
    public void start() {
        if (hasOwnLoop && runloop != null) {
            log.info("Engine {}: 启动专属 Runloop。", engineId);
            // 注册 Engine 自身作为 Agent 到 Runloop
            runloop.addAgent(this);
            runloop.start(); // 启动 Runloop 线程
        } else if (app != null) { // Fallback if no dedicated loop
            log.info("Engine {}: 使用 App 的 Runloop 进行消息处理注册。", engineId);
            // 将 Engine 的 doWork 方法注册到 App 的 Runloop，让 App 负责调度处理
            app.getAppRunloop().ifPresent(appRunloop -> {
                // 这里如果 App Runloop 也支持 addAgent，则更优
                // 暂时通过注册 inMsgs 的 drain 方法来处理
                appRunloop.registerExternalEventSource(inMsgs::drain, () -> {
                });
            });
        } else {
            log.warn("Engine {}: 无法启动，既没有专属 Runloop，也没有关联 App 的 Runloop。", engineId);
        }
    }

    /**
     * 停止 Engine，关闭 Runloop 并清理资源。
     */
    public void stop() {
        log.info("Engine {}: 正在停止...", engineId);
        isReadyToHandleMsg = false;
        if (runloop != null) {
            runloop.shutdown();
        } else if (app != null) {
            // 如果使用 App 的 Runloop，则从 App 的 Runloop 中注销
            app.getAppRunloop().ifPresent(appRunloop -> {
                // Agrona 没有直接的 unregister 方法，这里简化处理，实际可能需要更复杂的机制
                log.warn("Engine {}: 无法从 App 的 Runloop 注销消息源，需要手动管理资源。", engineId);
            });
        }
        if (extensionContext != null) {
            extensionContext.cleanup();
        }
        orphanConnections.clear(); // 清理孤立连接

        // 确保每个 Remote 都被关闭
        remotes.values().forEach(Remote::shutdown);
        remotes.clear(); // 清理 Remote 实例

        commandFutures.forEach((cmdId, future) -> future.cancel(true)); // 取消所有未完成的命令
        commandFutures.clear();

        log.info("Engine {}: 已停止并清理资源。", engineId);
    }

    @Override
    public void submitMessage(Message message) {
        if (!isReadyToHandleMsg) {
            log.warn("Engine {}: 引擎未准备好处理消息，消息 {} 被丢弃。", engineId, message.getId());
            return;
        }
        if (!inMsgs.offer(message)) {
            log.warn("Engine {}: 内部消息队列已满，消息 {} 被丢弃。", engineId, message.getId());
        }
    }

    @Override
    public CompletableFuture<Object> submitCommand(Command command) {
        if (!isReadyToHandleMsg) {
            log.warn("Engine {}: 引擎未准备好处理命令，命令 {} 被丢弃。", engineId, command.getId());
            return CompletableFuture.completedFuture(new RuntimeException("Engine not ready."));
        }
        long commandId = Long.parseLong(command.getId()); // 假设 Command ID 是 long 字符串
        CompletableFuture<Object> future = new CompletableFuture<>();
        commandFutures.put(commandId, future);
        if (!inMsgs.offer(command)) {
            log.warn("Engine {}: 内部命令队列已满，命令 {} 被丢弃。", engineId, command.getId());
            future.completeExceptionally(new RuntimeException("Engine command queue full."));
            commandFutures.remove(commandId);
        }
        return future;
    }

    @Override
    public void submitCommandResult(CommandResult commandResult) {
        if (!isReadyToHandleMsg) {
            log.warn("Engine {}: 引擎未准备好处理命令结果，结果 {} 被丢弃。", engineId, commandResult.getId());
            return;
        }
        if (!inMsgs.offer(commandResult)) {
            log.warn("Engine {}: 内部命令结果队列已满，结果 {} 被丢弃。", engineId, commandResult.getId());
        }
    }

    /**
     * 处理传入的消息。
     * 这是 Engine Runloop 线程中的核心处理逻辑。
     *
     * @param message 传入的消息。
     */
    @Override
    public void processMessage(Message message) {
        String msgId = message.getId();
        MessageType msgType = message.getType();

        if (isClosing && !isMessageAllowedWhenClosing(message)) {
            log.warn("Engine {}: 引擎正在关闭，消息 {} (Type: {}) 被丢弃。", engineId, msgId, msgType);
            // 对于命令，如果引擎正在关闭，可能需要返回错误结果
            if (message instanceof Command) {
                Command command = (Command) message;
                CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                if (future != null) {
                    future.completeExceptionally(new IllegalStateException("Engine is closing."));
                }
            }
            return;
        }

        log.debug("Engine {}: 处理消息: ID={}, Type={}, SrcLoc={}", engineId, msgId, msgType, message.getSrcLoc());

        // 1. 如果是命令，首先尝试通过注册的命令处理器处理
        if (message instanceof Command) {
            Command command = (Command) message;
            EngineCommandHandler handler = commandHandlers.get(command.getType());
            if (handler != null) {
                try {
                    Object result = handler.handle(this, command);
                    // 处理命令结果
                    CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                    if (future != null) {
                        future.complete(result);
                    }
                } catch (Exception e) {
                    log.error("Engine {}: 命令处理器处理命令 {} 失败: {}", engineId, command.getId(), e.getMessage(), e);
                    CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                    if (future != null) {
                        future.completeExceptionally(e);
                    }
                }
                return; // 命令已被处理，不再继续路由到 Extension
            } else {
                log.warn("Engine {}: 未知命令类型或没有注册处理器: {}", engineId, msgType);
                // 如果是命令但没有找到处理器，返回失败结果
                CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                if (future != null) {
                    future.completeExceptionally(new UnsupportedOperationException("未知命令类型或没有注册处理器: " + msgType));
                }
                return;
            }
        }

        // 2. 对于非命令消息，委托给 messageDispatcher 路由到 Extension
        messageDispatcher.dispatchMessage(message);
    }

    /**
     * 判断在 Engine 关闭过程中，某些特定消息是否仍然需要处理。
     * 这主要用于确保关闭命令本身能够被处理，而不是立即被丢弃。
     *
     * @param message 要检查的消息。
     * @return 如果消息在关闭过程中仍然需要处理，则返回 true；否则返回 false。
     */
    private boolean isMessageAllowedWhenClosing(Message message) {
        // 允许处理与关闭相关的命令，例如 CMD_CLOSE_APP (虽然通常由 App 处理，但 Engine 可能需要响应)
        // 或其他确保 Engine 正常关闭的内部命令。
        // 这里暂时只允许 CommandResult，因为 Engine 可能会收到自身发出的命令的结果。
        return message instanceof CommandResult;
    }

    /**
     * 处理命令结果消息。
     *
     * @param commandResult 要处理的命令结果消息。
     */
    private void processCommandResult(CommandResult commandResult) {
        log.debug("Engine {}: 处理命令结果: OriginalCommandId={}, StatusCode={}, Detail={}",
                engineId, commandResult.getOriginalCommandId(), commandResult.getStatusCode(),
                commandResult.getDetail());

        long originalCommandId = Long.parseLong(commandResult.getOriginalCommandId());
        CompletableFuture<Object> future = commandFutures.remove(originalCommandId);
        if (future != null) {
            // 根据 CommandResult 的状态，完成或异常完成 Future
            if (commandResult.getStatusCode() == 0) { // 假设 0 为成功
                future.complete(commandResult);
            } else {
                future.completeExceptionally(new RuntimeException("Command failed with status: "
                        + commandResult.getStatusCode() + ", Detail: " + commandResult.getDetail()));
            }
        } else {
            log.warn("Engine {}: 未找到与命令结果 {} 对应的 Future。", engineId, commandResult.getOriginalCommandId());
        }

        // 如果命令结果有返回地址，则通过 App 回传
        if (app != null && commandResult.getDestLocs() != null && !commandResult.getDestLocs().isEmpty()) {
            // CommandResult 通常只有一个返回 Location，这里取第一个
            Location returnLocation = commandResult.getDestLocs().get(0);
            if (returnLocation != null) {
                app.sendMessageToLocation(commandResult, returnLocation);
                log.debug("Engine {}: 命令结果 {} 已回传到 Location: {}", engineId, commandResult.getId(), returnLocation);
            }
        }
    }

    /**
     * 将 Connection 添加到孤立连接列表。
     * 孤立连接是指已经迁移到 Engine 但尚未绑定到具体 Remote 的连接。
     *
     * @param connection 要添加的 Connection。
     */
    public void addOrphanConnection(Connection connection) {
        orphanConnections.add(connection);
        log.info("Engine {}: Connection {} 已添加为孤立连接。", engineId, connection.getConnectionId());
    }

    /**
     * 根据 ID 查找孤立连接。
     *
     * @param connId 连接 ID。
     * @return 匹配的 Connection，如果不存在则返回 Optional.empty()。
     */
    public Optional<Connection> findOrphanConnectionById(String connId) {
        return orphanConnections.stream()
                .filter(conn -> conn.getConnectionId().equals(connId))
                .findFirst();
    }

    /**
     * 获取或创建 Remote 实例。
     *
     * @param targetAppUri      目标 App 的 URI。
     * @param targetGraphId     目标 Graph 的 ID。
     * @param initialConnection 可选的初始 Connection，如果创建新的 Remote 且需要关联。
     * @return 对应的 Remote 实例。
     */
    public Optional<Remote> getOrCreateRemote(String targetAppUri, String targetGraphId,
            Optional<Connection> initialConnection) {
        // 使用 AppUri 和 GraphId 组合作为 Remote 的唯一 Key
        String remoteKey = targetAppUri + "/" + targetGraphId;

        // 尝试获取现有 Remote
        Remote existingRemote = remotes.get(remoteKey);
        if (existingRemote != null) {
            // 如果存在，并且提供了 initialConnection 且现有 Remote 没有关联 Connection，则关联
            initialConnection.ifPresent(conn -> {
                // 这里需要根据实际 Remote 实现来判断是否可以设置关联 Connection
                // 对于 DummyRemote，我们可以直接设置
                if (existingRemote instanceof DummyRemote) {
                    DummyRemote dummyRemote = (DummyRemote) existingRemote;
                    if (!dummyRemote.getAssociatedConnection().isPresent()) {
                        dummyRemote.setAssociatedConnection(conn);
                        log.info("Engine {}: 现有 Remote {} 关联了新的 Connection {}", engineId, remoteKey,
                                conn.getConnectionId());
                    }
                }
            });
            return Optional.of(existingRemote);
        }

        // 如果不存在，则创建新的 DummyRemote
        Location remoteLocation = new Location().setAppUri(targetAppUri).setGraphId(targetGraphId);
        DummyRemote newRemote = new DummyRemote(remoteKey, remoteLocation, this, initialConnection);
        newRemote.activate(); // 激活 Remote
        remotes.put(remoteKey, newRemote); // 添加到管理 Map 中
        log.info("Engine {}: 创建并激活新的 Remote: {}", engineId, remoteKey);
        return Optional.of(newRemote);
    }
}