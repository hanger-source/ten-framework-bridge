package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM基础抽象类
 * 基于ten-framework AI_BASE的AsyncLLMBaseExtension设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 - 使用BlockingQueue实现生产者-消费者模式
 * 2. 工具编排能力 - 支持动态工具注册和管理
 * 3. 流式处理支持 - 支持流式文本输出和中断机制
 * 4. 会话状态管理 - 维护对话历史和上下文
 * 5. 精确的错误处理和监控
 */
@Slf4j
public abstract class AbstractLLMExtension implements Extension {

    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;

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

    // 性能监控
    private final ExtensionMetrics metrics;

    public AbstractLLMExtension() {
        this.processingQueue = new LinkedBlockingQueue<>();
        this.processingExecutor = Executors.newVirtualThreadPerTaskExecutor();
        // 修正 ExtensionMetrics 的实例化，不再使用 builder()
        this.metrics = new ExtensionMetrics("AbstractLLMExtension"); // 临时名称，会在 onConfigure 中更新
    }

    @Override
    public void onConfigure(ExtensionContext context) {
        this.extensionName = context.getExtensionName();
        this.metrics.setExtensionContext(context); // 设置 ExtensionContext 到 metrics 中
        log.info("LLM扩展配置阶段: extensionName={}", extensionName);
        onLLMConfigure(context);
    }

    @Override
    public void onInit(ExtensionContext context) {
        log.info("LLM扩展初始化阶段: extensionName={}", extensionName);
        onLLMInit(context);
    }

    @Override
    public void onStart(ExtensionContext context) {
        log.info("LLM扩展启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
        this.interrupted.set(false);

        // 启动处理队列
        startProcessingQueue(context);
        onLLMStart(context);
    }

    @Override
    public void onStop(ExtensionContext context) {
        log.info("LLM扩展停止阶段: extensionName={}", extensionName);
        this.isRunning = false;

        // 停止处理队列
        stopProcessingQueue();
        onLLMStop(context);
    }

    @Override
    public void onDeinit(ExtensionContext context) {
        log.info("LLM扩展清理阶段: extensionName={}", extensionName);
        onLLMDeinit(context);

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
    public void onCommand(Command command, ExtensionContext context) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            handleLLMCommand(command, context);
            metrics.recordCommand();
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordConfigure(duration);
        } catch (Exception e) {
            log.error("LLM扩展命令处理异常: extensionName={}, commandName={}",
                    extensionName, command.getName(), e);
            metrics.recordCommandError(e.getMessage());
            sendErrorResult(command, context, "LLM命令处理异常: " + e.getMessage());
        }
    }

