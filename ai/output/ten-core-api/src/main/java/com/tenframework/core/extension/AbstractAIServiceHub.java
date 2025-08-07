package com.tenframework.core.extension;

import java.util.Map;

import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.VideoFrame;

/**
 * 抽象的AI服务中心Extension，提供了AI服务相关Extension的通用逻辑。
 * 继承自BaseExtension，并实现了Extension接口。
 */
public abstract class AbstractAIServiceHub extends BaseExtension {

    /**
     * 构造函数，初始化AI服务中心Extension。
     *
     * @param extensionName Extension的名称。
     */
    public AbstractAIServiceHub(String extensionName) {
        super(); // 修改为无参构造函数
    }

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        super.onConfigure(env);
        onAIServiceConfigure(env);
    }

    @Override
    public void onInit(AsyncExtensionEnv env) {
        super.onInit(env);
        onAIServiceInit(env);
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        super.onStart(env);
        onAIServiceStart(env);
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        super.onStop(env);
        onAIServiceStop(env);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        super.onDeinit(env);
        onAIServiceDeinit(env);
    }

    @Override
    public void onCommand(Command command, AsyncExtensionEnv env) {
        super.onCommand(command, env);
        handleAIServiceCommand(command, env);
    }

    @Override
    public void onData(Data data, AsyncExtensionEnv env) {
        super.onData(data, env);
        handleAIServiceData(data, env);
    }

    @Override
    public void onAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv env) {
        super.onAudioFrame(audioFrame, env);
        handleAIServiceAudioFrame(audioFrame, env);
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv env) {
        super.onVideoFrame(videoFrame, env);
        handleAIServiceVideoFrame(videoFrame, env);
    }

    protected abstract void onAIServiceConfigure(AsyncExtensionEnv context);

    protected abstract void onAIServiceInit(AsyncExtensionEnv context);

    protected abstract void onAIServiceStart(AsyncExtensionEnv context);

    protected abstract void onAIServiceStop(AsyncExtensionEnv context);

    protected abstract void onAIServiceDeinit(AsyncExtensionEnv context);

    protected abstract void handleAIServiceCommand(Command command, AsyncExtensionEnv context);

    protected abstract void handleAIServiceData(Data data, AsyncExtensionEnv context);

    protected abstract void handleAIServiceAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context);

    protected abstract void handleAIServiceVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context);

    protected void sendErrorResult(Command command, AsyncExtensionEnv context, String errorMessage) {
        // 构建错误结果
        CommandResult errorResult = CommandResult.error(command.getCommandId(), errorMessage);
        // 发送结果
        context.sendResult(errorResult);
    }

    protected void sendSuccessResult(Command command, AsyncExtensionEnv context, Map<String, Object> result) {
        // 构建成功结果
        CommandResult successResult = CommandResult.success(command.getCommandId(), result);
        // 发送结果
        context.sendResult(successResult);
    }
}