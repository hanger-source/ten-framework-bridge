package com.tenframework.core.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import lombok.extern.slf4j.Slf4j;

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
    protected void handleAIServiceCommand(Command command, AsyncExtensionEnv env) {
        log.debug("LLM收到命令: {}", command.getName());
        // 模拟处理命令并发送结果
        // env.sendResult(new CommandResult(command.getCommandId(), "LLM Processed: " +
        // command.getName()));
        sendCommandResult(command.getId(), Map.of("llm_response", "Hello from LLM!"), null);
    }

    @Override
    protected void handleAIServiceData(DataMessage data, AsyncExtensionEnv env) {
        log.debug("LLM收到数据: {}", new String(data.getData()));
        // 模拟处理数据并可能触发其他命令或发送数据
        // env.sendMessage(new Data("llm_processed_data", ("Processed: " + new
        // String(data.getData())).getBytes()));
    }

    @Override
    protected void handleAIServiceAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
        log.debug("LLM收到音频帧: {} ({}Hz, {}ch)", audioFrame.getName(),
                audioFrame.getSampleRate(), audioFrame.getChannels());
        // 模拟处理音频帧
    }

    @Override
    protected void handleAIServiceVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
        log.debug("LLM收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());
        // 模拟处理视频帧
    }

    @Override
    protected void handleAIServiceCommandResult(CommandResult commandResult, AsyncExtensionEnv env) {
        log.debug("LLM收到命令结果: {}", commandResult.getId());
        // 处理上游命令的结果
    }

    @Override
    public String getExtensionName() {
        return "SimpleLLMExtension";
    }

    // 示例：LLM可以发送一个命令到其他Extension
    public CompletableFuture<Object> sendLLMCommand(String targetExtension, String commandName,
            Map<String, Object> args) {
        // 使用通用的 Command 构造函数，这里假设我们发送一个 DATA_MESSAGE 类型的命令作为示例
        // 实际应用中，这里应该根据具体业务定义 Command 子类
        Command cmd = new Command(
                MessageType.DATA_MESSAGE, // 使用一个通用的消息类型
                asyncExtensionEnv.getCurrentLocation(),
                Collections.singletonList(asyncExtensionEnv.createLocation(targetExtension)),
                commandName) {
            // 匿名内部类，可以添加特定的属性，如果需要
            // 例如，可以传递 args 到 properties
            {
                setProperties(args);
            }
        };
        cmd.setId(String.valueOf(generateCommandId()));
        return submitCommand(cmd);
    }
}