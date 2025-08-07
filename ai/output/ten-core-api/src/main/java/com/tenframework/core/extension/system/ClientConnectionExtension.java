package com.tenframework.core.extension.system;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.Location;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端连接扩展。
 * 负责管理客户端Channel，并将消息在客户端与Engine之间路由。
 */
@Slf4j
public class ClientConnectionExtension implements Extension {

    public static final String NAME = "client-connection-extension";

    private final Engine engine; // 新增Engine引用

    // 存储客户端Location URI到Channel ID的映射
    private final ConcurrentMap<String, String> clientLocationUriToChannelIdMap = new ConcurrentHashMap<>();

    private AsyncExtensionEnv context;

    public ClientConnectionExtension(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void onConfigure(AsyncExtensionEnv context) {
        this.context = context;
    }

    @Override
    public String getAppUri() {
        return MessageConstants.APP_URI_SYSTEM; // ClientConnectionExtension属于系统应用
    }

    @Override
    public void onCommand(Command message, AsyncExtensionEnv context) {
        String clientLocationUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String channelId = message.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, String.class);

        String originalClientAppUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String originalClientGraphId = message.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        if (originalClientAppUri != null && originalClientGraphId != null) {
            // 这是来自客户端的入站命令消息 (由WebSocketMessageFrameHandler路由到此Extension)
            if (clientLocationUri != null && channelId != null) {
                // 建立clientLocationUri到channelId的映射
                clientLocationUriToChannelIdMap.put(clientLocationUri, channelId);
                log.debug("ClientConnectionExtension: 客户端命令映射已建立或更新: clientLocationUri={}, channelId={}",
                        clientLocationUri, channelId);

                // 将消息的sourceLocation修改为客户端的真实Location
                message.setSourceLocation(
                        new Location(originalClientAppUri, originalClientGraphId, ClientConnectionExtension.NAME));

                // 清除其目的地，让Engine根据图连接进行路由
                message.setDestinationLocations(null);
                // 重新提交到Engine
                context.sendCommand(message);
                log.debug(
                        "ClientConnectionExtension: 重新提交客户端命令消息到Engine进行图内路由. commandName={}, originalClientLocationUri={}",
                        message.getName(), clientLocationUri);
            } else {
                log.warn("ClientConnectionExtension: 收到不完整的入站命令消息，缺少必要的客户端属性. commandName={}", message.getName());
            }
        } else {
            // 这是从其他Extension路由到此的命令消息，意味着需要回传给客户端
            if (clientLocationUri != null && channelId != null) {
                Channel channel = engine.getChannel(channelId).orElse(null);
                if (channel != null && channel.isActive()) {
                    log.debug(
                            "ClientConnectionExtension: 回传命令消息到客户端Channel. clientLocationUri: {}, channelId: {}, commandName: {}",
                            clientLocationUri, channelId, message.getName());
                    channel.writeAndFlush(message);
                } else {
                    log.warn(
                            "ClientConnectionExtension: 无法回传命令消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, commandName: {}",
                            clientLocationUri, channelId, message.getName());
                    removeClientChannelMapping(channelId);
                }
            } else {
                log.warn(
                        "ClientConnectionExtension: 收到不完整的命令回传消息，缺少PROPERTY_CLIENT_LOCATION_URI或PROPERTY_CLIENT_CHANNEL_ID. commandName: {}",
                        message.getName());
            }
        }
    }

    /**
     * 处理从Engine路由到此Extension的出站消息（即需要回传给客户端的消息）。
     *
     * @param message 需要发送给客户端的消息
     */
    @Override
    public void onData(Data message, AsyncExtensionEnv context) {
        String clientLocationUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String channelId = message.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, String.class);