    @Override
    public void onData(Data data, ExtensionContext context) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略数据: extensionName={}, dataName={}",
                    extensionName, data.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 将数据加入处理队列
            LLMInputItem inputItem = new LLMInputItem(data, context);
            boolean queued = processingQueue.offer(inputItem);
            if (!queued) {
                log.warn("LLM处理队列已满，丢弃数据: extensionName={}, dataName={}",
                        extensionName, data.getName());
                metrics.recordDataError("处理队列已满");
            } else {
                metrics.recordData();
            }
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordConfigure(duration);
        } catch (Exception e) {
            log.error("LLM扩展数据处理异常: extensionName={}, dataName={}",
                    extensionName, data.getName(), e);
            metrics.recordDataError(e.getMessage());
        }
    }

    @Override
    public void onAudioFrame(AudioFrame audioFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略音频帧: extensionName={}, frameName={}",
                    extensionName, audioFrame.getName());
            return;
        }

        // LLM通常不直接处理音频帧，但可以记录
        metrics.recordAudioFrame();
        log.debug("LLM扩展收到音频帧: extensionName={}, frameName={}",
                extensionName, audioFrame.getName());
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略视频帧: extensionName={}, frameName={}",
                    extensionName, videoFrame.getName());
            return;
        }

        // LLM通常不直接处理视频帧，但可以记录
        metrics.recordVideoFrame();
        log.debug("LLM扩展收到视频帧: extensionName={}, frameName={}",
                extensionName, videoFrame.getName());
    }

    /**
     * 启动处理队列
     */
    private void startProcessingQueue(ExtensionContext context) {
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
    private void processQueue(ExtensionContext context) throws InterruptedException {
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
                metrics.recordConfigure(duration);
            } catch (Exception e) {
                log.error("LLM数据处理异常: extensionName={}", extensionName, e);
                metrics.recordDataError(e.getMessage());
            }
        }
    }

    /**
     * 处理LLM命令
     */
    private void handleLLMCommand(Command command, ExtensionContext context) {
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
    private void handleToolRegister(Command command, ExtensionContext context) {
        try {
            String toolMetadataJson = command.getArg("tool", String.class)
                    .orElseThrow(() -> new IllegalArgumentException("缺少工具元数据"));

            ToolMetadata toolMetadata = parseToolMetadata(toolMetadataJson);

            synchronized (toolsLock) {
                availableTools.add(toolMetadata);
            }

            onToolsUpdate(context, toolMetadata);

            // 发送成功结果
            CommandResult result = CommandResult.success(command.getCommandId(),
                    Map.of("tool_name", toolMetadata.getName()));
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
    private void handleChatCompletionCall(Command command, ExtensionContext context) {
        try {
            // 解析聊天完成参数
            Map<String, Object> args = command.getArgs();

            // 使用虚拟线程执行LLM调用
            CompletableFuture.runAsync(() -> {
                try {
                    onCallChatCompletion(args, context);
                } catch (Exception e) {
                    log.error("LLM聊天完成调用异常: extensionName={}", extensionName, e);
                    metrics.recordCommandError(e.getMessage());
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
    private void handleFlush(ExtensionContext context) {
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
    protected void sendTextOutput(ExtensionContext context, String text, boolean endOfSegment) {
        try {
            Data outputData = Data.text("llm_output", text);
            outputData.setProperties(Map.of(
                    "text", text,
                    "end_of_segment", endOfSegment,
                    "extension_name", extensionName));

            boolean success = context.sendMessage(outputData);
            if (success) {
                metrics.recordResult();
                log.debug("LLM文本输出发送成功: extensionName={}, text={}, endOfSegment={}",
                        extensionName, text, endOfSegment);
            } else {
                log.warn("LLM文本输出发送失败: extensionName={}", extensionName);
            }
        } catch (Exception e) {
            log.error("LLM文本输出发送异常: extensionName={}", extensionName, e);
        }
    }

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, ExtensionContext context, String errorMessage) {
        CommandResult errorResult = CommandResult.error(command.getCommandId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 解析工具元数据
     */
    protected ToolMetadata parseToolMetadata(String json) {
        // 这里应该使用Jackson解析JSON
        // 简化实现，实际应该使用Jackson
        return ToolMetadata.builder()
                .name("parsed_tool")
                .description("Parsed from JSON")
                .build();
    }

    // 抽象方法 - 子类必须实现

    /**
     * LLM配置阶段
     */
    protected abstract void onLLMConfigure(ExtensionContext context);

    /**
     * LLM初始化阶段
     */
    protected abstract void onLLMInit(ExtensionContext context);

    /**
     * LLM启动阶段
     */
    protected abstract void onLLMStart(ExtensionContext context);

    /**
     * LLM停止阶段
     */
    protected abstract void onLLMStop(ExtensionContext context);

    /**
     * LLM清理阶段
     */
    protected abstract void onLLMDeinit(ExtensionContext context);

    /**
     * 处理数据驱动的聊天完成
     */
    protected abstract void onDataChatCompletion(Data data, ExtensionContext context);

    /**
     * 处理命令驱动的聊天完成
     */
    protected abstract void onCallChatCompletion(Map<String, Object> args, ExtensionContext context);

    /**
     * 处理工具更新
     */
    protected abstract void onToolsUpdate(ExtensionContext context, ToolMetadata tool);

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
     * 获取性能指标
     */
    public ExtensionMetrics getMetrics() {
        return metrics;
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
        final Data data;
        final ExtensionContext context;

        LLMInputItem(Data data, ExtensionContext context) {
            this.data = data;
            this.context = context;
        }
    }
}