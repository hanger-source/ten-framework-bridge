package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 简单的Echo Extension示例
 * 展示开发者如何开箱即用，只需实现核心业务逻辑
 *
 * 开发者只需要：
 * 1. 继承BaseExtension
 * 2. 实现4个简单的handle方法
 * 3. 可选实现生命周期方法
 *
 * 所有底层能力都由BaseExtension提供：
 * - 自动生命周期管理
 * - 内置异步处理
 * - 自动错误处理和重试
 * - 内置性能监控
 * - 自动资源管理
 */
@Slf4j
public class SimpleEchoExtension extends BaseExtension {

    private String echoPrefix = "Echo: ";
    private long messageCount = 0;

    // 构造函数已被移除，依赖BaseExtension的默认构造函数

    @Override
    protected void handleCommand(Command command, ExtensionContext context) {
        // 开发者只需关注业务逻辑
        String commandName = command.getName();
        log.info("收到命令: {}", commandName);

        // 简单的回显逻辑
        String echoMessage = echoPrefix + commandName;

        // 使用BaseExtension提供的便捷方法发送结果
        sendResult(CommandResult.success(command.getCommandId(),
                Map.of("echo_message", echoMessage, "count", ++messageCount)));
    }

    @Override
    protected void handleData(Data data, ExtensionContext context) {
        // 开发者只需关注业务逻辑
        String dataName = data.getName();
        String dataContent = new String(data.getDataBytes());
        log.info("收到数据: {} = {}", dataName, dataContent);

        // 简单的回显逻辑
        String echoContent = echoPrefix + dataContent;

        // 使用BaseExtension提供的便捷方法发送消息
        Data echoData = Data.binary("echo_data", echoContent.getBytes());
        echoData.setProperties(Map.of("original_name", dataName, "count", ++messageCount));
        sendMessage(echoData);
    }

    @Override
    protected void handleAudioFrame(AudioFrame audioFrame, ExtensionContext context) {
        // 开发者只需关注业务逻辑
        log.debug("收到音频帧: {} ({} bytes)", audioFrame.getName(), audioFrame.getDataSize());

        // 简单的音频处理逻辑
        // 这里可以添加音频分析、VAD检测等
    }

    @Override
    protected void handleVideoFrame(VideoFrame videoFrame, ExtensionContext context) {
        // 开发者只需关注业务逻辑
        log.debug("收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());

        // 简单的视频处理逻辑
        // 这里可以添加视频分析、帧率控制等
    }

    // 可选：自定义配置
    @Override
    protected void onExtensionConfigure(ExtensionContext context) {
        // 从配置中读取echo前缀
        echoPrefix = getConfig("echo_prefix", String.class, "Echo: ");
        log.info("Echo前缀配置: {}", echoPrefix);
    }

    // 可选：自定义健康检查
    @Override
    protected boolean performHealthCheck() {
        // 简单的健康检查：消息计数正常
        return messageCount > 0 && getErrorCount() < 10;
    }
}