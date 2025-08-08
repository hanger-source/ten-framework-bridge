package com.tenframework.core.extension.system;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.extension.BaseExtension;
import com.tenframework.core.extension.ExtensionMetrics;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.util.ClientLocationUriUtils;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端连接扩展。
 * 负责管理客户端Channel，并将消息在客户端与Engine之间路由。
 */
@Slf4j
public class ClientConnectionExtension extends BaseExtension {

    public static final String NAME = "client-connection-extension";
    private final Engine engine; // 新增Engine引用
    // 此ClientConnectionExtension实例所代表的客户端连接的上下文信息
    private String clientLocationUri;
    private String clientAppUri;

    public ClientConnectionExtension(Engine engine) {
        this.engine = engine;
    }

    @Override
    public ExtensionMetrics getMetrics() {
        return null;
    }

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        // 从Extension的配置属性中获取并存储客户端上下文信息
        clientLocationUri = env.getPropertyString(MessageConstants.PROPERTY_CLIENT_LOCATION_URI).orElse(null);
        clientAppUri = env.getAppUri();

        if (clientLocationUri != null) {
            log.info("ClientConnectionExtension configured and mapped: clientLocationUri={}", clientLocationUri);
        } else {
            log.warn("ClientConnectionExtension configured without complete client context");
        }
    }

    @Override
    public String getAppUri() {
        return clientAppUri;
    }

    @Override
    public void onCommand(Command message, AsyncExtensionEnv context) {
        String originalClientAppUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String originalClientGraphId = message.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        // 根据消息是否包含原始客户端应用和图ID来判断是入站还是出站消息
        if (originalClientAppUri != null && originalClientGraphId != null) {
            // 这是来自客户端的入站命令消息 (由WebSocketMessageFrameHandler路由到此Extension)
            message.setSourceLocation(
                    new Location(originalClientAppUri, originalClientGraphId, ClientConnectionExtension.NAME));

            // 清除其目的地，让Engine根据图连接进行路由
            message.setDestinationLocations(null);
            // 重新提交到Engine
            context.sendCommand(message);
            log.debug(
                    "ClientConnectionExtension: 重新提交客户端命令消息到Engine进行图内路由. commandName={}, clientLocationUri={}",
                    message.getName(), clientLocationUri); // 使用实例变量进行日志记录
        } else {
            log.error(
                "ClientConnectionExtension: 暂不支持回传命令消息到客户端Channel. clientLocationUri: {}, commandName: {}",
                clientLocationUri, message.getName());
        }
    }

    /**
     * 处理从Engine路由到此Extension的出站消息（即需要回传给客户端的消息）。
     *
     * @param message 需要发送给客户端的消息
     */
    @Override
    public void onData(Data message, AsyncExtensionEnv env) {
        String clientLocationUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);

        // 根据消息是否包含原始客户端应用和图ID来判断是入站还是出站消息
        if (clientLocationUri != null) {
            // 这是来自客户端的入站数据消息 (由WebSocketMessageFrameHandler路由到此Extension)
            String clientAppUri = ClientLocationUriUtils.getAppUri(clientLocationUri);
            String clientGraphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
            message.setSourceLocation(
                    new Location(clientAppUri, clientGraphId, ClientConnectionExtension.NAME));

            // 清除其目的地，让Engine根据图连接进行路由
            message.setDestinationLocations(null);
            // 重新提交到Engine
            env.sendData(message);
            log.debug(
                    "ClientConnectionExtension: 重新提交客户端数据消息到Engine进行图内路由. name={}, clientLocationUri={}",
                    message.getName(), clientLocationUri); // 使用实例变量进行日志记录

        } else {
            // 这是从其他Extension路由到此的数据消息，意味着需要回传给客户端
            // 直接使用此ClientConnectionExtension实例自身维护的客户端上下文信息
            String channelId = ClientLocationUriUtils.getChannelId(this.clientLocationUri);
            Channel targetChannel = engine.getChannel(channelId).orElse(null);
            if (targetChannel != null && targetChannel.isActive()) {
                log.debug(
                    "ClientConnectionExtension: 回传数据消息到客户端Channel. clientLocationUri: {}, channelId: {}, messageName: {}",
                    this.clientLocationUri, channelId, message.getName());
                targetChannel.writeAndFlush(message);
            } else {
                log.warn(
                    "ClientConnectionExtension: 无法回传数据消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, messageName: {}",
                    this.clientLocationUri, channelId, message.getName());
            }
        }
    }

    @Override
    protected void handleCommand(Command command, AsyncExtensionEnv context) {

    }

    @Override
    protected void handleData(Data data, AsyncExtensionEnv context) {

    }

    @Override
    protected void handleAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context) {

    }

    @Override
    protected void handleVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context) {

    }

    @Override
    public void onCommandResult(CommandResult commandResult, AsyncExtensionEnv context) {
        // 直接使用此ClientConnectionExtension实例自身维护的客户端上下文信息

        if (clientLocationUri != null) {
            String channelId = ClientLocationUriUtils.getChannelId(clientLocationUri);
            Channel targetChannel = engine.getChannel(channelId).orElse(null);
            if (targetChannel != null && targetChannel.isActive()) {
                log.debug(
                        "ClientConnectionExtension: 回传命令结果消息到客户端Channel. clientLocationUri: {}, channelId: {}, commandId: {}",
                        clientLocationUri, channelId, commandResult.getCommandId());
                targetChannel.writeAndFlush(commandResult);
            } else {
                log.warn(
                        "ClientConnectionExtension: 无法回传命令结果消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, commandId: {}",
                        clientLocationUri, channelId, commandResult.getCommandId());
            }
        } else {
            log.warn(
                    "ClientConnectionExtension: 收到不完整的命令结果回传消息，ClientConnectionExtension未配置完整的客户端上下文. commandId: {}",
                    commandResult.getCommandId());
        }
    }

}