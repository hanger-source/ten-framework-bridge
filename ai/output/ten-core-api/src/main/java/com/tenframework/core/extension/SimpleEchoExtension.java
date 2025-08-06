package com.tenframework.core.extension;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.Location;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

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

    private static final ObjectMapper objectMapper = new ObjectMapper(); // Add this static final field
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
        CommandResult result = CommandResult.success(command.getCommandId(),
                Map.of("echo_message", echoMessage, "count", ++messageCount));
        result.setSourceLocation(new Location(context.getAppUri(), context.getGraphId(), context.getExtensionName()));
        sendResult(result);
    }

    @Override
    protected void handleData(Data data, ExtensionContext context) {
        String dataName = data.getName();
        log.info("SimpleEchoExtension收到数据: name={}, sourceLocation={}",
                dataName, data.getSourceLocation());
        log.debug("原始数据内容类型: {}, 编码: {}", data.getContentType(), data.getEncoding());
        if (data.hasData()) {
            log.debug("原始数据大小: {} bytes", data.getDataSize());
        } else {
            log.debug("原始数据不包含有效负载。");
        }

        try {
            // 1. 解析原始数据内容为Map
            byte[] rawDataBytes = data.getDataBytes();
            if (rawDataBytes.length == 0) {
                log.warn("SimpleEchoExtension收到空数据或null数据，无法处理回显。");
                return;
            }
            log.debug("尝试将原始数据解析为JSON Map, 长度: {} bytes", rawDataBytes.length);
            Map<String, Object> originalPayload = objectMapper.readValue(rawDataBytes, Map.class);
            log.debug("原始数据解析成功: {}", originalPayload);
            String originalContent = (String) originalPayload.get("content");
            if (originalContent == null) {
                log.warn("原始数据payload中未找到'content'字段，跳过回显处理。");
                return;
            }

            String echoContent = echoPrefix + originalContent; // 只对原始内容进行前缀
            log.debug("回显内容: {}", echoContent);

            // 2. 更新payload中的content
            originalPayload.put("content", echoContent);
            log.debug("更新后的payload: {}", originalPayload);

            // 3. 将更新后的payload序列化回JSON字节
            byte[] echoedContentBytes = objectMapper.writeValueAsBytes(originalPayload);
            log.debug("回显数据序列化为 {} 字节", echoedContentBytes.length);

            Data echoData = Data.binary(MessageConstants.DATA_NAME_ECHO_DATA, echoedContentBytes); // 使用常量
            echoData.setProperties(Map.of("original_name", dataName, "count", ++messageCount));

            // IMPORTANT: 复制原始消息的__client_channel_id__到回显消息
            String clientChannelId = data.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, String.class); // 使用常量
            if (clientChannelId != null) {
                echoData.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, clientChannelId); // 使用常量
                log.debug("复制 client_channel_id: {}", clientChannelId);
            } else {
                log.warn("原始消息中未找到 __client_channel_id__，无法回传给特定客户端。");
            }

            echoData.setSourceLocation(
                    new Location(context.getAppUri(), context.getGraphId(), context.getExtensionName()));
            // Modified: Instead of sending back to the original source extension (which
            // causes a loop if it's another EchoExtension),
            // send back to the client directly via its original source location if it's a
            // client.
            // The Engine's processData method handles routing to the Channel if the
            // destination appUri is a client.
            if (data.getSourceLocation() != null &&
                (MessageConstants.APP_URI_TEST_CLIENT.equals(data.getSourceLocation().appUri()) ||
                    MessageConstants.APP_URI_HTTP_CLIENT.equals(data.getSourceLocation().appUri()))) {
                echoData.setDestinationLocations(java.util.Collections.singletonList(data.getSourceLocation()));
            } else {
                // Fallback: If not from a recognized client URI, or no source location, send
                // back to the original source location.
                // This might still cause a loop if source is another echo extension, but the
                // primary client case is handled.
                echoData.setDestinationLocations(
                    java.util.Collections.singletonList(data.getSourceLocation()));
            }
            log.debug("准备发送回显数据: name={}, sourceLocation={}, destinationLocations={}",
                echoData.getName(), echoData.getSourceLocation(), echoData.getDestinationLocations());
            sendMessage(echoData);
            log.info("SimpleEchoExtension发送回显数据: name={}, destinationLocations={}",
                    echoData.getName(), echoData.getDestinationLocations());
        } catch (IOException e) {
            log.error("处理数据解析/序列化时发生错误: {}", e.getMessage(), e);
            // 可以在这里发送一个错误消息回客户端，或者只是记录日志并丢弃消息
        } catch (Exception e) {
            log.error("SimpleEchoExtension处理数据时发生意外错误: {}", e.getMessage(), e);
        }
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