package com.tenframework.core.extension;

import java.util.Map;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的工具扩展示例，用于演示如何处理不同类型的消息。
 */
@Slf4j
public class SimpleToolExtension extends AbstractToolProvider {

    @Override
    public void onInit(AsyncExtensionEnv env) {
        log.info("SimpleToolExtension: {} onInit called.", getExtensionName());
        // 可以在这里加载工具配置或进行其他初始化
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        log.info("SimpleToolExtension: {} onStart called.", getExtensionName());
        // 可以在这里启动工具服务
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        log.info("SimpleToolExtension: {} onStop called.", getExtensionName());
        // 可以在这里停止工具服务
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        log.info("SimpleToolExtension: {} onDeinit called.", getExtensionName());
        // 可以在这里释放工具资源
    }

    @Override
    protected void handleToolCommand(Command command, AsyncExtensionEnv context) {
        log.debug("工具扩展收到命令: {}", command.getName());
        // 模拟处理命令并发送结果
        // env.sendResult(new CommandResult(command.getCommandId(), "Tool Processed: " +
        // command.getName()));
        sendCommandResult(command.getId(), Map.of("tool_response", "Hello from Tool!"), null);
    }

    @Override
    protected void handleToolData(DataMessage data, AsyncExtensionEnv context) {
        log.debug("工具扩展收到数据: {}", new String(data.getData()));
        // 模拟处理数据并可能触发其他命令或发送数据
    }

    @Override
    protected void handleToolAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv context) {
        log.debug("工具扩展收到音频帧: {} ({}Hz, {}ch)", audioFrame.getName(),
                audioFrame.getSampleRate(), audioFrame.getChannels());
        // 模拟处理音频帧
    }

    @Override
    protected void handleToolVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv context) {
        log.debug("工具扩展收到视频帧: {}", videoFrame.getName());
        // 模拟处理视频帧
    }

    @Override
    protected void handleToolCommandResult(CommandResult commandResult, AsyncExtensionEnv context) {
        log.debug("工具扩展收到命令结果: {}", commandResult.getId());
        // 处理上游命令的结果
    }

    @Override
    public String getExtensionName() {
        return "SimpleToolExtension";
    }
}