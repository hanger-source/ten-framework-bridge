package com.tenframework.core.extension;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;

/**
 * Extension性能指标收集类
 * 用于监控Extension的执行状态和性能
 */
public class ExtensionMetrics {

    private final MetricRegistry metricsRegistry;
    private volatile ExtensionContext context; // 新增字段，用于存储ExtensionContext

    public ExtensionMetrics(String extensionName) {
        this.extensionName = extensionName;
        this.metricsRegistry = new MetricRegistry();
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Message Stats
        totalCommands = metricsRegistry.meter(MetricRegistry.name(extensionName, "messages", "commands", "total"));
        totalData = metricsRegistry.meter(MetricRegistry.name(extensionName, "messages", "data", "total"));
        totalAudioFrames = metricsRegistry
                .meter(MetricRegistry.name(extensionName, "messages", "audioFrames", "total"));
        totalVideoFrames = metricsRegistry
                .meter(MetricRegistry.name(extensionName, "messages", "videoFrames", "total"));
        totalResults = metricsRegistry.meter(MetricRegistry.name(extensionName, "messages", "results", "total"));
        totalMessages = metricsRegistry.meter(MetricRegistry.name(extensionName, "messages", "total"));

        // Lifecycle Stats
        configureCount = metricsRegistry.counter(MetricRegistry.name(extensionName, "lifecycle", "configure", "count"));
        configureTimer = metricsRegistry
                .timer(MetricRegistry.name(extensionName, "lifecycle", "configure", "duration"));
        initCount = metricsRegistry.counter(MetricRegistry.name(extensionName, "lifecycle", "init", "count"));
        initTimer = metricsRegistry.timer(MetricRegistry.name(extensionName, "lifecycle", "init", "duration"));
        startCount = metricsRegistry.counter(MetricRegistry.name(extensionName, "lifecycle", "start", "count"));
        startTimer = metricsRegistry.timer(MetricRegistry.name(extensionName, "lifecycle", "start", "duration"));
        stopCount = metricsRegistry.counter(MetricRegistry.name(extensionName, "lifecycle", "stop", "count"));
        stopTimer = metricsRegistry.timer(MetricRegistry.name(extensionName, "lifecycle", "stop", "duration"));
        deinitCount = metricsRegistry.counter(MetricRegistry.name(extensionName, "lifecycle", "deinit", "count"));
        deinitTimer = metricsRegistry.timer(MetricRegistry.name(extensionName, "lifecycle", "deinit", "duration"));

        // Error Stats
        commandErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "commands"));
        dataErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "data"));
        audioFrameErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "audioFrames"));
        videoFrameErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "videoFrames"));
        lifecycleErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "lifecycle"));
        totalErrors = metricsRegistry.counter(MetricRegistry.name(extensionName, "errors", "total"));

        // Performance Stats (using Timers for processing time)
        messageProcessingTimer = metricsRegistry
                .timer(MetricRegistry.name(extensionName, "performance", "messageProcessingTime"));

        // Gauges for dynamic values
        activeThreadsGauge = metricsRegistry.gauge(MetricRegistry.name(extensionName, "performance", "activeThreads"),
                () -> () -> {
                    // TODO: 获取实际活跃线程数
                    // 从ExtensionContext获取实际活跃的虚拟线程任务数
                    if (context != null) {
                        return context.getActiveVirtualThreadCount();
                    }
                    return 0;
                });
        queueSizeGauge = metricsRegistry.gauge(MetricRegistry.name(extensionName, "performance", "queueSize"),
                () -> () -> {
                    // TODO: 获取实际队列大小
                    return 0L;
                });
        memoryUsageGauge = metricsRegistry.gauge(MetricRegistry.name(extensionName, "performance", "memoryUsage"),
                () -> () -> {
                    // TODO: 获取实际内存使用量
                    return 0L;
                });
    }

    // 新增setter方法，在ExtensionContext可用时设置
    public void setExtensionContext(ExtensionContext context) {
        this.context = context;
    }

    /**
     * Extension名称
     */
    private String extensionName;

    /**
     * 消息处理统计
     */
    private Meter totalCommands;
    private Meter totalData;
    private Meter totalAudioFrames;
    private Meter totalVideoFrames;
    private Meter totalResults;
    private Meter totalMessages;

    /**
     * 生命周期统计
     */
    private Counter configureCount;
    private Timer configureTimer;
    private Counter initCount;
    private Timer initTimer;
    private Counter startCount;
    private Timer startTimer;
    private Counter stopCount;
    private Timer stopTimer;
    private Counter deinitCount;
    private Timer deinitTimer;

    /**
     * 错误统计
     */
    private Counter commandErrors;
    private Counter dataErrors;
    private Counter audioFrameErrors;
    private Counter videoFrameErrors;
    private Counter lifecycleErrors;
    private Counter totalErrors;

    private long lastErrorTime = 0L;
    private String lastErrorMessage = "";

    /**
     * 性能统计
     */
    private Timer messageProcessingTimer;
    private Gauge<Integer> activeThreadsGauge;
    private Gauge<Long> queueSizeGauge;
    private Gauge<Long> memoryUsageGauge;

    /**
     * 记录命令处理
     */
    public void recordCommand() {
        totalCommands.mark();
        totalMessages.mark();
    }

    /**
     * 记录数据处理
     */
    public void recordData() {
        totalData.mark();
        totalMessages.mark();
    }

    /**
     * 记录音频帧处理
     */
    public void recordAudioFrame() {
        totalAudioFrames.mark();
        totalMessages.mark();
    }

    /**
     * 记录视频帧处理
     */
    public void recordVideoFrame() {
        totalVideoFrames.mark();
        totalMessages.mark();
    }

    /**
     * 记录结果发送
     */
    public void recordResult() {
        totalResults.mark();
    }

    /**
     * 记录配置阶段
     */
    public void recordConfigure(long duration) {
        configureCount.inc();
        configureTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录初始化阶段
     */
    public void recordInit(long duration) {
        initCount.inc();
        initTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录启动阶段
     */
    public void recordStart(long duration) {
        startCount.inc();
        startTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录停止阶段
     */
    public void recordStop(long duration) {
        stopCount.inc();
        stopTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录清理阶段
     */
    public void recordDeinit(long duration) {
        deinitCount.inc();
        deinitTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录命令错误
     */
    public void recordCommandError(String errorMessage) {
        commandErrors.inc();
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 记录数据错误
     */
    public void recordDataError(String errorMessage) {
        dataErrors.inc();
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 记录生命周期错误
     */
    public void recordLifecycleError(String errorMessage) {
        lifecycleErrors.inc();
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 记录音频帧错误
     */
    public void recordAudioFrameError(String errorMessage) {
        audioFrameErrors.inc();
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 记录视频帧错误
     */
    public void recordVideoFrameError(String errorMessage) {
        videoFrameErrors.inc();
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 记录其他类型错误（例如，异步任务中的错误）
     */
    public void recordOtherError(String errorMessage) {
        totalErrors.inc();
        this.lastErrorMessage = errorMessage;
        this.lastErrorTime = System.currentTimeMillis();
    }

    /**
     * 更新性能统计
     */
    private void updatePerformanceStats(long duration) {
        messageProcessingTimer.update(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        return totalErrors.getCount() == 0 ||
                (System.currentTimeMillis() - lastErrorTime) > 60000; // 1分钟内无错误
    }

    /**
     * 获取性能评分
     */
    public double getPerformanceScore() {
        long totalMessagesCount = totalMessages.getCount();
        long totalErrorsCount = totalErrors.getCount();

        if (totalMessagesCount == 0) {
            return 1.0;
        }

        return Math.max(0.0, 1.0 - ((double) totalErrorsCount / totalMessagesCount));
    }

    // Getter for metricsRegistry, if needed for external reporting
    public MetricRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    /**
     * 记录消息处理时间
     *
     * @param duration 处理持续时间（纳秒）
     */
    public void recordMessageProcessingTime(long duration) {
        messageProcessingTimer.update(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}