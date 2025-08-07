package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageInboundHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(MessageInboundHandler.class);
    private final Engine engine;

    // 添加一个本地映射，用于在channelInactive时清理
    private final Map<String, String> clientChannelIdToLocationUriMap = new ConcurrentHashMap<>();

    public MessageInboundHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Channel激活时，注册到Engine的channelMap，以便Engine能够向其发送消息
        engine.addChannel(ctx.channel()); // 注册Channel
        logger.info("MessageInboundHandler: Channel active, registered channel: {}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel不活跃时，从Engine中移除此Channel
        engine.removeChannel(ctx.channel().id().asShortText()); // 移除Channel
        logger.info("MessageInboundHandler: Channel inactive, unregistered channel: {}",
                ctx.channel().id().asShortText());

        // 同时，从Engine的clientLocationUriToChannelIdMap中移除所有与此Channel ID相关的客户端Location URI
        // 遍历本地映射，找到与当前Channel ID关联的Location URI并注销
        List<String> locationsToUnregister = new ArrayList<>();
        clientChannelIdToLocationUriMap.forEach((channelId, locationUri) -> {
            if (channelId.equals(ctx.channel().id().asShortText())) {
                locationsToUnregister.add(locationUri);
            }
        });
        locationsToUnregister.forEach(engine::unregisterClientLocationToChannel);
        locationsToUnregister.forEach(clientChannelIdToLocationUriMap::remove); // 从本地映射中移除
        logger.info("MessageInboundHandler: Unregistered {} client locations associated with channel: {}",
                locationsToUnregister.size(), ctx.channel().id().asShortText());

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        // 首次接收到业务消息时，如果它来自客户端且有源Location，则注册客户端Channel
        if (msg.getSourceLocation() != null &&
                (MessageConstants.APP_URI_TEST_CLIENT.equals(msg.getSourceLocation().appUri()) ||
                        MessageConstants.APP_URI_HTTP_CLIENT.equals(msg.getSourceLocation().appUri()))) {
            String clientLocationUri = msg.getSourceLocation().toString();
            String serverChannelId = ctx.channel().id().asShortText();

            // 注册客户端Location URI到服务器端Channel ID的映射
            engine.registerClientLocationToChannel(clientLocationUri, serverChannelId);
            // 维护本地映射，用于在channelInactive时清理
            clientChannelIdToLocationUriMap.put(serverChannelId, clientLocationUri);

            logger.info("MessageInboundHandler: Registered client location URI [{}] to server channel ID [{}]",
                    clientLocationUri, serverChannelId);

            // 将客户端Location URI添加到消息属性中，以便Engine在回传时使用
            if (msg.getProperties() == null) {
                msg.setProperties(new java.util.HashMap<>());
            }
            // 不再添加 __client_channel_id__，而是添加 __client_location_uri__
            msg.getProperties().put(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
            logger.debug("MessageInboundHandler: Added __client_location_uri__ to message: {}", clientLocationUri);

        }

        logger.info("MessageInboundHandler: Received Message - Type: {}, Name: {}", msg.getType(),
                msg.getName());
        // Forward the message to the engine for processing
        engine.submitMessage(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("MessageInboundHandler: Exception caught in message inbound handler", cause);
        ctx.close();
    }
}