        String originalClientAppUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String originalClientGraphId = message.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        if (originalClientAppUri != null && originalClientGraphId != null) {
            // 这是来自客户端的入站数据消息 (由WebSocketMessageFrameHandler路由到此Extension)
            if (clientLocationUri != null && channelId != null) {
                clientLocationUriToChannelIdMap.put(clientLocationUri, channelId);
                log.debug("ClientConnectionExtension: 客户端数据映射已建立或更新: clientLocationUri={}, channelId={}",
                        clientLocationUri, channelId);

                // 将消息的sourceLocation修改为客户端的真实Location
                message.setSourceLocation(
                        new Location(originalClientAppUri, originalClientGraphId, ClientConnectionExtension.NAME));

                // 清除其目的地，让Engine根据图连接进行路由
                message.setDestinationLocations(null);
                // 重新提交到Engine
                context.sendData(message);
                log.debug(
                        "ClientConnectionExtension: 重新提交客户端数据消息到Engine进行图内路由. messageName={}, originalClientLocationUri={}",
                        message.getName(), clientLocationUri);
            } else {
                log.warn("ClientConnectionExtension: 收到不完整的入站数据消息，缺少必要的客户端属性. messageName={}", message.getName());
            }
        } else {
            // 这是从其他Extension路由到此的数据消息，意味着需要回传给客户端
            if (clientLocationUri != null && channelId != null) {
                Channel channel = engine.getChannel(channelId).orElse(null);
                if (channel != null && channel.isActive()) {
                    log.debug(
                            "ClientConnectionExtension: 回传数据消息到客户端Channel. clientLocationUri: {}, channelId: {}, messageName: {}",
                            clientLocationUri, channelId, message.getName());
                    channel.writeAndFlush(message);
                } else {
                    log.warn(
                            "ClientConnectionExtension: 无法回传数据消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, messageName: {}",
                            clientLocationUri, channelId, message.getName());
                    removeClientChannelMapping(channelId); // 调用新的清理方法
                }
            } else {
                log.warn(
                        "ClientConnectionExtension: 收到不完整的数据回传消息，缺少PROPERTY_CLIENT_LOCATION_URI或PROPERTY_CLIENT_CHANNEL_ID. messageName: {}",
                        message.getName());
            }
        }
    }

    /**
     * 根据clientLocationUri获取对应的Channel ID。
     *
     * @param clientLocationUri 客户端Location URI
     * @return 对应的Channel ID，如果不存在则返回null
     */
    public String getChannelIdByClientLocationUri(String clientLocationUri) {
        return clientLocationUriToChannelIdMap.get(clientLocationUri);
    }

    /**
     * 移除与指定Channel ID相关的所有客户端映射。
     * 当Channel断开连接时调用。
     *
     * @param channelId 要移除的Channel ID
     */
    public void removeClientChannelMapping(String channelId) {
        clientLocationUriToChannelIdMap.entrySet().removeIf(entry -> entry.getValue().equals(channelId));
        log.info("ClientConnectionExtension: 清理了Channel {} 的所有客户端映射.", channelId);
    }

    @Override
    public void onCommandResult(CommandResult commandResult, AsyncExtensionEnv context) {
        String clientLocationUri = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI,
                String.class);
        String channelId = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, String.class);

        if (clientLocationUri != null && channelId != null) {
            Channel channel = engine.getChannel(channelId).orElse(null);
            if (channel != null && channel.isActive()) {
                log.debug(
                        "ClientConnectionExtension: 回传命令结果消息到客户端Channel. clientLocationUri: {}, channelId: {}, commandId: {}",
                        clientLocationUri, channelId, commandResult.getCommandId());
                channel.writeAndFlush(commandResult);
            } else {
                log.warn(
                        "ClientConnectionExtension: 无法回传命令结果消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, commandId: {}",
                        clientLocationUri, channelId, commandResult.getCommandId());
                removeClientChannelMapping(channelId);
            }
        } else {
            log.warn(
                    "ClientConnectionExtension: 收到不完整的命令结果回传消息，缺少PROPERTY_CLIENT_LOCATION_URI或PROPERTY_CLIENT_CHANNEL_ID. commandId: {}",
                    commandResult.getCommandId());
        }
    }

}