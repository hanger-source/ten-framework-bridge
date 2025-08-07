package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.ClientConnectionExtension;
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
    private final ClientConnectionExtension clientConnectionExtension;

    // 添加一个本地映射，用于在channelInactive时清理
    private final Map<String, String> clientChannelIdToLocationUriMap = new ConcurrentHashMap<>();

    public MessageInboundHandler(Engine engine) {
        this.engine = engine;
        this.clientConnectionExtension = engine.getClientConnectionExtension();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Channel激活时，Engine负责管理Channel，ClientConnectionExtension不需要直接注册Channel
        engine.addChannel(ctx.channel()); // 注册Channel到Engine
        logger.info("MessageInboundHandler: Channel active, registered channel: {}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel不活跃时，从Engine中移除此Channel
        String channelId = ctx.channel().id().asShortText();
        engine.removeChannel(channelId); // 移除Channel
        logger.info("MessageInboundHandler: Channel inactive, unregistered channel: {}", channelId);

        // 通知ClientConnectionExtension移除所有与此Channel ID相关的客户端Location URI
        clientConnectionExtension.removeClientChannelMapping(channelId); // 通知ClientConnectionExtension清理映射
        logger.info("MessageInboundHandler: Notified ClientConnectionExtension to remove mappings for channel: {}",
                channelId);

        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        // 首次接收到业务消息时，如果它来自客户端且有源Location，则将客户端信息添加到消息属性中
        if (msg.getSourceLocation() != null &&
                (MessageConstants.APP_URI_TEST_CLIENT.equals(msg.getSourceLocation().appUri()) ||
                        MessageConstants.APP_URI_HTTP_CLIENT.equals(msg.getSourceLocation().appUri()))) {
            String clientLocationUri = msg.getSourceLocation().toString();
            String channelId = ctx.channel().id().asShortText();

            // 将客户端Location URI和Channel ID添加到消息属性中，以便Engine在回传时使用
            // ClientConnectionExtension会在处理入站消息时建立这些映射
            msg.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
            msg.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, channelId);

            logger.debug("MessageInboundHandler: Added client_location_uri [{}] and client_channel_id [{}] to message",
                    clientLocationUri, channelId);

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