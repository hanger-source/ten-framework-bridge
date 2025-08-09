package com.tenframework.core.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import lombok.extern.slf4j.Slf4j;

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
    public void onConfigure(AsyncExtensionEnv env) {
        extensionName = env.getExtensionName();
        // 配置属性将在子类中通过getProperty方法获取
        log.info("控制器配置阶段: extensionName={}", extensionName);
        onControllerConfigure(env);
    }

    @Override
    public void onInit(AsyncExtensionEnv env) {
        log.info("控制器初始化阶段: extensionName={}", extensionName);
        onControllerInit(env);
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        log.info("控制器启动阶段: extensionName={}", extensionName);
        isRunning = true;
        onControllerStart(env);
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        log.info("控制器停止阶段: extensionName={}", extensionName);
        isRunning = false;
        onControllerStop(env);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        log.info("控制器清理阶段: extensionName={}", extensionName);
        onControllerDeinit(env);
    }

    @Override
    public void onCommand(Command command, AsyncExtensionEnv env) {
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
                handleControllerCommand(command, env);
            } catch (Exception e) {
                log.error("控制器命令处理异常: extensionName={}, commandName={}",
                        extensionName, command.getName(), e);
                sendErrorResult(command, env, "控制器处理异常: " + e.getMessage());
            }
        }, env.getVirtualThreadExecutor());
    }

    @Override
    public void onData(DataMessage data, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略数据: extensionName={}, dataId={}",
                    extensionName, data.getId());
            return;
        }

        log.debug("控制器收到数据: extensionName={}, dataId={}",
                extensionName, data.getId());
        handleControllerData(data, env);
    }

    @Override
    public void onAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略音频帧: extensionName={}, frameId={}",
                    extensionName, audioFrame.getId());
            return;
        }

        log.debug("控制器收到音频帧: extensionName={}, frameId={}",
                extensionName, audioFrame.getId());
        handleControllerAudioFrame(audioFrame, env);
    }

    @Override
    public void onVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("控制器未运行，忽略视频帧: extensionName={}, frameId={}",
                    extensionName, videoFrame.getId());
            return;
        }

        log.debug("控制器收到视频帧: extensionName={}, frameId={}",
                extensionName, videoFrame.getId());
        handleControllerVideoFrame(videoFrame, env);
    }

    /**
     * 控制器配置阶段
     */
    protected abstract void onControllerConfigure(AsyncExtensionEnv context);

    /**
     * 控制器初始化阶段
     */
    protected abstract void onControllerInit(AsyncExtensionEnv context);

    /**
     * 控制器启动阶段
     */
    protected abstract void onControllerStart(AsyncExtensionEnv context);

    /**
     * 控制器停止阶段
     */
    protected abstract void onControllerStop(AsyncExtensionEnv context);

    /**
     * 控制器清理阶段
     */
    protected abstract void onControllerDeinit(AsyncExtensionEnv context);

    /**
     * 处理控制器命令
     */
    protected abstract void handleControllerCommand(Command command, AsyncExtensionEnv context);

    /**
     * 处理控制器数据
     */
    protected abstract void handleControllerData(DataMessage data, AsyncExtensionEnv context);

    /**
     * 处理控制器音频帧
     */
    protected abstract void handleControllerAudioFrame(AudioFrameMessage audioFrame,
            AsyncExtensionEnv context);

    /**
     * 处理控制器视频帧
     */
    protected abstract void handleControllerVideoFrame(VideoFrameMessage videoFrame,
            AsyncExtensionEnv context);

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, AsyncExtensionEnv context, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command.getId(), errorMessage); // 使用 Command.getId()
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(Command command, AsyncExtensionEnv context, Object result) {
        CommandResult successResult = CommandResult.success(command.getId(),
                result != null ? result.toString() : ""); // 将结果转换为String
        context.sendResult(successResult);
    }

    @Override
    public String getAppUri() {
        return asyncExtensionEnv != null ? asyncExtensionEnv.getAppUri() : "unknown"; // 从 env 获取 appUri
    }
}