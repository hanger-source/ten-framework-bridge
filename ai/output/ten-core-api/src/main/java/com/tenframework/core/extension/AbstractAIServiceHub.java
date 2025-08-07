package com.tenframework.core.extension;

import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.message.CommandResult;
// import com.tenframework.core.message.StatusCode; // 移除StatusCode导入
import com.tenframework.core.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    public void onConfigure(AsyncExtensionEnv context) {
        super.onConfigure(context);
        onAIServiceConfigure(context);
    }

    @Override
    public void onInit(AsyncExtensionEnv context) {
        super.onInit(context);
        onAIServiceInit(context);
    }

    @Override
    public void onStart(AsyncExtensionEnv context) {
        super.onStart(context);
        onAIServiceStart(context);
    }

    @Override
    public void onStop(AsyncExtensionEnv context) {
        super.onStop(context);
        onAIServiceStop(context);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv context) {
        super.onDeinit(context);
        onAIServiceDeinit(context);
    }

    @Override
    public void onCommand(Command command, AsyncExtensionEnv context) {
        super.onCommand(command, context);
        handleAIServiceCommand(command, context);
    }

    @Override
    public void onData(Data data, AsyncExtensionEnv context) {
        super.onData(data, context);
        handleAIServiceData(data, context);
    }

    @Override
    public void onAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context) {
        super.onAudioFrame(audioFrame, context);
        handleAIServiceAudioFrame(audioFrame, context);
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context) {
        super.onVideoFrame(videoFrame, context);
        handleAIServiceVideoFrame(videoFrame, context);
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