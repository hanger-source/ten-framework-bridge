package com.tenframework.core.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.tenframework.core.command.AddExtensionToGraphCommandHandler;
import com.tenframework.core.command.InternalCommandHandler;
import com.tenframework.core.command.InternalCommandType;
import com.tenframework.core.command.RemoveExtensionFromGraphCommandHandler;
import com.tenframework.core.command.StartGraphCommandHandler;
import com.tenframework.core.command.StopGraphCommandHandler;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphInstances;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.path.PathManager;
import com.tenframework.core.path.PathOut;
import com.tenframework.core.route.RouteManager;
import com.tenframework.core.util.ClientLocationUriUtils;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.SleepingIdleStrategy;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * TEN框架的核心Engine实现
 * 基于单线程事件循环模型，使用Agrona队列实现高性能消息处理
 * 对应C语言中的ten_engine_t结构
 */
@Slf4j
public final class Engine implements MessageSubmitter, CommandSubmitter { // 实现MessageSubmitter和CommandSubmitter

    /**
     * 队列容量，默认64K条消息
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 65536;
    /**
     * Engine的唯一标识符
     */
    private final String engineId;
    /**
     * Engine当前状态
     */
    private final AtomicReference<EngineState> state = new AtomicReference<>(EngineState.CREATED);
    /**
     * 引用计数，用于资源管理
     */
    private final AtomicLong referenceCount = new AtomicLong(1);
    /**
     * Engine是否正在运行
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 入站消息队列 - 使用Agrona ManyToOneConcurrentArrayQueue
     * 多生产者（Netty线程、Extension虚拟线程）单消费者（Engine核心线程）模式
     */
    private final ManyToOneConcurrentArrayQueue<Message> inboundMessageQueue;
    /**
     * 路径表，管理命令的生命周期和结果回溯
     */
    private final PathManager pathManager;
    /**
     * Engine核心处理线程 - 单线程ExecutorService
     */
    private final ExecutorService engineThread;
    /**
     * Agrona空闲策略，用于优化消息循环的CPU使用
     */
    private final IdleStrategy idleStrategy;
    /**
     * 维护graphId到GraphInstance的映射
     */
    private GraphInstances graphInstances;
    /**
     * 管理活动的Netty Channel，用于消息回传
     */
    private final Map<String, Channel> channelMap;
    /**
     * 维护channelId到相关联的Command ID集合的映射，用于断开连接时清理PathOut
     */
    private final RouteManager routeManager;

    private final Map<String, InternalCommandHandler> internalCommandHandlers;
    /**
     * 维护命令ID到CompletableFuture的映射，用于异步返回命令结果给调用方
     */
    private final ConcurrentMap<Long, CompletableFuture<Object>> commandFutures = new ConcurrentHashMap<>();
    private volatile Thread coreThread;

    /**
     * 构造函数
     *
     * @param engineId Engine唯一标识符
     */
    public Engine(String engineId) {
        this(engineId, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 构造函数
     *
     * @param engineId      Engine唯一标识符
     * @param queueCapacity 队列容量
     */
    public Engine(String engineId, int queueCapacity) {
        this.engineId = engineId;

        // 创建Agrona队列，必须是2的幂
        int capacity = Integer.highestOneBit(queueCapacity);
        if (capacity < queueCapacity) {
            capacity <<= 1;
        }
        inboundMessageQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
        idleStrategy = new SleepingIdleStrategy(1_000_000L); // 1ms sleep time

        channelMap = new ConcurrentHashMap<>();
        graphInstances = new GraphInstances();

        // 初始化PathManager
        pathManager = new PathManager(this);

        // 初始化RouteManager
        routeManager = new RouteManager(graphInstances);

        // 初始化内部命令处理器
        internalCommandHandlers = new HashMap<>();
        internalCommandHandlers.put(InternalCommandType.START_GRAPH.getCommandName(), new StartGraphCommandHandler());
        internalCommandHandlers.put(InternalCommandType.STOP_GRAPH.getCommandName(), new StopGraphCommandHandler());
        internalCommandHandlers.put(InternalCommandType.ADD_EXTENSION_TO_GRAPH.getCommandName(),
                new AddExtensionToGraphCommandHandler());
        internalCommandHandlers.put(InternalCommandType.REMOVE_EXTENSION_FROM_GRAPH.getCommandName(),
                new RemoveExtensionFromGraphCommandHandler());

        // 创建单线程ExecutorService，使用自定义ThreadFactory
        engineThread = newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TEN-Engine-" + engineId);
            t.setDaemon(false); // 非守护线程
            t.setUncaughtExceptionHandler((_, ex) -> {
                log.error("Engine核心线程发生未捕获异常", ex);
                state.set(EngineState.ERROR);
            });
            return t;
        });

        log.info("Engine已创建: engineId={}, queueCapacity={}", engineId, capacity);
    }

