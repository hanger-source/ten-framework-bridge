package com.tenframework.core.extension.system;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
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

    // 存储客户端Location URI到Channel ID的映射 (这是全局的，由所有实例共享)
    // 用于处理入站消息的首次映射，以及通用地查找channelId
    private static final ConcurrentMap<String, String> clientLocationUriToChannelIdMap = new ConcurrentHashMap<>();
    // 此ClientConnectionExtension实例所代表的客户端连接的上下文信息
    private String clientLocationUri;
    private String clientAppUri;
    private String clientGraphId;
    private String channelId; // 此Extension实例关联的Channel ID

    public ClientConnectionExtension(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void onConfigure(AsyncExtensionEnv context) {
        // 从Extension的配置属性中获取并存储客户端上下文信息
        this.clientLocationUri = context.getPropertyString(MessageConstants.PROPERTY_CLIENT_LOCATION_URI).orElse(null);
        this.clientAppUri = context.getPropertyString(MessageConstants.PROPERTY_CLIENT_APP_URI).orElse(null);
        this.clientGraphId = context.getPropertyString(MessageConstants.PROPERTY_CLIENT_GRAPH_ID).orElse(null);
        this.channelId = context.getPropertyString(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID).orElse(null);

        if (this.clientLocationUri != null && this.channelId != null) {
            // 将此ClientConnectionExtension实例代表的客户端连接信息注册到全局映射中
            clientLocationUriToChannelIdMap.put(this.clientLocationUri, this.channelId);
            log.info(
                    "ClientConnectionExtension configured and mapped: clientLocationUri={}, channelId={}, clientAppUri={}, clientGraphId={}",
                    this.clientLocationUri, this.channelId, this.clientAppUri, this.clientGraphId);
        } else {
            log.warn(
                    "ClientConnectionExtension configured without complete client context: clientLocationUri={}, channelId={}",
                    this.clientLocationUri, this.channelId);
        }
    }

    @Override
    public String getAppUri() {
        return MessageConstants.APP_URI_SYSTEM; // ClientConnectionExtension属于系统应用
    }

    @Override
    public void onCommand(Command message, AsyncExtensionEnv context) {
        String originalClientAppUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String originalClientGraphId = message.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        // 根据消息是否包含原始客户端应用和图ID来判断是入站还是出站消息
        if (originalClientAppUri != null && originalClientGraphId != null) {
            // 这是来自客户端的入站命令消息 (由WebSocketMessageFrameHandler路由到此Extension)
            // 此刻，此ClientConnectionExtension实例已通过onConfigure方法拥有其绑定的客户端上下文。
            // 无需再次从消息属性中提取clientLocationUri和channelId来更新全局映射，
            // 因为该映射在实例配置时已建立或由NettyMessageServer负责维护连接生命周期。

            // 将消息的sourceLocation修改为客户端的真实Location
            message.setSourceLocation(
                    new Location(originalClientAppUri, originalClientGraphId, ClientConnectionExtension.NAME));

            // 清除其目的地，让Engine根据图连接进行路由
            message.setDestinationLocations(null);
            // 重新提交到Engine
            context.sendCommand(message);
            log.debug(
                    "ClientConnectionExtension: 重新提交客户端命令消息到Engine进行图内路由. commandName={}, clientLocationUri={}",
                    message.getName(), this.clientLocationUri); // 使用实例变量进行日志记录
        } else {
            // 这是从其他Extension路由到此的命令消息，意味着需要回传给客户端
            // 直接使用此ClientConnectionExtension实例自身维护的客户端上下文信息
            if (this.clientLocationUri != null && this.channelId != null) {
                Channel targetChannel = engine.getChannel(this.channelId).orElse(null);
                if (targetChannel != null && targetChannel.isActive()) {
                    log.debug(
                            "ClientConnectionExtension: 回传命令消息到客户端Channel. clientLocationUri: {}, channelId: {}, commandName: {}",
                            this.clientLocationUri, this.channelId, message.getName());
                    targetChannel.writeAndFlush(message);
                } else {
                    log.warn(
                            "ClientConnectionExtension: 无法回传命令消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, commandName: {}",
                            this.clientLocationUri, this.channelId, message.getName());
                    removeClientChannelMapping(this.channelId); // 清理失效映射
                }
            } else {
                log.warn(
                        "ClientConnectionExtension: 收到出站命令回传消息，ClientConnectionExtension未配置完整的客户端上下文. commandName: {}",
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
        String originalClientAppUri = message.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String originalClientGraphId = message.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        // 根据消息是否包含原始客户端应用和图ID来判断是入站还是出站消息
        if (originalClientAppUri != null && originalClientGraphId != null) {
            // 这是来自客户端的入站数据消息 (由WebSocketMessageFrameHandler路由到此Extension)
            // 此刻，此ClientConnectionExtension实例已通过onConfigure方法拥有其绑定的客户端上下文。
            // 无需再次从消息属性中提取clientLocationUri和channelId来更新全局映射。

            // 将消息的sourceLocation修改为客户端的真实Location
            message.setSourceLocation(
                    new Location(originalClientAppUri, originalClientGraphId, ClientConnectionExtension.NAME));

            // 清除其目的地，让Engine根据图连接进行路由
            message.setDestinationLocations(null);
            // 重新提交到Engine
            context.sendData(message);
            log.debug(
                    "ClientConnectionExtension: 重新提交客户端数据消息到Engine进行图内路由. name={}, clientLocationUri={}",
                    message.getName(), this.clientLocationUri); // 使用实例变量进行日志记录

        } else {
            // 这是从其他Extension路由到此的数据消息，意味着需要回传给客户端
            // 直接使用此ClientConnectionExtension实例自身维护的客户端上下文信息
            if (this.clientLocationUri != null && this.channelId != null) {
                Channel targetChannel = engine.getChannel(this.channelId).orElse(null);
                if (targetChannel != null && targetChannel.isActive()) {
                    log.debug(
                            "ClientConnectionExtension: 回传数据消息到客户端Channel. clientLocationUri: {}, channelId: {}, messageName: {}",
                            this.clientLocationUri, this.channelId, message.getName());
                    targetChannel.writeAndFlush(message);
                } else {
                    log.warn(
                            "ClientConnectionExtension: 无法回传数据消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, messageName: {}",
                            this.clientLocationUri, this.channelId, message.getName());
                    removeClientChannelMapping(this.channelId); // 清理失效映射
                }
            } else {
                log.warn(
                        "ClientConnectionExtension: 收到出站数据消息，ClientConnectionExtension未配置完整的客户端上下文. messageName: {}",
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
        // 直接使用此ClientConnectionExtension实例自身维护的客户端上下文信息
        if (this.clientLocationUri != null && this.channelId != null) {
            Channel targetChannel = engine.getChannel(this.channelId).orElse(null);
            if (targetChannel != null && targetChannel.isActive()) {
                log.debug(
                        "ClientConnectionExtension: 回传命令结果消息到客户端Channel. clientLocationUri: {}, channelId: {}, commandId: {}",
                        this.clientLocationUri, this.channelId, commandResult.getCommandId());
                targetChannel.writeAndFlush(commandResult);
            } else {
                log.warn(
                        "ClientConnectionExtension: 无法回传命令结果消息，客户端Channel不存在或不活跃. clientLocationUri: {}, channelId: {}, commandId: {}",
                        this.clientLocationUri, this.channelId, commandResult.getCommandId());
                removeClientChannelMapping(this.channelId);
            }
        } else {
            log.warn(
                    "ClientConnectionExtension: 收到不完整的命令结果回传消息，ClientConnectionExtension未配置完整的客户端上下文. commandId: {}",
                    commandResult.getCommandId());
        }
    }

}