package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 控制器/注入器基础抽象类
 * 用于控制和管理其他Extension，提供注入和编排功能
 * 
 * 功能：
 * 1. 管理其他Extension实例
 * 2. 提供Extension编排和路由
 * 3. 处理Extension间的依赖关系
 * 4. 提供Extension生命周期管理
 */
@Slf4j
public abstract class AbstractController implements Extension {

    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;

    @Override
    public void onConfigure(ExtensionContext context) {
        this.extensionName = context.getExtensionName();
        // 配置属性将在子类中通过getProperty方法获取
        log.info("控制器配置阶段: extensionName={}", extensionName);
        onControllerConfigure(context);
    }

    @Override
    public void onInit(ExtensionContext context) {
        log.info("控制器初始化阶段: extensionName={}", extensionName);
        onControllerInit(context);
    }

    @Override
    public void onStart(ExtensionContext context) {
        log.info("控制器启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
        onControllerStart(context);
    }

    @Override
    public void onStop(ExtensionContext context) {
        log.info("控制器停止阶段: extensionName={}", extensionName);
        this.isRunning = false;
        onControllerStop(context);
    }

    @Override
    public void onDeinit(ExtensionContext context) {
        log.info("控制器清理阶段: extensionName={}", extensionName);
        onControllerDeinit(context);
    }

    @Override
    public void onCommand(Command command, ExtensionContext context) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略命令: extensionName={}, commandName={}", 
                    extensionName, command.getName());
            return;
        }

        log.debug("控制器收到命令: extensionName={}, commandName={}", 
                extensionName, command.getName());

        // 使用虚拟线程处理控制器命令
        CompletableFuture.runAsync(() -> {
            try {
                handleControllerCommand(command, context);
            } catch (Exception e) {
                log.error("控制器命令处理异常: extensionName={}, commandName={}", 
                        extensionName, command.getName(), e);
                sendErrorResult(command, context, "控制器处理异常: " + e.getMessage());
            }
        }, context.getVirtualThreadExecutor());
    }

    @Override
    public void onData(com.tenframework.core.message.Data data, ExtensionContext context) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略数据: extensionName={}, dataName={}", 
                    extensionName, data.getName());
            return;
        }

        log.debug("控制器收到数据: extensionName={}, dataName={}", 
                extensionName, data.getName());
        handleControllerData(data, context);
    }

    @Override
    public void onAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略音频帧: extensionName={}, frameName={}", 
                    extensionName, audioFrame.getName());
            return;
        }

        log.debug("控制器收到音频帧: extensionName={}, frameName={}", 
                extensionName, audioFrame.getName());
        handleControllerAudioFrame(audioFrame, context);
    }

    @Override
    public void onVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略视频帧: extensionName={}, frameName={}", 
                    extensionName, videoFrame.getName());
            return;
        }

        log.debug("控制器收到视频帧: extensionName={}, frameName={}", 
                extensionName, videoFrame.getName());
        handleControllerVideoFrame(videoFrame, context);
    }

    /**
     * 控制器配置阶段
     */
    protected abstract void onControllerConfigure(ExtensionContext context);

    /**
     * 控制器初始化阶段
     */
    protected abstract void onControllerInit(ExtensionContext context);

    /**
     * 控制器启动阶段
     */
    protected abstract void onControllerStart(ExtensionContext context);

    /**
     * 控制器停止阶段
     */
    protected abstract void onControllerStop(ExtensionContext context);

    /**
     * 控制器清理阶段
     */
    protected abstract void onControllerDeinit(ExtensionContext context);

    /**
     * 处理控制器命令
     */
    protected abstract void handleControllerCommand(Command command, ExtensionContext context);

    /**
     * 处理控制器数据
     */
    protected abstract void handleControllerData(com.tenframework.core.message.Data data, ExtensionContext context);

    /**
     * 处理控制器音频帧
     */
    protected abstract void handleControllerAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context);

    /**
     * 处理控制器视频帧
     */
    protected abstract void handleControllerVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context);

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

    /**
     * 转发命令到其他Extension
     */
    protected void forwardCommand(Command command, ExtensionContext context, String targetExtension) {
        // 这里需要访问Engine来转发命令
        // 在实际实现中，可能需要通过Engine的API来实现
        log.debug("转发命令到Extension: command={}, target={}", command.getName(), targetExtension);
    }

    /**
     * 广播命令到多个Extension
     */
    protected void broadcastCommand(Command command, ExtensionContext context, String... targetExtensions) {
        for (String target : targetExtensions) {
            forwardCommand(command, context, target);
        }
    }
} 