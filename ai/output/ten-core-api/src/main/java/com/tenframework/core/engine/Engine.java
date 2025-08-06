package com.tenframework.core.engine;

import com.tenframework.core.Location;
import com.tenframework.core.extension.EngineExtensionContext;
import com.tenframework.core.extension.ExtensionMetrics;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.Data; // 确保导入 Data
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.path.PathOut;
import com.tenframework.core.path.PathTable;
import com.tenframework.core.path.ResultReturnPolicy;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId; // 未使用，但可能之前有
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import com.tenframework.core.server.ChannelDisconnectedException;

/**
 * TEN框架的核心Engine实现
 * 基于单线程事件循环模型，使用Agrona队列实现高性能消息处理
 * 对应C语言中的ten_engine_t结构
 */
@Slf4j
public final class Engine {

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
    private final PathTable pathTable;

    /**
     * Engine核心处理线程 - 单线程ExecutorService
     */
    private final ExecutorService engineThread;

    /**
     * Agrona空闲策略，用于优化消息循环的CPU使用
     */
    private final IdleStrategy idleStrategy;

    /**
     * Engine核心线程的Thread引用，用于线程检查
     */
    private volatile Thread coreThread;

    /**
     * Extension实例注册表，使用extensionName作为键
     */
    private final Map<String, Extension> extensionRegistry;

    /**
     * ExtensionContext实例注册表，与Extension一一对应
     */
    private final Map<String, EngineExtensionContext> extensionContextRegistry;

    /**
     * ExtensionMetrics实例注册表，与Extension一一对应
     */
    private final Map<String, ExtensionMetrics> extensionMetricsRegistry;

    /**
     * 管理活动的Netty Channel，用于消息回传
     */
    private final Map<String, Channel> channelMap;

    /**
     * 维护channelId到相关联的Command ID集合的映射，用于断开连接时清理PathOut
     */
    private final ConcurrentMap<String, ConcurrentSkipListSet<UUID>> channelToCommandIdsMap;

    /**
     * 队列容量，默认64K条消息
     */
    private static final int DEFAULT_QUEUE_CAPACITY = 65536;

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
        this.inboundMessageQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
        this.pathTable = new PathTable();
        this.idleStrategy = new SleepingIdleStrategy(1_000_000L); // 1ms sleep time

        // 初始化Extension注册表
        this.extensionRegistry = new ConcurrentHashMap<>();
        this.extensionContextRegistry = new ConcurrentHashMap<>();
        this.extensionMetricsRegistry = new ConcurrentHashMap<>();
        this.channelMap = new ConcurrentHashMap<>();
        this.channelToCommandIdsMap = new ConcurrentHashMap<>();

