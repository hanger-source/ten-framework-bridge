package com.tenframework.core.engine;

import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.extension.ExtensionContext;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.path.PathManager;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.remote.DummyRemote;
import com.tenframework.core.remote.Remote;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.tenframework.core.message.MessageType.*;

/**
 * Engine 是 ten-framework 中 Graph 的运行时实例，负责管理 Extension、处理消息路由和命令执行。
 * 它对应 C 语言中的 `ten_engine_t`。
 */
@Slf4j
public final class Engine implements MessageSubmitter, CommandSubmitter {

    private final String engineId;
    private final Runloop runloop; // 组合 Runloop 实例
    private final ManyToOneConcurrentArrayQueue<Message> inMsgs; // 消息输入队列
    private final boolean hasOwnLoop; // 是否拥有自己的 Runloop
    private final ConcurrentMap<Long, CompletableFuture<Object>> commandFutures; // 用于跟踪命令结果的 Future
    private final List<Connection> orphanConnections; // 存储未被 Remote 认领的 Connection

    private GraphDefinition graphDefinition; // 图的静态定义
    private PathManager pathManager; // 路径管理器，负责图内路由
    private DefaultExtensionMessageDispatcher extensionMessageDispatcher; // 消息分发器
    private ExtensionContext extensionContext; // 扩展上下文
    private App app; // 引用所属的 App 实例
    private final Map<String, Remote> remotes; // 管理 Remote 实例

    public Engine(String engineId, int queueCapacity, boolean hasOwnLoop) {
        this.engineId = engineId;
        this.hasOwnLoop = hasOwnLoop;
        this.inMsgs = new ManyToOneConcurrentArrayQueue<>(queueCapacity);
        this.commandFutures = new ConcurrentHashMap<>();
        this.orphanConnections = Collections.synchronizedList(new ArrayList<>());
        this.remotes = new ConcurrentHashMap<>();

        if (hasOwnLoop) {
            this.runloop = new Runloop("EngineRunloop-" + engineId);
        } else {
            this.runloop = null; // 由 App 提供 Runloop
        }
    }

    /**
     * 初始化 Engine 的运行时环境。
     * @param startGraphCommand 启动图命令，包含图的 JSON 定义。
     * @param appInstance App 实例引用。
     */
    public void initializeEngineRuntime(StartGraphCommand startGraphCommand, App appInstance) {
        log.info("Engine {}: 初始化运行时环境...", engineId);
        this.app = appInstance;
        this.graphDefinition = new GraphDefinition(app.getAppUri(), startGraphCommand.getGraphJsonDefinition());
        this.pathManager = new PathManager(this.graphDefinition);

        // 创建 EngineAsyncExtensionEnv 实例，将其作为 Extension 与 Engine 交互的接口
        Map<String, Object> initialProperties = new HashMap<>(); // 示例属性
        AsyncExtensionEnv engineAsyncExtensionEnv = new EngineAsyncExtensionEnv(
                this.graphId, // ExtensionName 暂时使用 graphId，后续可根据具体 Extension 调整
                this.graphId,
                this.app.getAppUri(),
                this, // MessageSubmitter
                this, // CommandSubmitter
                initialProperties
        );
        this.extensionContext = new ExtensionContext(this, graphDefinition, pathManager, engineAsyncExtensionEnv);
        this.extensionMessageDispatcher = new DefaultExtensionMessageDispatcher(extensionContext, commandFutures);

        // 加载 Extension
        graphDefinition.getExtensionsInfo().forEach(extensionInfo -> {
            log.info("Engine {}: 加载 Extension: {}", engineId, extensionInfo.getExtensionName());
            extensionContext.loadExtension(extensionInfo);
        });

        // 临时处理 StartGraphCommand 的长运行模式
        if (startGraphCommand.isLongRunningMode()) {
            log.warn("Engine {}: 收到长运行模式的 StartGraphCommand，此功能暂未完全实现。", engineId);
        }
        isReadyToHandleMsg = true;
        log.info("Engine {}: 运行时环境初始化完成。", engineId);
    }

