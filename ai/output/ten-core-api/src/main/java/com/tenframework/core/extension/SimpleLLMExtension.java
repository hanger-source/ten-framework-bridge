package com.tenframework.core.extension;

import com.tenframework.core.message.CommandMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.VideoFrameMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 简单的LLM扩展示例，用于演示如何处理不同类型的消息。
 */
@Slf4j
public class SimpleLLMExtension extends AbstractAIServiceHub {

    @Override
    public void onInit(AsyncExtensionEnv env) {
        log.info("SimpleLLMExtension: {} onInit called.", getExtensionName());
        // 可以在这里加载LLM模型或进行其他初始化
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        log.info("SimpleLLMExtension: {} onStart called.", getExtensionName());
        // 可以在这里启动LLM服务
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        log.info("SimpleLLMExtension: {} onStop called.", getExtensionName());
        // 可以在这里停止LLM服务
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        log.info("SimpleLLMExtension: {} onDeinit called.", getExtensionName());
        // 可以在这里释放LLM模型资源
    }

    @Override
    protected void handleAIServiceCommand(CommandMessage command, AsyncExtensionEnv context) {
        log.debug("LLM收到命令: {}", command.getName());
        // 模拟处理命令并发送结果
        // context.sendResult(new CommandResult(command.getCommandId(), "LLM Processed: " + command.getName()));
        sendCommandResult(command.getId(), Map.of("llm_response", "Hello from LLM!"), null);
    }

    @Override
    protected void handleAIServiceData(DataMessage data, AsyncExtensionEnv context) {
        log.debug("LLM收到数据: {}", new String(data.getData()));
        // 模拟处理数据并可能触发其他命令或发送数据
        // context.sendMessage(new Data("llm_processed_data", ("Processed: " + new String(data.getData())).getBytes()));
    }

    @Override
    protected void handleAIServiceAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv context) {
        log.debug("LLM收到音频帧: {} ({}Hz, {}ch)", audioFrame.getName(),
                audioFrame.getSampleRate(), audioFrame.getChannels());
        // 模拟处理音频帧
    }

    @Override
    protected void handleAIServiceVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv context) {
        log.debug("LLM收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());
        // 模拟处理视频帧
    }

    @Override
    protected void handleAIServiceCommandResult(CommandResult commandResult, AsyncExtensionEnv context) {
        log.debug("LLM收到命令结果: {}", commandResult.getId());
        // 处理上游命令的结果
    }

    @Override
    public String getExtensionName() {
        return "SimpleLLMExtension";
    }

    // 示例：LLM可以发送一个命令到其他Extension
    public CompletableFuture<Object> sendLLMCommand(String targetExtension, String commandName, Map<String, Object> args) {
        CommandMessage cmd = new CommandMessage(String.valueOf(generateCommandId()),
                asyncExtensionEnv.getCurrentLocation(),
                commandName, args);
        cmd.setDestinationLocations(Collections.singletonList(asyncExtensionEnv.createLocation(targetExtension)));
        return submitCommand(cmd);
    }
}