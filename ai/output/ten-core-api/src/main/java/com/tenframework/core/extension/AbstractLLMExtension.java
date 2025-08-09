package com.tenframework.core.extension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.util.JsonUtils;
import com.tenframework.core.util.MessageUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM基础抽象类
 * 基于ten-framework AI_BASE的AsyncLLMBaseExtension设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 - 使用BlockingQueue实现生产者-消费者模式
 * 2. 工具编排能力 - 支持动态工具注册和管理
 * 3. 流式处理支持 - 支持流式文本输出和中断机制
 * 4. 会话状态管理 - 维护对话历史和上下文
 * 5. 精确的错误处理和监控 (此处为占位符，需实际集成)
 */
@Slf4j
public abstract class AbstractLLMExtension implements Extension {

    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;
    protected AsyncExtensionEnv context;

    // 异步处理队列 - 核心组件
    private final BlockingQueue<LLMInputItem> processingQueue;
    private final ExecutorService processingExecutor;
    private final AtomicBoolean processingTaskRunning = new AtomicBoolean(false);
    private final AtomicReference<Future<?>> currentProcessingTask = new AtomicReference<>();

    // 工具管理
    private final List<ToolMetadata> availableTools = new CopyOnWriteArrayList<>();
    private final Object toolsLock = new Object();

    // 会话状态
    private final Map<String, Object> sessionState = new ConcurrentHashMap<>();
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    // 性能监控 (简化或移除，因为 metrics 包不存在)
    private final ObjectMapper objectMapper = new ObjectMapper(); // 用于 JSON 解析

    public AbstractLLMExtension() {
        this.processingQueue = new LinkedBlockingQueue<>();
        this.processingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        this.extensionName = env.getExtensionName();
        this.context = env;
        log.info("LLM扩展配置阶段: extensionName={}", extensionName);
        onLLMConfigure(env);
    }