    /**
     * 启动 Engine 的消息处理循环。
     * 如果 Engine 有自己的 Runloop，则启动它并注册消息队列。
     */
    public void start() {
        if (hasOwnLoop && runloop != null) {
            log.info("Engine {}: 启动专属 Runloop。", engineId);
            // 注册内部消息队列，EngineAgent 会从 inMsgs 中排水
            runloop.registerExternalEventSource(inMsgs::drain, () -> {}); // 第二个参数是 notifier，暂时为空 lambda
            runloop.start(); // 启动 Runloop 线程
        } else if (app != null) {
            log.info("Engine {}: 使用 App 的 Runloop 进行消息处理注册。", engineId);
            // 将 Engine 的消息队列注册到 App 的 Runloop，让 App 负责调度处理
            app.getRunloop().ifPresent(appRunloop -> {
                appRunloop.registerExternalEventSource(inMsgs::drain, () -> {});
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
            runloop.stop();
        } else if (app != null) {
            // 如果使用 App 的 Runloop，则从 App 的 Runloop 中注销
            app.getRunloop().ifPresent(appRunloop -> {
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
     * 处理 Engine 接收到的消息。
     * 这是 Engine Runloop 线程中的核心处理逻辑。
     * @param message
     */
    private void processMessage(Message message) {
        log.debug("Engine {}: 处理消息: ID={}, Type={}, SrcLoc={}",
                engineId, message.getId(), message.getType(), message.getSrcLoc());

        // 统一通过 extensionMessageDispatcher 处理命令和数据消息
        switch (message.getType()) {
            case CMD_START_GRAPH:
            case CMD_STOP_GRAPH:
            case CMD_CLOSE_APP:
            case CMD_TIMER:
            case CMD_TIMEOUT:
            case CMD_ADD_EXTENSION_TO_GRAPH:
            case CMD_REMOVE_EXTENSION_FROM_GRAPH:
                extensionMessageDispatcher.dispatchMessage(message);
                break;
            case DATA_MESSAGE:
            case AUDIO_FRAME_MESSAGE:
            case VIDEO_FRAME_MESSAGE:
                // 对于数据和音视频帧消息，优先检查是否需要外部路由
                boolean isRoutedExternally = false;
                if (message.getDestLocs() != null && app != null) {
                    for (Location destLoc : message.getDestLocs()) {
                        // 如果目的地不是当前 Engine，则通过 App 进行外部路由
                        if (destLoc.getGraphId() == null || !destLoc.getGraphId().equals(this.graphId)) {
                            app.sendMessageToLocation(message, destLoc);
                            isRoutedExternally = true;
                            log.debug("Engine {}: 消息 {} 已路由到外部目的地: {}", engineId, message.getId(), destLoc);
                        }
                    }
                }
                if (!isRoutedExternally) {
                    // 如果没有外部路由，则在 Engine 内部派发给 Extension
                    extensionMessageDispatcher.dispatchMessage(message);
                } else {
                    log.debug("Engine {}: 消息 {} 已路由到外部，跳过内部 Extension 派发。", engineId, message.getId());
                }
                break;
            case CMD_RESULT:
                processCommandResult((CommandResult) message);
                break;
            case INVALID:
            default:
                log.warn("Engine {}: 收到未知或无效消息类型: {}", engineId, message.getType());
                break;
        }
    }

    private void processCommandResult(CommandResult commandResult) {
        log.debug("Engine {}: 处理命令结果: OriginalCommandId={}, StatusCode={}, Detail={}",
                engineId, commandResult.getOriginalCommandId(), commandResult.getStatusCode(), commandResult.getDetail());

        long originalCommandId = Long.parseLong(commandResult.getOriginalCommandId());
        CompletableFuture<Object> future = commandFutures.remove(originalCommandId);
        if (future != null) {
            // 根据 CommandResult 的状态，完成或异常完成 Future
            if (commandResult.getStatusCode() == 0) { // 假设 0 为成功
                future.complete(commandResult);
            } else {
                future.completeExceptionally(new RuntimeException("Command failed with status: " + commandResult.getStatusCode() + ", Detail: " + commandResult.getDetail()));
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
     * @param connection 要添加的 Connection。
     */
    public void addOrphanConnection(Connection connection) {
        orphanConnections.add(connection);
        log.info("Engine {}: Connection {} 已添加为孤立连接。", engineId, connection.getConnectionId());
    }

    /**
     * 根据 ID 查找孤立连接。
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
     * @param targetAppUri 目标 App 的 URI。
     * @param targetGraphId 目标 Graph 的 ID。
     * @param initialConnection 可选的初始 Connection，如果创建新的 Remote 且需要关联。
     * @return 对应的 Remote 实例。
     */
    public Optional<Remote> getOrCreateRemote(String targetAppUri, String targetGraphId, Optional<Connection> initialConnection) {
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
                        log.info("Engine {}: 现有 Remote {} 关联了新的 Connection {}", engineId, remoteKey, conn.getConnectionId());
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

    public String getEngineId() {
        return engineId;
    }

    public String getGraphId() {
        return graphDefinition != null ? graphDefinition.getGraphId() : null;
    }

    public Optional<Runloop> getRunloop() {
        return Optional.ofNullable(runloop);
    }

    // 标识 Engine 是否准备好处理消息
    private volatile boolean isReadyToHandleMsg = false;
}