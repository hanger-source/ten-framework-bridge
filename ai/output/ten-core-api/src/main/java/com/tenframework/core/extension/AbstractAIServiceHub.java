package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI服务集线器基础抽象类
 * 用于集成多个AI服务，提供统一的接口
 * 
 * 功能：
 * 1. 管理多个AI服务实例
 * 2. 提供统一的命令处理接口
 * 3. 支持服务路由和负载均衡
 * 4. 处理AI服务的异步响应
 */
@Slf4j
public abstract class AbstractAIServiceHub implements Extension {

    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;

    @Override
    public void onConfigure(ExtensionContext context) {
        this.extensionName = context.getExtensionName();
        // 配置属性将在子类中通过getProperty方法获取
        log.info("AI服务集线器配置阶段: extensionName={}", extensionName);
        onAIServiceConfigure(context);
    }

    @Override
    public void onInit(ExtensionContext context) {
        log.info("AI服务集线器初始化阶段: extensionName={}", extensionName);
        onAIServiceInit(context);
    }

    @Override
    public void onStart(ExtensionContext context) {
        log.info("AI服务集线器启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
        onAIServiceStart(context);
    }

    @Override
    public void onStop(ExtensionContext context) {
        log.info("AI服务集线器停止阶段: extensionName={}", extensionName);
        this.isRunning = false;
        onAIServiceStop(context);
    }

    @Override
    public void onDeinit(ExtensionContext context) {
        log.info("AI服务集线器清理阶段: extensionName={}", extensionName);
        onAIServiceDeinit(context);
    }

    @Override
    public void onCommand(Command command, ExtensionContext context) {
        if (!isRunning) {
            log.warn("AI服务集线器未运行，忽略命令: extensionName={}, commandName={}", 
                    extensionName, command.getName());
            return;
        }

        log.debug("AI服务集线器收到命令: extensionName={}, commandName={}", 
                extensionName, command.getName());

        // 使用虚拟线程处理AI服务命令
        CompletableFuture.runAsync(() -> {
            try {
                handleAIServiceCommand(command, context);
            } catch (Exception e) {
                log.error("AI服务集线器命令处理异常: extensionName={}, commandName={}", 
                        extensionName, command.getName(), e);
                sendErrorResult(command, context, "AI服务处理异常: " + e.getMessage());
            }
        }, context.getVirtualThreadExecutor());
    }

    @Override
    public void onData(com.tenframework.core.message.Data data, ExtensionContext context) {
        if (!isRunning) {
            log.warn("AI服务集线器未运行，忽略数据: extensionName={}, dataName={}", 
                    extensionName, data.getName());
            return;
        }

        log.debug("AI服务集线器收到数据: extensionName={}, dataName={}", 
                extensionName, data.getName());
        handleAIServiceData(data, context);
    }

    @Override
    public void onAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("AI服务集线器未运行，忽略音频帧: extensionName={}, frameName={}", 
                    extensionName, audioFrame.getName());
            return;
        }

        log.debug("AI服务集线器收到音频帧: extensionName={}, frameName={}", 
                extensionName, audioFrame.getName());
        handleAIServiceAudioFrame(audioFrame, context);
    }

    @Override
    public void onVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("AI服务集线器未运行，忽略视频帧: extensionName={}, frameName={}", 
                    extensionName, videoFrame.getName());
            return;
        }

        log.debug("AI服务集线器收到视频帧: extensionName={}, frameName={}", 
                extensionName, videoFrame.getName());
        handleAIServiceVideoFrame(videoFrame, context);
    }

    /**
     * AI服务配置阶段
     */
    protected abstract void onAIServiceConfigure(ExtensionContext context);

    /**
     * AI服务初始化阶段
     */
    protected abstract void onAIServiceInit(ExtensionContext context);

    /**
     * AI服务启动阶段
     */
    protected abstract void onAIServiceStart(ExtensionContext context);

    /**
     * AI服务停止阶段
     */
    protected abstract void onAIServiceStop(ExtensionContext context);

    /**
     * AI服务清理阶段
     */
    protected abstract void onAIServiceDeinit(ExtensionContext context);

    /**
     * 处理AI服务命令
     */
    protected abstract void handleAIServiceCommand(Command command, ExtensionContext context);

    /**
     * 处理AI服务数据
     */
    protected abstract void handleAIServiceData(com.tenframework.core.message.Data data, ExtensionContext context);

    /**
     * 处理AI服务音频帧
     */
    protected abstract void handleAIServiceAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context);

    /**
     * 处理AI服务视频帧
     */
    protected abstract void handleAIServiceVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context);

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, ExtensionContext context, String errorMessage) {
        CommandResult errorResult = CommandResult.error(command.getCommandId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(Command command, ExtensionContext context, Map<String, Object> result) {
        CommandResult successResult = CommandResult.success(command.getCommandId(), result);
        context.sendResult(successResult);
    }
} 