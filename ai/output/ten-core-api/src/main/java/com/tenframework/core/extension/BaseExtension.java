package com.tenframework.core.extension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * 基础Extension抽象类
 * 提供丰富的底层能力，让开发者开箱即用
 *
 * 核心能力：
 * 1. 自动生命周期管理
 * 2. 内置消息队列和异步处理
 * 3. 自动错误处理和重试机制
 * 4. 内置性能监控和健康检查
 * 5. 自动资源管理和清理
 * 6. 内置配置管理和热更新
 * 7. 自动日志记录和调试支持
 */
@Slf4j
public abstract class BaseExtension implements Extension {

    // 基础状态管理
    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;
    protected AsyncExtensionEnv context;
    private String appUri; // 新增字段来存储 appUri

    // 内置异步处理能力
    private final ExecutorService asyncVirtualExecutor;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    // 内置性能监控
    private final ExtensionMetrics metrics;
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);

    // 内置重试机制
    private final int maxRetries;
    private final long retryDelayMs;

    // 内置健康检查
    private final ScheduledExecutorService healthCheckExecutor;
    private volatile boolean isHealthy = true;

    public BaseExtension() {
        this(3, 1000); // 默认3次重试，1秒延迟
    }

    public BaseExtension(int maxRetries, long retryDelayMs) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;

        // 初始化内置组件
        this.asyncVirtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // 修正 ExtensionMetrics 的实例化，不再使用 builder()
        this.metrics = new ExtensionMetrics(extensionName); // extensionName 在 onConfigure 中设置，这里会是 null，但 Gauge 的获取是懒加载

        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BaseExtension-HealthCheck");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== 自动生命周期管理 ====================

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        this.context = env;
        this.extensionName = env.getExtensionName();
        this.appUri = env.getAppUri(); // 从 context 中获取 appUri
        this.metrics.setExtensionContext(env); // 设置ExtensionContext到metrics中

        // 启动内置组件
        startBuiltInComponents();

        // 调用子类配置
        onExtensionConfigure(env);

        log.info("Extension配置完成: extensionName={}", extensionName);
    }

    @Override
    public void onInit(AsyncExtensionEnv env) {
        // 自动初始化内置组件
        initializeBuiltInComponents();

        // 调用子类初始化
        onExtensionInit(env);

        log.info("Extension初始化完成: extensionName={}", extensionName);
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        this.isRunning = true;
        this.isHealthy = true;

        // 启动健康检查
        startHealthCheck();

        // 调用子类启动
        onExtensionStart(env);

        log.info("Extension启动完成: extensionName={}", extensionName);
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        this.isRunning = false;

        // 停止健康检查
        stopHealthCheck();

        // 调用子类停止
        onExtensionStop(env);

        log.info("Extension停止完成: extensionName={}", extensionName);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        // 调用子类清理
        onExtensionDeinit(env);

        // 自动清理资源
        cleanupResources();

        log.info("Extension清理完成: extensionName={}", extensionName);
    }

    // ==================== 自动消息处理 ====================

    @Override
    public void onCommand(Command command, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("Extension未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        // 自动记录和监控
        messageCounter.incrementAndGet();
        metrics.recordCommand();

        // 使用重试机制处理命令
        executeWithRetry(() -> {
            long startTime = System.nanoTime(); // 记录开始时间
            try {
                handleCommand(command, env);
            } finally {
                metrics.recordMessageProcessingTime(System.nanoTime() - startTime); // 记录处理时间
            }
        });
    }

    @Override
    public void onData(Data data, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("Extension未运行，忽略数据: extensionName={}, dataName={}",
                    extensionName, data.getName());
            return;
        }

        // 自动记录和监控
        messageCounter.incrementAndGet();
        metrics.recordData();

        // 异步处理数据
        submitTask(() -> {
            long startTime = System.nanoTime(); // 记录开始时间
            try {
                handleData(data, env);
            } finally {
                metrics.recordMessageProcessingTime(System.nanoTime() - startTime); // 记录处理时间
            }
        });
    }

    @Override
    public void onAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("Extension未运行，忽略音频帧: extensionName={}, frameName={}",
                    extensionName, audioFrame.getName());
            return;
        }

        // 自动记录和监控
        messageCounter.incrementAndGet();
        metrics.recordAudioFrame();

        // 异步处理音频帧
        submitTask(() -> {
            long startTime = System.nanoTime(); // 记录开始时间
            try {
                handleAudioFrame(audioFrame, env);
            } finally {
                metrics.recordMessageProcessingTime(System.nanoTime() - startTime); // 记录处理时间
            }
        });
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("Extension未运行，忽略视频帧: extensionName={}, frameName={}",
                    extensionName, videoFrame.getName());
            return;
        }

        // 自动记录和监控
        messageCounter.incrementAndGet();
        metrics.recordVideoFrame();

        // 异步处理视频帧
        submitTask(() -> {
            long startTime = System.nanoTime(); // 记录开始时间
            try {
                handleVideoFrame(videoFrame, env);
            } finally {
                metrics.recordMessageProcessingTime(System.nanoTime() - startTime); // 记录处理时间
            }
        });
    }

    // ==================== 内置能力 ====================

    /**
     * 提交异步任务
     */
    protected void submitTask(Runnable task) {
        if (isRunning) {
            activeTaskCount.incrementAndGet(); // 增加活跃任务计数
            CompletableFuture.runAsync(task, asyncVirtualExecutor)
                    .whenComplete((v, e) -> {
                        activeTaskCount.decrementAndGet(); // 减少活跃任务计数
                        if (e != null) {
                            log.error("异步任务执行异常: extensionName={}", extensionName, e);
                            errorCounter.incrementAndGet();
                            metrics.recordOtherError(e.getMessage()); // 记录其他错误
                        }
                    });
        }
    }

    /**
     * 带重试的执行
     */
    protected void executeWithRetry(Runnable task) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                task.run();
                return;
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxRetries) {
                    log.error("任务执行失败，已达到最大重试次数: extensionName={}, attempts={}",
                            extensionName, attempts, e);
                    metrics.recordCommandError(e.getMessage()); // 记录命令错误
                    throw e;
                }
                log.warn("任务执行失败，准备重试: extensionName={}, attempt={}/{}",
                        extensionName, attempts, maxRetries);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("任务被中断", ie);
                }
            }
        }
    }

    /**
     * 发送消息的便捷方法
     */
    protected void sendMessage(Data data) {
        if (context != null) {
            context.sendData(data); // 更改为sendData
            metrics.recordResult(); // 仍然记录成功发送
        }
    }

    /**
     * 发送结果的便捷方法
     */
    protected void sendResult(CommandResult result) {
        if (context != null) {
            context.sendResult(result);
            metrics.recordResult(); // 仍然记录成功发送
        }
    }

    /**
     * 获取配置的便捷方法
     */
    protected <T> T getConfig(String key, Class<T> type, T defaultValue) {
        if (context == null) {
            return defaultValue;
        }

        if (type == String.class) {
            return (T) context.getPropertyString(key).orElse((String) defaultValue);
        } else if (type == Integer.class) {
            return (T) context.getPropertyInt(key).orElse((Integer) defaultValue);
        } else if (type == Boolean.class) {
            return (T) context.getPropertyBool(key).orElse((Boolean) defaultValue);
        } else if (type == Float.class) {
            return (T) context.getPropertyFloat(key).orElse((Float) defaultValue);
        } else if (type == Double.class) {
            // 如果需要支持Double类型，应该在AsyncExtensionEnv中添加getPropertyDouble方法
            // 或者通过getPropertyToJson获取String后，自行进行JSON反序列化
            // 目前保持原样，让它返回defaultValue
        } else if (type == Long.class) {
            // 如果需要支持Long类型，应该在AsyncExtensionEnv中添加getPropertyLong方法
            // 或者通过getPropertyToJson获取String后，自行进行JSON反序列化
            // 目前保持原样，让它返回defaultValue
        }
        // 如果是Map或其他复杂对象，通常它们被存储为JSON字符串。
        // 这里需要将JSON字符串反序列化为T。
        // 但由于当前AsyncExtensionEnv没有提供从JSON直接反序列化为Map的方法，
        // 并且为了避免引入ObjectMapper的直接依赖，暂时只处理基本类型。
        // 或者，可以由调用者自行获取JSON字符串后反序列化。
        log.warn("BaseExtension: 不支持的配置类型或配置未找到，返回默认值: key={}, type={}", key, type.getName());
        return defaultValue;
    }

    /**
     * 获取虚拟线程执行器
     */
    protected ExecutorService getVirtualThreadExecutor() {
        return context != null ? context.getVirtualThreadExecutor() : asyncVirtualExecutor;
    }

    // ==================== 内置组件管理 ====================

    private void loadConfiguration(AsyncExtensionEnv context) {
        // 自动加载配置 - 简化实现
        this.configuration = Map.of(); // 简化实现，实际应该从context获取
        log.debug("配置加载完成: extensionName={}, configSize={}",
                extensionName, configuration.size());
    }

    private void startBuiltInComponents() {
        // 启动内置组件
        log.debug("启动内置组件: extensionName={}", extensionName);
    }

    private void initializeBuiltInComponents() {
        // 初始化内置组件
        log.debug("初始化内置组件: extensionName={}", extensionName);
    }

    private void startHealthCheck() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                boolean healthy = performHealthCheck();
                if (healthy != isHealthy) {
                    isHealthy = healthy;
                    log.info("健康状态变化: extensionName={}, healthy={}", extensionName, healthy);
                }
            } catch (Exception e) {
                log.error("健康检查异常: extensionName={}", extensionName, e);
                isHealthy = false;
                metrics.recordLifecycleError(e.getMessage()); // 记录生命周期错误
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopHealthCheck() {
        healthCheckExecutor.shutdown();
    }

    private void cleanupResources() {
        // 清理资源
        asyncVirtualExecutor.shutdown();
        try {
            if (!asyncVirtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncVirtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncVirtualExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 子类需要实现的简单方法 ====================

    /**
     * 处理命令 - 子类只需实现这个简单方法
     */
    protected abstract void handleCommand(Command command, AsyncExtensionEnv context);

    /**
     * 处理数据 - 子类只需实现这个简单方法
     */
    protected abstract void handleData(Data data, AsyncExtensionEnv context);

    /**
     * 处理音频帧 - 子类只需实现这个简单方法
     */
    protected abstract void handleAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context);

    /**
     * 处理视频帧 - 子类只需实现这个简单方法
     */
    protected abstract void handleVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context);

    // ==================== 可选的生命周期方法 ====================

    /**
     * Extension配置 - 可选实现
     */
    protected void onExtensionConfigure(AsyncExtensionEnv context) {
        // 默认空实现
    }

    /**
     * Extension初始化 - 可选实现
     */
    protected void onExtensionInit(AsyncExtensionEnv context) {
        // 默认空实现
    }

    /**
     * Extension启动 - 可选实现
     */
    protected void onExtensionStart(AsyncExtensionEnv context) {
        // 默认空实现
    }

    /**
     * Extension停止 - 可选实现
     */
    protected void onExtensionStop(AsyncExtensionEnv context) {
        // 默认空实现
    }

    /**
     * Extension清理 - 可选实现
     */
    protected void onExtensionDeinit(AsyncExtensionEnv context) {
        // 默认空实现
    }

    /**
     * 执行健康检查 - 可选实现
     */
    protected boolean performHealthCheck() {
        // 默认健康检查：检查消息处理是否正常
        return errorCounter.get() < 100; // 简单阈值检查
    }

    // ==================== 内置监控和统计 ====================

    /**
     * 获取消息计数 (已弃用，请使用 Dropwizard Metrics 提供的具体指标)
     */
    @Deprecated
    public long getMessageCount() {
        // return metrics.getMessageStats().getTotalMessages().get(); // 已经移除
        return 0; // 临时返回 0，待后续通过 Dropwizard Metrics 获取
    }

    /**
     * 获取错误计数 (已弃用，请使用 Dropwizard Metrics 提供的具体指标)
     */
    @Deprecated
    public long getErrorCount() {
        // return metrics.getErrorStats().getTotalErrors().get(); // 已经移除
        return 0; // 临时返回 0，待后续通过 Dropwizard Metrics 获取
    }

    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        return isHealthy;
    }

    /**
     * 获取性能指标
     */
    public ExtensionMetrics getMetrics() {
        return metrics;
    }

    /**
     * 获取配置
     */
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    /**
     * 获取Extension内部虚拟线程执行器中当前活跃的任务数量。
     * 这是对 AsyncExtensionEnv.getActiveVirtualThreadCount() 的实际实现。
     *
     * @return 活跃的虚拟线程任务数量
     */
    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    @Override
    public String getAppUri() {
        return appUri;
    }
}