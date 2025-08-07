package com.tenframework.core.extension;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.Data; // 新增导入
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
    // 移除 channelIdToChannelMap，因为我们通过 Engine 获取 Channel
    // private final ConcurrentMap<String, Channel> channelIdToChannelMap = new
    // ConcurrentHashMap<>();

    public ClientConnectionExtension(Engine engine) {
        this.engine = engine;
    }

    @Override
    public String getAppUri() {
        return MessageConstants.APP_URI_SYSTEM; // ClientConnectionExtension属于系统应用
    }

    /**
     * 处理来自客户端的入站消息。
     * 该方法会在WebSocketMessageFrameHandler接收到消息后调用，用于注册客户端信息并初步处理消息。
     *
     * @param message           客户端发送的消息
     * @param channelId         Netty Channel的ID
     * @param clientLocationUri 客户端的逻辑Location URI
     * @return 经过处理（可能添加了额外属性）并准备提交给Engine的消息
     */
    public Message handleInboundClientMessage(Message message, String channelId, String clientLocationUri) {
        // 注册或更新映射关系
        if (clientLocationUri != null && channelId != null) {
            clientLocationUriToChannelIdMap.put(clientLocationUri, channelId);
            // channelIdToChannelMap 已经在Engine中管理，这里仅作逻辑映射
            // 确保Message中带有必要的客户端信息，Engine或后续Extension可以使用
            message.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
            message.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, channelId);
            log.debug("ClientConnectionExtension: 注册客户端Location URI和Channel ID映射. clientLocationUri: {}, channelId: {}",
                    clientLocationUri, channelId);
        } else {
            log.warn(
                    "ClientConnectionExtension: 无法注册客户端映射，clientLocationUri或channelId为null. clientLocationUri: {}, channelId: {}",
                    clientLocationUri, channelId);
        }
        return message;
    }

    /**
     * 处理从Engine路由到此Extension的出站消息（即需要回传给客户端的消息）。
     *
     * @param message 需要发送给客户端的消息
     */
    @Override
    public void onData(Data message, ExtensionContext context) {
        // 这里处理的是从Engine提交过来的Data消息，其中包含了之前handleInboundClientMessage设置的客户端信息
        String clientLocationUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String channelId = message.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, String.class);

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
                // 如果Channel不活跃或不存在，清除相关映射
                if (clientLocationUriToChannelIdMap.containsKey(clientLocationUri)) {
                    clientLocationUriToChannelIdMap.remove(clientLocationUri);
                }
            }
        } else {
            log.warn(
                    "ClientConnectionExtension: 收到不完整的回传消息，缺少PROPERTY_CLIENT_LOCATION_URI或PROPERTY_CLIENT_CHANNEL_ID. messageName: {}",
                    message.getName());
        }
    }

    /**
     * 当客户端Channel关闭时，移除相关映射。
     *
     * @param channelId 关闭的Channel ID
     */
    public void removeClientChannelMapping(String channelId) {
        // 查找并移除所有指向该Channel ID的clientLocationUri映射
        clientLocationUriToChannelIdMap.entrySet().removeIf(entry -> entry.getValue().equals(channelId));
        log.debug("ClientConnectionExtension: 移除Channel ID对应的客户端映射. channelId: {}", channelId);
    }
}