        // 创建单线程ExecutorService，使用自定义ThreadFactory
        this.engineThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TEN-Engine-" + engineId);
            t.setDaemon(false); // 非守护线程
            t.setUncaughtExceptionHandler((thread, ex) -> {
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

        // 清理所有Extension资源
        cleanupAllExtensions();

        // 关闭线程池
        engineThread.shutdown();

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
    public boolean submitMessage(Message message) {
        return submitMessage(message, null);
    }

    /**
     * 向Engine提交消息（非阻塞）
     * 由Netty线程或Extension虚拟线程调用
     *
     * @param message   要处理的消息
     * @param channelId 可选的Channel ID，如果消息来自特定Channel
     * @return true如果成功提交，false如果队列已满
     */
    public boolean submitMessage(Message message, String channelId) {
        if (state.get() != EngineState.RUNNING) {
            log.warn("Engine未在运行状态，拒绝消息: engineId={}, messageType={}",
                    engineId, message.getType());
            return false;
        }

        // 使用Agrona队列的非阻塞offer方法
        boolean success = inboundMessageQueue.offer(message);

        if (!success) {
            // 队列满了，根据消息类型决定处理策略
            handleQueueFullback(message);
        } else if (channelId != null && message.getType() == MessageType.COMMAND) {
            // 如果是来自客户端的Command消息，将其channelId关联到PathOut
            // 注意：PathOut在processCommand中创建并关联channelId，这里仅处理队列满的情况
            // 为Command添加一个临时属性，携带channelId，以便processCommand能够获取
            if (message.getProperties() != null) {
                message.getProperties().put("__channel_id__", channelId);
            }
        }

        return success;
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
        if (log.isDebugEnabled()) {
            log.debug("处理消息: engineId={}, messageType={}, messageName={}, sourceLocation={}",
                    engineId, message.getType(), message.getName(), message.getSourceLocation());
        }

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

        // 1. 提取目标Extension名称
        String targetExtensionName = extractExtensionName(command);
        if (targetExtensionName == null) {
            log.warn("命令消息缺少有效的目标Extension: engineId={}, commandName={}, commandId={}",
                    engineId, command.getName(), command.getCommandId());
            return;
        }

        // 2. 查找目标Extension和Context
        Optional<Extension> extensionOpt = getExtension(targetExtensionName);
        Optional<EngineExtensionContext> contextOpt = getExtensionContext(targetExtensionName);

        if (extensionOpt.isEmpty() || contextOpt.isEmpty()) {
            log.warn("目标Extension不存在: engineId={}, extensionName={}, commandName={}, commandId={}",
                    engineId, targetExtensionName, command.getName(), command.getCommandId());
            return;
        }

        Extension extension = extensionOpt.get();
        EngineExtensionContext context = contextOpt.get();

        // 创建PathOut记录命令路径，以便后续结果回溯
        String associatedChannelId = null;
        if (command.getProperties() != null && command.getProperties().containsKey("__channel_id__")) {
            associatedChannelId = (String) command.getProperties().get("__channel_id__");
            // 移除临时属性，避免传递给Extension
            command.getProperties().remove("__channel_id__");
        }

        pathTable.createOutPath(command.getCommandIdAsUUID(), command.getParentCommandIdAsUUID(),
                command.getName(), command.getSourceLocation(), command.getDestinationLocations().get(0),
                new CompletableFuture<>(), ResultReturnPolicy.FIRST_ERROR_OR_LAST_OK, associatedChannelId);

        // 将Command ID与Channel ID关联，用于断开连接时清理
        if (associatedChannelId != null) {
            channelToCommandIdsMap.computeIfAbsent(associatedChannelId, k -> new ConcurrentSkipListSet<>())
                    .add(command.getCommandIdAsUUID());
        }

        // 3. 调用Extension的onCommand方法
        try {
            extension.onCommand(command, context);
            log.debug("Extension处理命令完成: engineId={}, extensionName={}, commandName={}, commandId={}",
                    engineId, targetExtensionName, command.getName(), command.getCommandId());
        } catch (Exception e) {
            log.error("Extension处理命令时发生异常: engineId={}, extensionName={}, commandName={}, commandId={}",
                    engineId, targetExtensionName, command.getName(), command.getCommandId(), e);
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

        String commandIdStr = commandResult.getCommandId();
        if (commandIdStr == null || commandIdStr.isEmpty()) {
            log.warn("CommandResult缺少commandId: engineId={}", engineId);
            return;
        }

        UUID commandId;
        try {
            commandId = UUID.fromString(commandIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("CommandResult的commandId格式无效: engineId={}, commandId={}",
                    engineId, commandIdStr, e);
            return;
        }

        // 从PathTable中查找对应的PathOut
        Optional<PathOut> pathOutOpt = pathTable.getOutPath(commandId);
        if (pathOutOpt.isEmpty()) {
            log.warn("未找到对应的PathOut: engineId={}, commandId={}", engineId, commandId);
            return;
        }

        PathOut pathOut = pathOutOpt.get();

        try {
            // 处理结果返回策略
            handleResultReturnPolicy(pathOut, commandResult);

            // 如果是最终结果，清理PathOut并完成Future
            if (commandResult.isFinal()) {
                completeCommandResult(pathOut, commandResult);
                pathTable.removeOutPath(commandId);
                // 从channelToCommandIdsMap中移除该commandId
                if (pathOut.getChannelId() != null) {
                    ConcurrentSkipListSet<UUID> commandIds = channelToCommandIdsMap.get(pathOut.getChannelId());
                    if (commandIds != null) {
                        commandIds.remove(commandId);
                        if (commandIds.isEmpty()) {
                            channelToCommandIdsMap.remove(pathOut.getChannelId());
                        }
                    }
                }
            }

        } catch (CloneNotSupportedException e) {
            log.error("克隆CommandResult失败: engineId={}, commandId={}",
                    engineId, commandId, e);

            // 异常情况下也要清理资源
            if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                pathOut.getResultFuture().completeExceptionally(e);
            }
            pathTable.removeOutPath(commandId);
            // 从channelToCommandIdsMap中移除该commandId
            if (pathOut.getChannelId() != null) {
                ConcurrentSkipListSet<UUID> commandIds = channelToCommandIdsMap.get(pathOut.getChannelId());
                if (commandIds != null) {
                    commandIds.remove(commandId);
                    if (commandIds.isEmpty()) {
                        channelToCommandIdsMap.remove(pathOut.getChannelId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理命令结果时发生异常: engineId={}, commandId={}",
                    engineId, commandId, e);

            // 异常情况下也要清理资源
            if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                pathOut.getResultFuture().completeExceptionally(e);
            }
            pathTable.removeOutPath(commandId);
            // 从channelToCommandIdsMap中移除该commandId
            if (pathOut.getChannelId() != null) {
                ConcurrentSkipListSet<UUID> commandIds = channelToCommandIdsMap.get(pathOut.getChannelId());
                if (commandIds != null) {
                    commandIds.remove(commandId);
                    if (commandIds.isEmpty()) {
                        channelToCommandIdsMap.remove(pathOut.getChannelId());
                    }
                }
            }
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

        // 1. 提取目标Extension名称
        String targetExtensionName = extractExtensionName(message);
        if (targetExtensionName == null) {
            log.warn("数据消息缺少有效的目标Extension: engineId={}, messageType={}, messageName={}",
                    engineId, message.getType(), message.getName());
            return;
        }

        // 2. 查找目标Extension和Context
        Optional<Extension> extensionOpt = getExtension(targetExtensionName);
        Optional<EngineExtensionContext> contextOpt = getExtensionContext(targetExtensionName);

        if (extensionOpt.isEmpty() || contextOpt.isEmpty()) {
            log.warn("目标Extension不存在: engineId={}, extensionName={}, messageType={}, messageName={}",
                    engineId, targetExtensionName, message.getType(), message.getName());
            return;
        }

        Extension extension = extensionOpt.get();
        EngineExtensionContext context = contextOpt.get();

        // 3. 根据消息类型调用相应的Extension方法
        try {
            switch (message.getType()) {
                case DATA -> {
                    if (message instanceof Data data) { // 使用 Data 类
                        extension.onData(data, context);
                        log.debug("Extension处理数据完成: engineId={}, extensionName={}, messageName={}",
                                engineId, targetExtensionName, data.getName());
                    }
                }
                case AUDIO_FRAME -> {
                    if (message instanceof AudioFrame audioFrame) {
                        extension.onAudioFrame(audioFrame, context);
                        log.debug("Extension处理音频帧完成: engineId={}, extensionName={}, messageName={}",
                                engineId, targetExtensionName, audioFrame.getName());
                    }
                }
                case VIDEO_FRAME -> {
                    if (message instanceof VideoFrame videoFrame) {
                        extension.onVideoFrame(videoFrame, context);
                        log.debug("Extension处理视频帧完成: engineId={}, extensionName={}, messageName={}",
                                engineId, targetExtensionName, videoFrame.getName());
                    }
                }
                default -> {
                    log.warn("未知的数据消息类型: engineId={}, extensionName={}, messageType={}",
                            engineId, targetExtensionName, message.getType());
                }
            }
        } catch (Exception e) {
            log.error("Extension处理数据消息时发生异常: engineId={}, extensionName={}, messageType={}, messageName={}",
                    engineId, targetExtensionName, message.getType(), message.getName(), e);
        }
    }

    /**
     * 处理结果返回策略
     * 根据PathOut中配置的ResultReturnPolicy来决定如何处理命令结果
     */
    private void handleResultReturnPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        ResultReturnPolicy policy = pathOut.getReturnPolicy();

        switch (policy) {
            case FIRST_ERROR_OR_LAST_OK -> handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            case EACH_OK_AND_ERROR -> handleEachOkAndErrorPolicy(pathOut, commandResult);
            default -> {
                log.warn("未知的结果返回策略: engineId={}, policy={}, commandId={}",
                        engineId, policy, commandResult.getCommandId());
                // 默认使用FIRST_ERROR_OR_LAST_OK策略
                handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            }
        }
    }

    /**
     * 处理FIRST_ERROR_OR_LAST_OK策略
     * 优先返回第一个错误，或等待所有OK结果并返回最后一个OK结果
     */
    private void handleFirstErrorOrLastOkPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        // 如果是错误结果且还未收到最终结果，立即完成Future
        if (!commandResult.isSuccess() && !pathOut.isHasReceivedFinalCommandResult()) {
            log.debug("收到错误结果，立即返回: engineId={}, commandId={}, error={}",
                    engineId, commandResult.getCommandId(), commandResult.getError());
            completeCommandResult(pathOut, commandResult);
            return;
        }

        // 如果是成功结果，缓存起来
        if (commandResult.isSuccess()) {
            pathOut.setCachedCommandResult(commandResult);
            log.debug("缓存成功结果: engineId={}, commandId={}",
                    engineId, commandResult.getCommandId());
        }

        // 如果是最终结果，使用缓存的结果或当前结果
        if (commandResult.isFinal()) {
            CommandResult finalResult = pathOut.getCachedCommandResult() != null ? pathOut.getCachedCommandResult()
                    : commandResult;
            completeCommandResult(pathOut, finalResult);
        }
    }

    /**
     * 处理EACH_OK_AND_ERROR策略
     * 返回每个OK或ERROR结果（流式结果）
     */
    private void handleEachOkAndErrorPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        log.debug("流式返回结果: engineId={}, commandId={}, isSuccess={}, isFinal={}",
                engineId, commandResult.getCommandId(), commandResult.isSuccess(), commandResult.isFinal());

        // 对于流式策略，每个结果都需要通知
        // 这里可以通过回调或其他机制通知上层
        // 暂时记录日志，后续可以扩展为实际的流式处理

        // 如果是最终结果，完成Future
        if (commandResult.isFinal()) {
            completeCommandResult(pathOut, commandResult);
        }

        // 如果PathOut关联了channelId，则将结果回传给客户端
        if (pathOut.getChannelId() != null) {
            sendMessageToChannel(pathOut.getChannelId(), commandResult);
        }
    }

    /**
     * 完成命令结果的Future并进行回溯
     * 将CommandResult的commandId恢复为parentCommandId，目的地设置为原始命令的sourceLocation
     */
    private void completeCommandResult(PathOut pathOut, CommandResult commandResult) throws CloneNotSupportedException {
        pathOut.setHasReceivedFinalCommandResult(true);

        // 完成CompletableFuture
        if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
            pathOut.getResultFuture().complete(commandResult);
            log.debug("命令结果Future已完成: engineId={}, commandId={}",
                    engineId, commandResult.getCommandId());
        }

        // 进行结果回溯：创建新的CommandResult消息并重新提交到Engine队列
        if (pathOut.getParentCommandId() != null) {
            CommandResult backtrackResult = (CommandResult) commandResult.clone();

            // 恢复commandId为parentCommandId
            backtrackResult.setCommandId(pathOut.getParentCommandId().toString());

            // 设置目的地为原始命令的sourceLocation
            backtrackResult.setDestinationLocation(pathOut.getSourceLocation());
            backtrackResult.setSourceLocation(pathOut.getDestinationLocation());

            // 重新提交到Engine的消息队列继续回溯
            submitMessage(backtrackResult, pathOut.getChannelId()); // 回溯时保留channelId

            log.debug("命令结果已回溯: engineId={}, originalCommandId={}, parentCommandId={}",
                    engineId, commandResult.getCommandId(), pathOut.getParentCommandId());
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
     * 向指定的Netty Channel发送消息
     *
     * @param channelId 目标Channel的ID
     * @param message   要发送的消息
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

        Channel channel = channelOpt.get();
        if (channel.isActive() && channel.isWritable()) {
            // Netty的writeAndFlush是异步的
            channel.writeAndFlush(message);
            log.debug("消息已发送到Channel: engineId={}, channelId={}, messageType={}, messageName={}",
                    engineId, channelId, message.getType(), message.getName());
            return true;
        } else {
            log.warn("Channel不可用或不可写，无法发送消息: engineId={}, channelId={}", engineId, channelId);
            return false;
        }
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

        // 获取与此Channel相关的所有Command ID
        ConcurrentSkipListSet<UUID> commandIdsToCleanup = channelToCommandIdsMap.remove(channelId);

        if (commandIdsToCleanup != null && !commandIdsToCleanup.isEmpty()) {
            log.debug("发现 {} 个与Channel {} 相关的命令需要清理", commandIdsToCleanup.size(), channelId);
            for (UUID commandId : commandIdsToCleanup) {
                Optional<PathOut> pathOutOpt = pathTable.getOutPath(commandId);
                if (pathOutOpt.isPresent()) {
                    PathOut pathOut = pathOutOpt.get();
                    // 完成Future，表明连接已断开，命令无法返回结果
                    if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                        pathOut.getResultFuture().completeExceptionally(new ChannelDisconnectedException(
                                "Channel " + channelId + " disconnected. CommandResult cannot be returned."));
                    }
                    pathTable.removeOutPath(commandId);
                    log.debug("清理与断开连接Channel相关的PathOut: commandId={}", commandId);
                } else {
                    log.warn("在断开连接清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
                }
            }
        } else {
            log.debug("没有发现与Channel {} 相关的命令需要清理", channelId);
        }

        // 从channelMap中移除，确保完全清理
        channelMap.remove(channelId);
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

    // Extension管理方法

    /**
     * 注册Extension实例到Engine中
     *
     * @param extensionName Extension名称
     * @param extension     Extension实例
     * @param properties    Extension配置属性
     * @return true如果注册成功，false如果Extension名称已存在
     */
    public boolean registerExtension(String extensionName, Extension extension, Map<String, Object> properties) {
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("Extension名称不能为空: engineId={}", engineId);
            return false;
        }
        if (extension == null) {
            log.warn("Extension实例不能为空: engineId={}, extensionName={}", engineId, extensionName);
            return false;
        }

        if (extensionRegistry.containsKey(extensionName)) {
            log.warn("Extension名称已存在: engineId={}, extensionName={}", engineId, extensionName);
            return false;
        }

        // 创建ExtensionContext
        // 传入 extension 实例以便 EngineExtensionContext 可以获取其活跃任务数
        EngineExtensionContext context = new EngineExtensionContext(extensionName, extension.getAppUri(), this,
                properties, extension);

        // 创建Extension性能指标
        ExtensionMetrics metrics = new ExtensionMetrics(extensionName);

        // 注册Extension、Context和Metrics
        extensionRegistry.put(extensionName, extension);
        extensionContextRegistry.put(extensionName, context);
        extensionMetricsRegistry.put(extensionName, metrics);

        // 调用Extension生命周期方法
        try {
            // 1. 配置阶段
            long startTime = System.currentTimeMillis();
            extension.onConfigure(context);
            long configureTime = System.currentTimeMillis() - startTime;
            log.debug("Extension配置完成: engineId={}, extensionName={}, 耗时={}ms",
                    engineId, extensionName, configureTime);

            // 配置阶段超时检查（5秒）
            if (configureTime > 5000) {
                log.warn("Extension配置阶段耗时过长: engineId={}, extensionName={}, 耗时={}ms",
                        engineId, extensionName, configureTime);
            }

            // 2. 初始化阶段
            startTime = System.currentTimeMillis();
            extension.onInit(context);
            long initTime = System.currentTimeMillis() - startTime;
            log.debug("Extension初始化完成: engineId={}, extensionName={}, 耗时={}ms",
                    engineId, extensionName, initTime);

            // 初始化阶段超时检查（10秒）
            if (initTime > 10000) {
                log.warn("Extension初始化阶段耗时过长: engineId={}, extensionName={}, 耗时={}ms",
                        engineId, extensionName, initTime);
            }

            // 3. 启动阶段
            startTime = System.currentTimeMillis();
            extension.onStart(context);
            long startTimeElapsed = System.currentTimeMillis() - startTime;
            log.debug("Extension启动完成: engineId={}, extensionName={}, 耗时={}ms",
                    engineId, extensionName, startTimeElapsed);

            // 启动阶段超时检查（15秒）
            if (startTimeElapsed > 15000) {
                log.warn("Extension启动阶段耗时过长: engineId={}, extensionName={}, 耗时={}ms",
                        engineId, extensionName, startTimeElapsed);
            }

        } catch (Exception e) {
            log.error("Extension生命周期调用失败: engineId={}, extensionName={}, 阶段={}",
                    engineId, extensionName, "onConfigure/onInit/onStart", e);
            // 清理已注册的资源
            extensionRegistry.remove(extensionName);
            extensionContextRegistry.remove(extensionName);
            context.close();
            return false;
        }

        log.info("Extension注册成功: engineId={}, extensionName={}", engineId, extensionName);
        return true;
    }

    /**
     * 注销Extension实例
     *
     * @param extensionName Extension名称
     * @return true如果注销成功，false如果Extension不存在
     */
    public boolean unregisterExtension(String extensionName) {
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("Extension名称不能为空: engineId={}", engineId);
            return false;
        }

        Extension extension = extensionRegistry.remove(extensionName);
        EngineExtensionContext context = extensionContextRegistry.remove(extensionName);
        ExtensionMetrics metrics = extensionMetricsRegistry.remove(extensionName);

        if (extension == null) {
            log.warn("Extension不存在: engineId={}, extensionName={}", engineId, extensionName);
            return false;
        }

        // 调用Extension生命周期清理方法
        try {
            // 1. 停止阶段
            long startTime = System.currentTimeMillis();
            extension.onStop(context);
            long stopTime = System.currentTimeMillis() - startTime;
            log.debug("Extension停止完成: engineId={}, extensionName={}, 耗时={}ms",
                    engineId, extensionName, stopTime);

            // 停止阶段超时检查（10秒）
            if (stopTime > 10000) {
                log.warn("Extension停止阶段耗时过长: engineId={}, extensionName={}, 耗时={}ms",
                        engineId, extensionName, stopTime);
            }

            // 2. 清理阶段
            startTime = System.currentTimeMillis();
            extension.onDeinit(context);
            long deinitTime = System.currentTimeMillis() - startTime;
            log.debug("Extension清理完成: engineId={}, extensionName={}, 耗时={}ms",
                    engineId, extensionName, deinitTime);

            // 清理阶段超时检查（5秒）
            if (deinitTime > 5000) {
                log.warn("Extension清理阶段耗时过长: engineId={}, extensionName={}, 耗时={}ms",
                        engineId, extensionName, deinitTime);
            }

        } catch (Exception e) {
            log.error("Extension生命周期清理失败: engineId={}, extensionName={}, 阶段={}",
                    engineId, extensionName, "onStop/onDeinit", e);
            // 即使清理失败，也要继续关闭Context
        }

        // 关闭ExtensionContext资源
        if (context != null) {
            context.close();
        }

        log.info("Extension注销成功: engineId={}, extensionName={}", engineId, extensionName);
        return true;
    }

    /**
     * 获取Extension实例
     *
     * @param extensionName Extension名称
     * @return Extension实例的Optional，如果不存在则为空
     */
    public Optional<Extension> getExtension(String extensionName) {
        return Optional.ofNullable(extensionRegistry.get(extensionName));
    }

    /**
     * 获取ExtensionContext实例
     *
     * @param extensionName Extension名称
     * @return ExtensionContext实例的Optional，如果不存在则为空
     */
    public Optional<EngineExtensionContext> getExtensionContext(String extensionName) {
        return Optional.ofNullable(extensionContextRegistry.get(extensionName));
    }

    /**
     * 获取当前注册的Extension数量
     */
    public int getExtensionCount() {
        return extensionRegistry.size();
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
     * 清理所有Extension资源（私有方法，在Engine停止时调用）
     */
    private void cleanupAllExtensions() {
        log.info("开始清理所有Extension资源: engineId={}, extensionCount={}", engineId, extensionRegistry.size());

        // 关闭所有ExtensionContext
        extensionContextRegistry.values().forEach(context -> {
            try {
                context.close();
            } catch (Exception e) {
                log.error("关闭ExtensionContext时发生异常: extensionName={}", context.getExtensionName(), e);
            }
        });

        // 清空注册表
        extensionRegistry.clear();
        extensionContextRegistry.clear();
        extensionMetricsRegistry.clear();

        log.info("所有Extension资源清理完成: engineId={}", engineId);
    }
}