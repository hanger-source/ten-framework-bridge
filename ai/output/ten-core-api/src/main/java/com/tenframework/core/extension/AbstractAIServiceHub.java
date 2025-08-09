package com.tenframework.core.extension;

import com.tenframework.core.engine.CommandSubmitter;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.util.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象AI服务枢纽扩展，用于简化AI服务相关的扩展开发。
 * 提供了统一的接口用于处理命令、数据、音视频帧，并支持异步命令提交和结果回溯。
 */
@Slf4j
public abstract class AbstractAIServiceHub extends BaseExtension {

    @Getter
    @Setter
    protected CommandSubmitter commandSubmitter;

    /**
     * 异步Extension上下文
     */
    @Getter
    protected AsyncExtensionEnv asyncExtensionEnv;

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        super.onConfigure(env);
        this.asyncExtensionEnv = env;
        this.commandSubmitter = env.getCommandSubmitter();
    }

    @Override
    public void onCommand(CommandMessage command, AsyncExtensionEnv env) {
        super.onCommand(command, env);
        handleAIServiceCommand(command, env);
    }

    @Override
    public void onData(DataMessage data, AsyncExtensionEnv env) {
        super.onData(data, env);
        handleAIServiceData(data, env);
    }

    @Override
    public void onAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
        super.onAudioFrame(audioFrame, env);
        handleAIServiceAudioFrame(audioFrame, env);
    }

    @Override
    public void onVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
        super.onVideoFrame(videoFrame, env);
        handleAIServiceVideoFrame(videoFrame, env);
    }

    @Override
    public void onCommandResult(CommandResult commandResult, AsyncExtensionEnv env) {
        super.onCommandResult(commandResult, env);
        handleAIServiceCommandResult(commandResult, env);
    }

    /**
     * AI服务特定的命令处理接口
     *
     * @param command 命令消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceCommand(CommandMessage command, AsyncExtensionEnv context);

    /**
     * AI服务特定的数据处理接口
     *
     * @param data 数据消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceData(DataMessage data, AsyncExtensionEnv context);

    /**
     * AI服务特定的音频帧处理接口
     *
     * @param audioFrame 音频帧消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv context);

    /**
     * AI服务特定的视频帧处理接口
     *
     * @param videoFrame 视频帧消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv context);

    /**
     * AI服务特定的命令结果处理接口
     *
     * @param commandResult 命令结果消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceCommandResult(CommandResult commandResult, AsyncExtensionEnv context);

    protected void sendCommandResult(String commandId, Object result, String errorMessage) {
        CommandResult commandResult = CommandResult.builder()
                .id(commandId)
                .result(result)
                .error(errorMessage != null ? MessageUtils.createError(errorMessage) : null)
                .build();
        asyncExtensionEnv.submitCommandResult(commandResult);
    }

    /**
     * 异步发送命令并等待结果。
     *
     * @param command 命令对象
     * @return 包含命令结果的CompletableFuture
     */
    protected CompletableFuture<Object> submitCommand(CommandMessage command) {
        return commandSubmitter.submitCommand(command);
    }

    protected long generateCommandId() {
        return UUID.randomUUID().getLeastSignificantBits();
    }

    @Override
    public String getAppUri() {
        return asyncExtensionEnv != null ? asyncExtensionEnv.getAppUri() : "unknown";
    }
}