    @Override
    public void onInit(AsyncExtensionEnv env) {
        log.info("LLM扩展初始化阶段: extensionName={}", extensionName);
        onLLMInit(env);
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        log.info("LLM扩展启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
        this.interrupted.set(false);

        // 启动处理队列
        startProcessingQueue(env);
        onLLMStart(env);
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        log.info("LLM扩展停止阶段: extensionName={}", extensionName);
        this.isRunning = false;

        // 停止处理队列
        stopProcessingQueue();
        onLLMStop(env);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        log.info("LLM扩展清理阶段: extensionName={}", extensionName);
        onLLMDeinit(env);

        // 关闭执行器
        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onCommand(Command command, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            handleLLMCommand(command, env);
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("LLM扩展命令处理异常: extensionName={}, commandName={}",
                    extensionName, command.getName(), e);
            sendErrorResult(command, env, "LLM命令处理异常: " + e.getMessage());
        }
    }

    @Override
    public void onData(DataMessage data, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略数据: extensionName={}, dataId={}",
                    extensionName, data.getId());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 将数据加入处理队列
            LLMInputItem inputItem = new LLMInputItem(data, env);
            boolean queued = processingQueue.offer(inputItem);
            if (!queued) {
                log.warn("LLM处理队列已满，丢弃数据: extensionName={}, dataId={}",
                        extensionName, data.getId());
            }
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("LLM扩展数据处理异常: extensionName={}, dataId={}",
                    extensionName, data.getId(), e);
        }
    }

    @Override
    public void onAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略音频帧: extensionName={}, frameId={}",
                    extensionName, audioFrame.getId());
            return;
        }

        log.debug("LLM扩展收到音频帧: extensionName={}, frameId={}",
                extensionName, audioFrame.getId());
    }

    @Override
    public void onVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略视频帧: extensionName={}, frameId={}",
                    extensionName, videoFrame.getId());
            return;
        }

        log.debug("LLM扩展收到视频帧: extensionName={}, frameId={}",
                extensionName, videoFrame.getId());
    }

    @Override
    public void onCommandResult(CommandResult commandResult, AsyncExtensionEnv env) {
        log.warn("LLM扩展收到未处理的 CommandResult: {}. OriginalCommandId: {}", extensionName,
                commandResult.getId(), commandResult.getOriginalCommandId());
    }

    /**
     * 启动处理队列
     */
    private void startProcessingQueue(AsyncExtensionEnv context) {
        if (processingTaskRunning.compareAndSet(false, true)) {
            Future<?> task = processingExecutor.submit(() -> {
                try {
                    processQueue(context);
                } catch (InterruptedException e) {
                    log.info("LLM处理队列被中断: extensionName={}", extensionName);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("LLM处理队列异常: extensionName={}", extensionName, e);
                } finally {
                    processingTaskRunning.set(false);
                }
            });
            currentProcessingTask.set(task);
        }
    }

    /**
     * 停止处理队列
     */
    private void stopProcessingQueue() {
        if (processingTaskRunning.compareAndSet(true, false)) {
            // 清空队列
            processingQueue.clear();

            // 取消当前任务
            Future<?> task = currentProcessingTask.get();
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }
    }

    /**
     * 处理队列循环
     */
    private void processQueue(AsyncExtensionEnv context) throws InterruptedException {
        while (processingTaskRunning.get() && !Thread.currentThread().isInterrupted()) {
            LLMInputItem item = processingQueue.take();
            if (item == null) {
                break; // 退出信号
            }

            try {
                if (interrupted.get()) {
                    log.debug("LLM处理被中断，跳过当前项目: extensionName={}", extensionName);
                    continue;
                }

                long startTime = System.currentTimeMillis();
                onDataChatCompletion(item.data, context);
                long duration = System.currentTimeMillis() - startTime;
            } catch (Exception e) {
                log.error("LLM数据处理异常: extensionName={}", extensionName, e);
            }
        }
    }

    /**
     * 处理LLM命令
     */
    private void handleLLMCommand(Command command, AsyncExtensionEnv context) {
        String commandName = command.getName();

        switch (commandName) {
            case "tool_register":
                handleToolRegister(command, context);
                break;
            case "chat_completion_call":
                handleChatCompletionCall(command, context);
                break;
            case "flush":
                handleFlush(context);
                break;
            default:
                log.warn("未知的LLM命令: extensionName={}, commandName={}",
                        extensionName, commandName);
        }
    }

    /**
     * 处理工具注册
     */
    private void handleToolRegister(Command command, AsyncExtensionEnv context) {
        try {
            // 从 properties 中获取 tool_metadata
            String toolMetadataJson = (String) command.getProperties().get("tool_metadata");
            if (toolMetadataJson == null) {
                throw new IllegalArgumentException("缺少工具元数据");
            }

            ToolMetadata toolMetadata = parseToolMetadata(toolMetadataJson);

            synchronized (toolsLock) {
                availableTools.add(toolMetadata);
            }

            onToolsUpdate(context, toolMetadata);

            // 发送成功结果
            CommandResult result = CommandResult.success(command.getId(), "Tool registered successfully.");
            context.sendResult(result);

            log.info("工具注册成功: extensionName={}, toolName={}",
                    extensionName, toolMetadata.getName());
        } catch (Exception e) {
            log.error("工具注册失败: extensionName={}", extensionName, e);
            sendErrorResult(command, context, "工具注册失败: " + e.getMessage());
        }
    }

    /**
     * 处理聊天完成调用
     */
    private void handleChatCompletionCall(Command command, AsyncExtensionEnv context) {
        try {
            // 从 properties 中获取 args
            Map<String, Object> args = (Map<String, Object>) command.getProperties().get("args");
            if (args == null) {
                throw new IllegalArgumentException("缺少聊天完成参数");
            }

            // 使用虚拟线程执行LLM调用
            CompletableFuture.runAsync(() -> {
                try {
                    onCallChatCompletion(args, context);
                } catch (Exception e) {
                    log.error("LLM聊天完成调用异常: extensionName={}", extensionName, e);
                }
            }, context.getVirtualThreadExecutor());

        } catch (Exception e) {
            log.error("聊天完成调用处理失败: extensionName={}", extensionName, e);
            sendErrorResult(command, context, "聊天完成调用失败: " + e.getMessage());
        }
    }

    /**
     * 处理刷新命令
     */
    private void handleFlush(AsyncExtensionEnv context) {
        log.info("LLM扩展收到刷新命令: extensionName={}", extensionName);

        // 清空处理队列
        processingQueue.clear();

        // 设置中断标志
        interrupted.set(true);

        // 取消当前处理任务
        Future<?> task = currentProcessingTask.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }

        // 重置中断标志
        interrupted.set(false);

        log.info("LLM扩展刷新完成: extensionName={}", extensionName);
    }

    /**
     * 发送文本输出
     */
    protected void sendTextOutput(AsyncExtensionEnv context, String text, boolean endOfSegment) {
        try {
            DataMessage outputData = new DataMessage(
                    java.util.UUID.randomUUID().toString(), // id
                    MessageType.DATA, // type
                    new Location().setAppUri(context.getAppUri()).setGraphId(context.getGraphId())
                            .setNodeId(extensionName), // srcLoc
                    Collections.emptyList(), // destLocs
                    text.getBytes(StandardCharsets.UTF_8) // data
            );
            // 将 text 和 end_of_segment 放入 properties
            outputData.getProperties().put("text", text);
            outputData.getProperties().put("end_of_segment", endOfSegment);
            outputData.getProperties().put("extension_name", extensionName);

            context.sendMessage(outputData);
            log.debug("LLM文本输出发送成功: extensionName={}, text={}, endOfSegment={}",
                    extensionName, text, endOfSegment);
        } catch (Exception e) {
            log.error("LLM文本输出发送异常: extensionName={}", extensionName, e);
        }
    }

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, AsyncExtensionEnv context, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command.getId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 解析工具元数据
     */
    protected ToolMetadata parseToolMetadata(String json) {
        try {
            return objectMapper.readValue(json, ToolMetadata.class);
        } catch (Exception e) {
            log.error("解析工具元数据失败: {}", e.getMessage(), e);
            return ToolMetadata.builder().name("invalid_tool").description("解析失败").build();
        }
    }

    // 抽象方法 - 子类必须实现

    /**
     * LLM配置阶段
     */
    protected abstract void onLLMConfigure(AsyncExtensionEnv context);

    /**
     * LLM初始化阶段
     */
    protected abstract void onLLMInit(AsyncExtensionEnv context);

    /**
     * LLM启动阶段
     */
    protected abstract void onLLMStart(AsyncExtensionEnv context);

    /**
     * LLM停止阶段
     */
    protected abstract void onLLMStop(AsyncExtensionEnv context);

    /**
     * LLM清理阶段
     */
    protected abstract void onLLMDeinit(AsyncExtensionEnv context);

    /**
     * 处理数据驱动的聊天完成
     */
    protected abstract void onDataChatCompletion(DataMessage data, AsyncExtensionEnv context);

    /**
     * 处理命令驱动的聊天完成
     */
    protected abstract void onCallChatCompletion(Map<String, Object> args, AsyncExtensionEnv context);

    /**
     * 处理工具更新
     */
    protected abstract void onToolsUpdate(AsyncExtensionEnv context, ToolMetadata tool);

    // 辅助方法

    /**
     * 获取可用工具列表
     */
    public List<ToolMetadata> getAvailableTools() {
        synchronized (toolsLock) {
            return new ArrayList<>(availableTools);
        }
    }

    /**
     * 获取会话状态
     */
    public Map<String, Object> getSessionState() {
        return sessionState;
    }

    /**
     * LLM输入项
     */
    private static class LLMInputItem {
        final DataMessage data;
        final AsyncExtensionEnv context;

        LLMInputItem(DataMessage data, AsyncExtensionEnv context) {
            this.data = data;
            this.context = context;
        }
    }

    /**
     * LLM工具元数据，用于工具调用功能
     */
    @Data
    @Builder
    public static class ToolMetadata {
        private String name;
        private String description;
        private Map<String, Object> parameters; // 工具参数的JSON Schema
        private List<String> required; // 必填参数列表
    }

    @Override
    public String getExtensionName() {
        return extensionName;
    }

    @Override
    public String getAppUri() {
        return context != null ? context.getAppUri() : null;
    }

    @Override
    public String getGraphId() {
        return context != null ? context.getGraphId() : null;
    }
}