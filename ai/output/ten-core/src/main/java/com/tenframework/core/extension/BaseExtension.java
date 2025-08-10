package com.tenframework.core.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.tenenv.TenEnv;
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

    private final AtomicLong inboundMessageCounter = new AtomicLong(0);
    private final AtomicLong outboundMessageCounter = new AtomicLong(0);
    private final AtomicLong totalCommandReceived = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0); // Added: for tracking errors

    // Removed engine and extensionContext fields as per previous refactoring
    protected String extensionName;
    protected TenEnv env; // Change from TenEnvProxy to TenEnv
    protected Map<String, Object> configuration = Collections.emptyMap();

    @Override
    public String getExtensionName() {
        return extensionName;
    }

    @Override
    public String getAppUri() {
        return env.getAppUri();
    }

    @Override
    public void init(String extensionId, GraphConfig config, TenEnv env) { // Changed parameter to TenEnv
        extensionName = extensionId;
        this.env = env; // Assign new TenEnv
        configuration = config.toMap();
        log.info("BaseExtension {} initialized with TenEnv.", extensionId);
    }

    @Override
    public void onConfigure(TenEnv env) { // Changed parameter to TenEnv
        // extensionName = env.getExtensionName(); // Get from TenEnv. Extension name is
        // already set in init
        log.info("Extension配置完成: extensionName={}", getExtensionName());
        onExtensionConfigure(env);
    }

    protected void onExtensionConfigure(TenEnv env) {
        // Subclasses can override this
    }

    @Override
    public void onInit(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension初始化阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension启动阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension停止阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension去初始化阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void destroy(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension销毁阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // Changed method name and parameter order
        totalCommandReceived.incrementAndGet();
        log.warn("Extension {} received unhandled Command: {}. Type: {}. Total received: {}", getExtensionName(),
                command.getId(), command.getType(), totalCommandReceived.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled commands
        // Default implementation: return a failure result for unhandled commands
        // Ensure this is posted to the TenEnv's runloop
        env.postTask(() -> env
                .sendResult(CommandResult.fail(command.getId(), "Extension does not support this command.")));
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed method name and parameter order
        log.warn("Extension {} received unhandled CommandResult: {}. OriginalCommandId: {}", getExtensionName(),
                commandResult.getId(), commandResult.getOriginalCommandId());
        errorCounter.incrementAndGet(); // Increment error count for unhandled command results
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled DataMessage: {}. Type: {}. Total received: {}", getExtensionName(),
                dataMessage.getId(), dataMessage.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled data messages
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled AudioFrameMessage: {}. Type: {}. Total received: {}",
                getExtensionName(),
                audioFrame.getId(), audioFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled audio frames
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled VideoFrameMessage: {}. Type: {}. Total received: {}",
                getExtensionName(),
                videoFrame.getId(), videoFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled video frames
    }

    /**
     * 递增内部错误计数。
     */
    protected void incrementErrorCount() {
        errorCounter.incrementAndGet();
    }

    /**
     * 获取当前错误计数。
     *
     * @return 错误计数。
     */
    protected long getErrorCount() {
        return errorCounter.get();
    }
}