    /**
     * 启动Engine
     */
    public void start() {
        if (!state.compareAndSet(EngineState.CREATED, EngineState.STARTING)) {
            log.warn("Engine已经启动或正在启动: engineId={}, currentState={}",
                    engineId, state.get());
            return;
        }

        log.info("正在启动Engine: engineId={}", engineId);

        // 提交核心处理任务到单线程ExecutorService
        engineThread.submit(() -> {
            // 记录核心线程引用
            coreThread = Thread.currentThread();
            log.info("Engine核心线程已启动: engineId={}, threadName={}",
                    engineId, coreThread.getName());

            // 标记为运行状态
            running.set(true);
            state.set(EngineState.RUNNING);

            try {
                // 核心消息处理循环
                runMessageLoop();
            } catch (Exception e) {
                log.error("Engine核心消息循环异常: engineId={}", engineId, e);
                state.set(EngineState.ERROR);
            } finally {
                running.set(false);
                log.info("Engine核心线程已停止: engineId={}", engineId);
            }
        });

        // 等待Engine启动完成
        while (state.get() == EngineState.STARTING) {
            Thread.yield();
        }

        log.info("Engine启动完成: engineId={}, state={}", engineId, state.get());
    }

    /**
     * 停止Engine
     */
    public void stop() {
        if (!state.compareAndSet(EngineState.RUNNING, EngineState.STOPPING)) {
            log.warn("Engine未在运行状态，无法停止: engineId={}, currentState={}",
                    engineId, state.get());
            return;
        }

        log.info("正在停止Engine: engineId={}", engineId);

        // 停止核心处理循环
        running.set(false);

        // 关闭线程池
        engineThread.shutdown();

        try {
            // 等待Engine核心线程池终止，设置超时时间，例如5秒
            if (!engineThread.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Engine核心线程池未能在规定时间内终止，尝试强制关闭: engineId={}", engineId);
                engineThread.shutdownNow(); // 尝试强制关闭
                if (!engineThread.awaitTermination(1, TimeUnit.SECONDS)) {
                    log.error("Engine核心线程池未能强制终止: engineId={}", engineId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待Engine核心线程池终止时被中断: engineId={}", engineId, e);
            engineThread.shutdownNow(); // 收到中断信号，尝试立即关闭
        }

        state.set(EngineState.STOPPED);
        log.info("Engine已停止: engineId={}", engineId);
    }

    /**
     * 向Engine提交消息（非阻塞）
     * 由Netty线程或Extension虚拟线程调用
     *
     * @param message 要处理的消息
     * @return true如果成功提交，false如果队列已满
     */
    @Override
    public boolean submitMessage(Message message) {
        if (state.get() != EngineState.RUNNING) {
            log.warn("Engine未在运行状态，拒绝消息: engineId={}, messageType={}",
                engineId, message.getType());
            return false;
        }

        if (message.getType() == MessageType.COMMAND && !InternalCommandType.isInternal(message.getName())) {
            Command command = (Command)message;
            command.setCommandId(Command.generateCommandId());
            CompletableFuture<Object> future = new CompletableFuture<>();
            commandFutures.put(command.getCommandId(), future);
        }

        // 使用Agrona队列的非阻塞offer方法
        boolean success = inboundMessageQueue.offer(message);

        if (!success) {
            // 队列满了，根据消息类型决定处理策略
            handleQueueFullback(message);
        }
        return true;
    }

    /**
     * 处理队列满的情况
     * 根据思考文档中的回压策略
     */
    private void handleQueueFullback(Message message) {
        MessageType type = message.getType();

        if (type == MessageType.DATA || type == MessageType.AUDIO_FRAME || type == MessageType.VIDEO_FRAME) {
            // 数据类消息：直接丢弃并记录警告
            log.warn("队列已满，丢弃数据消息: engineId={}, messageType={}, messageName={}",
                    engineId, type, message.getName());
        } else if (type == MessageType.COMMAND) {
            // 命令消息：记录错误，后续可以返回错误给调用方
            log.error("队列已满，命令消息被拒绝: engineId={}, messageType={}, messageName={}",
                    engineId, type, message.getName());
            // TODO: 生成CommandResult错误并回溯给上游

            if (message instanceof Command command) {
                CommandResult errorResult = CommandResult.error(command.getCommandId(),
                        "Engine队列已满，命令被拒绝");

                // 尝试找到对应的PathOut并回溯错误
                pathManager.getOutPath(command.getCommandId()).ifPresent(pathOut -> {
                    try {
                        pathManager.handleResultReturnPolicy(pathOut, errorResult);
                    } catch (CloneNotSupportedException ex) {
                        log.error("回溯队列满错误时克隆CommandResult失败: commandId={}", command.getCommandId(), ex);
                    }
                });
            }
        } else {
            log.warn("队列已满，消息被拒绝: engineId={}, messageType={}, messageName={}",
                    engineId, type, message.getName());
        }
    }

    /**
     * Engine核心消息处理循环
     * 在单线程中运行，保证消息的严格FIFO顺序处理
     *
     * 使用Agrona优化的消息处理循环：
     * - 使用drain方法批量处理消息以提高吞吐量
     * - 使用IdleStrategy优化空闲时的CPU使用
     * - 支持优雅关闭
     */
    private void runMessageLoop() {
        log.info("Engine核心消息循环开始: engineId={}", engineId);

        while (running.get()) {
            try {
                // 使用Agrona的drain方法批量处理消息
                int processedCount = inboundMessageQueue.drain(this::processMessage);

                if (processedCount > 0) {
                    // 处理了消息，重置空闲策略
                    idleStrategy.reset();
                } else {
                    // 没有消息，执行空闲策略
                    idleStrategy.idle();
                }

            } catch (Exception e) {
                log.error("处理消息时发生异常: engineId={}", engineId, e);
                // 继续处理下一个消息，不中断循环
                // 重置空闲策略，避免异常影响性能
                idleStrategy.reset();
            }
        }

        log.info("Engine核心消息循环结束: engineId={}", engineId);
    }

    /**
     * 处理单个消息
     * 根据消息类型和目标位置进行分发
     *
     * @param message 要处理的消息
     */
    private void processMessage(Message message) {
        // 增加日志以跟踪消息进入Engine的processMessage方法
        log.info("Engine收到消息进行处理: engineId={}, messageType={}, messageName={}, sourceLocation={}",
                engineId, message.getType(), message.getName(), message.getSourceLocation());

        try {
            // 1. 检查消息完整性
            if (!message.checkIntegrity()) {
                log.warn("消息完整性检查失败: engineId={}, messageType={}, messageName={}",
                        engineId, message.getType(), message.getName());
                return;
            }

            // 2. 根据消息类型进行分类处理
            switch (message.getType()) {
                case COMMAND -> processCommand(message);
                case COMMAND_RESULT -> processCommandResult(message);
                case DATA, AUDIO_FRAME, VIDEO_FRAME -> processData(message);
                default -> {
                    log.warn("未知消息类型: engineId={}, messageType={}, messageName={}",
                            engineId, message.getType(), message.getName());
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("消息处理完成: engineId={}, messageType={}, messageName={}",
                        engineId, message.getType(), message.getName());
            }

        } catch (Exception e) {
            log.error("消息处理异常: engineId={}, messageType={}, messageName={}",
                    engineId, message.getType(), message.getName(), e);
        }
    }

    private String fetchGraphId(Message message) {
        String graphId = null;
        // 优先从客户端LocationUri中获取GraphId
        String clientLocationUri = message.getPropertyAsString(PROPERTY_CLIENT_LOCATION_URI);
        if (clientLocationUri != null) {
            graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
        }
        // 内部消息传递过程中 不存在客户端LocationUri，则从源LocationGraphId中获取
        if (message.getSourceLocation() != null) {
            graphId = message.getSourceLocation().graphId();
        }
        return graphId;
    }

    /**
     * 处理命令消息
     *
     * @param message 命令消息
     */
    private void processCommand(Message message) {
        if (!(message instanceof Command command)) {
            log.warn("收到非Command类型的消息在processCommand中: engineId={}, messageType={}",
                    engineId, message.getType());
            return;
        }

        log.debug("处理命令消息: engineId={}, commandName={}, commandId={}",
                engineId, command.getName(), command.getCommandId());

        InternalCommandHandler handler = internalCommandHandlers.get(command.getName());
        if (handler != null) {
            handler.handle(command, this);
            return;
        }

        // 1. 获取消息的源GraphId
        String sourceGraphId = fetchGraphId(message);
        if (sourceGraphId == null) {
            log.warn("命令消息缺少有效的源GraphId，无法进行路由: engineId={}, commandName={}, commandId={}",
                    engineId, command.getName(), command.getCommandId());
            // 如果是sendCmd外部调用，且没有sourceLocation，可能需要completeExceptionally对应的Future
            // 但通常从sendCmd发出的命令会带sourceLocation
            CompletableFuture<Object> future = commandFutures.remove(command.getCommandId()); // 清理Future
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException("命令消息缺少有效源GraphId"));
            }
            return;
        }

        // 2. 查找源GraphInstance
        GraphInstance graphInstance = graphInstances.getByGraphId(sourceGraphId);
        if (graphInstance == null) {
            log.warn("源GraphInstance不存在或未加载: engineId={}, graphId={}, commandName={}, commandId={}",
                    engineId, sourceGraphId, command.getName(), command.getCommandId());
            CompletableFuture<Object> future = commandFutures.remove(command.getCommandId()); // 清理Future
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException("源GraphInstance不存在或未加载"));
            }
            return;
        }

        // 3. 解析消息的实际目的地列表，委托给RouteManager
        List<Location> targetLocations = routeManager.resolveMessageDestinations(command);

        if (targetLocations.isEmpty()) {
            log.warn("命令消息没有解析到任何目标Extension: engineId={}, commandName={}, commandId={}",
                    engineId, command.getName(), command.getCommandId());
            CompletableFuture<Object> future = commandFutures.remove(command.getCommandId()); // 清理Future
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException("命令消息没有解析到任何目标Extension"));
            }
            return;
        }

        String associatedChannelId = null;
        if (command.getProperties() != null && command.getProperties().containsKey("__channel_id__")) {
            associatedChannelId = (String) command.getProperties().get("__channel_id__");
            command.getProperties().remove("__channel_id__");
        }

        // 为每个目标Extension分发消息
        for (Location targetLocation : targetLocations) {
            // 获取目标Extension实例
            Extension currentTargetExtension = graphInstance.getExtension(targetLocation.extensionName())
                    .orElse(null);
            if (currentTargetExtension == null) {
                log.warn("Engine: 目标Extension未找到: {}. 无法分发命令: {}", targetLocation.extensionName(), command.getName());
                // 如果找不到Extension，也需要完成对应的Future
                CompletableFuture<Object> future = commandFutures.remove(command.getCommandId());
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(
                            new IllegalStateException("目标Extension未找到: " + targetLocation.extensionName()));
                }
                continue;
            }

            // 默认使用原始消息
            Command finalMessageToSend;
            if (targetLocations.size() > 1) {
                try {
                    // 只有在需要分发到多个目的地时才克隆消息
                    finalMessageToSend = command.clone();
                    // 克隆的命令不应该有新的commandId，它应该保持与原始命令相同的commandId
                    // 因为PathOut是通过原始commandId追踪的。这里不再生成新的ID。
                    // 重新考虑：如果一个命令需要分发到多个extension，它们应该共享同一个commandId和同一个PathOut。
                    // 否则，结果回溯会混乱。所以这里不应该改变commandId。
                } catch (CloneNotSupportedException e) {
                    log.error("克隆命令消息失败，无法分发到所有目标: commandId={}, targetExtensionName={}",
                            command.getCommandId(), targetLocation.extensionName(), e);
                    CompletableFuture<Object> future = commandFutures.remove(command.getCommandId()); // 清理Future
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(e);
                    }
                    continue;
                }
            } else {
                finalMessageToSend = command;
            }
            finalMessageToSend.setDestinationLocations(Collections.singletonList(targetLocation));

            // 获取与此命令ID关联的CompletableFuture
            CompletableFuture<Object> resultFuture = commandFutures.get(finalMessageToSend.getCommandId());
            // resultFuture可能为null，例如Engine内部发起的命令，或者队列满时被丢弃的命令
            // 对于非内部命令，如果Future为null，则表示逻辑错误或被提前处理

            // PathOut的创建已移至Engine.submitMessage(Message,
            // String)方法中，以确保其在命令进入Engine时就创建并关联CompletableFuture。
            // 这里的PathOut是为Command命令创建的，而Data消息的PathIn在processData中创建。
            // pathManager.createOutPath(finalMessageToSend.getCommandId(),
            // finalMessageToSend.getParentCommandId(),
            // finalMessageToSend.getName(), finalMessageToSend.getSourceLocation(),
            // targetLocation,
            // resultFuture, // 传递Engine管理的CompletableFuture
            // ResultReturnPolicy.FIRST_ERROR_OR_LAST_OK,
            // associatedChannelId);

            log.debug("Engine: 关联Command {} 到 Channel {}", finalMessageToSend.getCommandId(), associatedChannelId); // 新增日志

            try {
                // 调用Extension的onCommand方法
                // targetExtension.getContext() 替换为从graphInstance获取context
                graphInstance.getAsyncExtensionEnv(targetLocation.extensionName())
                        .ifPresent(context -> currentTargetExtension.onCommand(finalMessageToSend, context));

                log.debug("Extension处理命令完成: engineId={}, extensionName={}, commandName={}, commandId={}",
                        engineId, targetLocation.extensionName(), finalMessageToSend.getName(),
                        finalMessageToSend.getCommandId());
            } catch (Exception e) {
                log.error("Engine: 调用Extension的onCommand方法异常. Extension: {}, CommandType: {}",
                    targetLocation.extensionName(), finalMessageToSend.getType().getValue(), e);
                // 在Extension处理异常时，也需要回溯错误结果
                // 异常时直接完成Future，不需要通过pathManager.handleResultReturnPolicy
                if (resultFuture != null && !resultFuture.isDone()) {
                    resultFuture.completeExceptionally(e);
                }
                commandFutures.remove(finalMessageToSend.getCommandId()); // 即使异常，也从map中移除
            }
        }
    }

    /**
     * 处理命令结果消息
     * 实现命令结果回溯机制，对应C语言中的ten_path_table_process_cmd_result逻辑
     *
     * @param message 命令结果消息
     */
    private void processCommandResult(Message message) {
        if (!(message instanceof CommandResult commandResult)) {
            log.warn("收到非CommandResult类型的消息在processCommandResult中: engineId={}, messageType={}",
                    engineId, message.getType());
            return;
        }

        long commandId = commandResult.getCommandId();
        if (commandId == 0) { // 检查commandId是否为0（无效值）
            log.warn("CommandResult缺少commandId或commandId无效: engineId={}", engineId);
            return;
        }

        String clientLocationUri = message.getPropertyAsString(PROPERTY_CLIENT_LOCATION_URI);
        if (InternalCommandType.isInternal(message.getName()) && clientLocationUri != null) {
            GraphInstance graphInstance = graphInstances.getByClientLocationUri(clientLocationUri);

            graphInstance.getExtension(ClientConnectionExtension.NAME).ifPresent(extension -> {
                graphInstance.getAsyncExtensionEnv(ClientConnectionExtension.NAME).ifPresent(extensionEnv -> {
                    extension.onCommandResult(commandResult, extensionEnv);
                });
            });
        }

        // 从PathTable中查找对应的PathOut
        Optional<PathOut> pathOutOpt = pathManager.getOutPath(commandId);
        if (pathOutOpt.isEmpty()) {
            log.warn("未找到对应的PathOut: commandId={}. 命令结果可能已通过Future完成或PathOut已过期。", commandId);
            return;
        }

        PathOut pathOut = pathOutOpt.get();

        log.debug("Engine: 处理CommandResult. CommandId: {}, IsFinal: {}, IsSuccess: {}", // 新增日志
                commandResult.getCommandId(), commandResult.isFinal(), commandResult.isSuccess());

        CompletableFuture<Object> associatedFuture = pathOut.getResultFuture();
        if (associatedFuture == null) {
            log.warn("Engine: PathOut {} 未关联CompletableFuture，命令结果无法回传到调用方。", commandId);
            pathManager.removeOutPath(commandId); // 清理PathOut
            return;
        }

        try {
            // 处理结果返回策略，这会影响pathOut.cachedCommandResult和hasReceivedFinalCommandResult
            pathManager.handleResultReturnPolicy(pathOut, commandResult);

            // 如果是最终结果，或者错误结果，则完成对应的CompletableFuture
            if (commandResult.isFinal() || !commandResult.isSuccess()) {
                if (associatedFuture != null && !associatedFuture.isDone()) {
                    if (commandResult.isSuccess()) {
                        associatedFuture.complete(commandResult.getResult());
                    } else {
                        associatedFuture.completeExceptionally(new RuntimeException(
                                "Command execution failed: " + commandResult.getError()));
                    }
                    // 发送CommandResult回客户端
                    if (pathOut.getChannelId() != null) {
                        sendMessageToChannel(pathOut.getChannelId(), commandResult);
                    }
                    log.debug("Engine: 命令结果Future已完成. CommandId: {}", commandId);
                } else {
                    log.warn("Engine: 命令结果Future已完成，但收到重复的最终结果或错误结果: commandId={}", commandId);
                }
                commandFutures.remove(commandId); // 最终结果或错误结果后，移除Future
                pathManager.removeOutPath(commandId); // 移除PathOut
            } else { // 流式中间结果
                // 对于流式结果，如果Future尚未完成且支持中间结果，可以考虑如何处理。
                // 目前，我们将只在最终结果时完成Future。
                log.debug("Engine: 收到中间命令结果. CommandId: {}", commandId);
            }

        } catch (CloneNotSupportedException e) {
            log.error("回溯命令结果时克隆CommandResult失败: commandId={}", commandId, e);
            if (associatedFuture != null && !associatedFuture.isDone()) {
                associatedFuture.completeExceptionally(new RuntimeException("Failed to clone CommandResult"));
            }
            commandFutures.remove(commandId);
            pathManager.removeOutPath(commandId);
        } catch (Exception e) {
            log.error("处理命令结果时发生异常: commandId={}", commandId, e);
            if (associatedFuture != null && !associatedFuture.isDone()) {
                associatedFuture.completeExceptionally(e);
            }
            commandFutures.remove(commandId);
            pathManager.removeOutPath(commandId);
        }
    }

    /**
     * 处理数据消息（包括音视频帧）
     *
     * @param message 数据消息
     */
    private void processData(Message message) {
        log.debug("处理数据消息: engineId={}, messageType={}, messageName={}",
                engineId, message.getType(), message.getName());

        String graphId = null;
        String clientLocationUri = message.getPropertyAsString(PROPERTY_CLIENT_LOCATION_URI);
        if (clientLocationUri != null) {
            graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
        }
        if (message.getSourceLocation() != null) {
            graphId = message.getSourceLocation().graphId();
        }

        if (graphId == null) {
            log.warn("数据消息缺少有效的 graphId，无法进行路由: engineId={}, messageType={}, messageName={}, message={}",
                engineId, message.getType(), message.getName(), message.getName());
        }

        // 2. 查找源GraphInstance
        GraphInstance graphInstance = graphInstances.getByGraphId(graphId);
        if (graphInstance == null) {
            log.warn("源GraphInstance不存在或未加载: engineId={}, graphId={}, messageType={}, messageName={}",
                    engineId, graphId, message.getType(), message.getName());
            return;
        }

        // 3. 解析消息的实际目的地列表，委托给RouteManager
        log.debug("Engine: 正在解析数据消息目标Extension. GraphId: {}, MessageName: {}", graphId, message.getName());

        List<Location> targetLocations = routeManager.resolveMessageDestinations(message);
        // 本身自带的 可能是extension 赋予的
        targetLocations.addAll(message.getDestinationLocations());

        if (targetLocations.isEmpty()) {
            log.warn("数据消息没有解析到任何目标Extension: engineId={}, messageType={}, messageName={}",
                    engineId, message.getType(), message.getName());
            return;
        }

        // 为每个目标Extension分发消息
        for (Location targetLocation : targetLocations) {
            // 获取目标Extension实例
            Extension currentTargetExtension = graphInstance.getExtension(targetLocation.extensionName())
                    .orElse(null);
            if (currentTargetExtension == null) {
                log.warn("Engine: 目标Extension未找到: {}. 无法分发数据消息: {}", targetLocation.extensionName(), message.getName());
                continue;
            }

            // 克隆消息，确保每个Extension接收到独立副本
            Message finalMessageToSend;
            if (targetLocations.size() > 1) {
                try {
                    finalMessageToSend = message.clone();
                } catch (CloneNotSupportedException e) {
                    log.error("克隆数据消息失败，无法分发到所有目标: messageType={}, messageName={}, targetExtensionName={}",
                            message.getType(), message.getName(), targetLocation.extensionName(), e);
                    continue;
                }
            } else {
                finalMessageToSend = message;
            }
            finalMessageToSend.setDestinationLocations(List.of(targetLocation));

            try {
                // 根据消息类型调用Extension的相应方法
                switch (finalMessageToSend.getType()) {
                    case DATA -> graphInstance.getAsyncExtensionEnv(targetLocation.extensionName())
                            .ifPresent(context -> currentTargetExtension.onData((Data) finalMessageToSend, context));
                    case AUDIO_FRAME ->
                        graphInstance.getAsyncExtensionEnv(targetLocation.extensionName())
                                .ifPresent(
                                        context -> currentTargetExtension.onAudioFrame((AudioFrame) finalMessageToSend,
                                                context));
                    case VIDEO_FRAME ->
                        graphInstance.getAsyncExtensionEnv(targetLocation.extensionName())
                                .ifPresent(
                                        context -> currentTargetExtension.onVideoFrame((VideoFrame) finalMessageToSend,
                                                context));
                    default -> log.warn("Engine: 不支持的数据消息类型: {}", finalMessageToSend.getType());
                }
                log.debug("Extension处理数据完成: engineId={}, extensionName={}, messageName={}",
                        engineId, targetLocation.extensionName(), finalMessageToSend.getName());
            } catch (Exception e) {
                log.error("Engine: 调用Extension的onData/onAudioFrame/onVideoFrame方法异常. Extension: {}, MessageType: {}",
                    targetLocation.extensionName(), finalMessageToSend.getType().getValue(), e);
            }
        }
    }

    /**
     * 检查当前线程是否为Engine核心线程
     * 用于确保某些操作只能在Engine线程中执行
     *
     * @return true如果是Engine核心线程
     */
    public boolean isEngineThread() {
        return Thread.currentThread() == coreThread;
    }

    /**
     * 增加引用计数
     */
    public void retain() {
        referenceCount.incrementAndGet();
    }

    /**
     * 减少引用计数，当引用计数为0时自动释放资源
     */
    public void release() {
        long count = referenceCount.decrementAndGet();
        if (count == 0) {
            stop();
        } else if (count < 0) {
            log.warn("Engine引用计数已为负数: engineId={}, count={}", engineId, count);
        }
    }

    /**
     * 添加一个Netty Channel到Engine的Channel映射中
     *
     * @param channel 要添加的Channel实例
     */
    public void addChannel(Channel channel) {
        if (channel == null || channel.id() == null) {
            log.warn("尝试添加空的或无效的Channel到Engine");
            return;
        }
        channelMap.put(channel.id().asShortText(), channel);
        log.debug("Channel已添加: engineId={}, channelId={}", engineId, channel.id().asShortText());
    }

    /**
     * 从Engine的Channel映射中移除一个Netty Channel
     *
     * @param channelId 要移除的Channel的ID
     */
    public void removeChannel(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("尝试移除空的或无效的Channel ID从Engine");
            return;
        }
        Channel removedChannel = channelMap.remove(channelId);
        if (removedChannel != null) {
            log.debug("Channel已移除: engineId={}, channelId={}", engineId, channelId);
        } else {
            log.warn("尝试移除不存在的Channel: engineId={}, channelId={}", engineId, channelId);
        }
    }

    /**
     * 获取Channel实例
     *
     * @param channelId Channel的ID
     * @return Channel实例的Optional，如果不存在则为空
     */
    public Optional<Channel> getChannel(String channelId) {
        return Optional.ofNullable(channelMap.get(channelId));
    }

    /**
     * 处理连接断开事件，清理与该Channel相关的PathOut
     *
     * @param channelId 断开连接的Channel ID
     */
    public void handleChannelDisconnected(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("尝试处理空的或无效的Channel ID断开事件");
            return;
        }

        log.info("处理Channel断开连接事件: engineId={}, channelId={}", engineId, channelId);

        // 通知PathManager处理连接断开事件，它会清理相关的PathOut
        pathManager.handleChannelDisconnected(channelId);

        // 从channelMap中移除，确保完全清理
        channelMap.remove(channelId);
    }

    /**
     * 向Engine发送一个命令，并返回一个CompletableFuture，用于异步获取命令执行结果。
     * 此方法为Engine内部使用，或由EngineAsyncExtensionEnv调用。
     * 实现CommandSubmitter接口。
     *
     * @param command 要发送的命令对象
     * @return CompletableFuture<Object>，代表命令执行的最终结果
     * @throws IllegalStateException 如果Engine未在运行状态
     */
    @Override // 添加Override注解
    public CompletableFuture<Object> submitCommand(Command command) { // 方法名改为submitCommand
        if (state.get() != EngineState.RUNNING) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine未在运行状态，无法发送命令: " + engineId));
            return future;
        }

        if (command.getCommandId() == 0) {
            command.setCommandId(Command.generateCommandId());
        }

        CompletableFuture<Object> future = new CompletableFuture<>();
        commandFutures.put(command.getCommandId(), future);

        // 提交命令到Engine的内部消息队列，不再检查返回值
        submitMessage(command); // 现在是void方法

        return future;
    }

    // Getter方法

    public String getEngineId() {
        return engineId;
    }

    public EngineState getState() {
        return state.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getReferenceCount() {
        return referenceCount.get();
    }

    /**
     * 获取指定clientLocationUri的GraphInstance实例。
     *
     * @param clientLocationUri 客户端位置URI
     * @return GraphInstance的Optional，如果不存在则为空
     */
    public Optional<GraphInstance> getGraphInstance(String clientLocationUri) {
        return Optional.ofNullable(graphInstances.getByClientLocationUri(clientLocationUri));
    }

    /**
     * 将GraphInstance添加到Engine的映射中。
     *
     * @param clientLocationUri       客户端位置URI
     * @param graphInstance 图实例对象
     */
    public void addGraphInstance(String clientLocationUri, GraphInstance graphInstance) {
        graphInstances.put(clientLocationUri, graphInstance);
    }

    /**
     * 从Engine的映射中移除并返回GraphInstance。
     *
     * @param clientLocationUri 客户端位置URI
     * @return 被移除的GraphInstance，如果不存在则为null
     */
    public GraphInstance removeGraphInstance(String clientLocationUri) {
        return graphInstances.remove(clientLocationUri);
    }

    /**
     * 获取队列当前大小（用于监控）
     */
    public int getQueueSize() {
        return inboundMessageQueue.size();
    }

    /**
     * 获取队列容量（用于监控）
     */
    public int getQueueCapacity() {
        return inboundMessageQueue.capacity();
    }

    /**
     * 从Message中提取第一个目标位置的extensionName（辅助方法）
     *
     * @param message 消息对象
     * @return extensionName，如果没有有效的目标位置则返回null
     */
    private String extractExtensionName(Message message) {
        if (message == null || message.getDestinationLocations() == null
                || message.getDestinationLocations().isEmpty()) {
            return null;
        }

        // 取第一个目标位置
        Location firstDestination = message.getDestinationLocations().get(0);
        if (firstDestination == null || firstDestination.extensionName() == null
                || firstDestination.extensionName().isEmpty()) {
            return null;
        }

        return firstDestination.extensionName();
    }

    /**
     * 向指定的Netty Channel发送消息
     *
     * @param channelId 目标Channel的ID
     * @param message   要发送的消息 (可以是Message或其子类，如CommandResult)
     * @return true如果消息成功发送（或提交到Channel的写队列），false如果Channel不存在或写入失败
     */
    private boolean sendMessageToChannel(String channelId, Message message) {
        if (channelId == null || message == null) {
            log.warn("尝试向空Channel ID或发送空消息");
            return false;
        }

        Optional<Channel> channelOpt = getChannel(channelId);
        if (channelOpt.isEmpty()) {
            log.warn("尝试向不存在的Channel发送消息: engineId={}, channelId={}", engineId, channelId);
            return false;
        }

        Channel targetChannel = channelOpt.get();
        if (targetChannel.isActive() && targetChannel.isWritable()) {
            // Netty的writeAndFlush是异步的
            targetChannel.writeAndFlush(message);
            log.debug("消息已发送到Channel: engineId={}, channelId={}, messageType={}, messageName={}",
                    engineId, channelId, message.getType(), message.getName());
            return true;
        } else {
            log.warn("Channel不可用或不可写，无法发送消息: engineId={}, channelId={}", engineId, channelId);
            return false;
        }
    }
}