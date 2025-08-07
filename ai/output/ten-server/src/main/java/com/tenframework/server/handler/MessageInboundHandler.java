package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageInboundHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(MessageInboundHandler.class);
    private final Engine engine;

    public MessageInboundHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel活跃时，将其添加到Engine的Channel映射中
        engine.addChannel(ctx.channel());
        logger.info("MessageInboundHandler: Channel active, registered with Engine. ChannelId: {}",
                ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel不活跃时，从Engine的Channel映射中移除
        engine.removeChannel(ctx.channel().id().asShortText());
        // 同时处理Engine中的相关PathOut清理
        engine.handleChannelDisconnected(ctx.channel().id().asShortText());
        logger.info("MessageInboundHandler: Channel inactive, unregistered from Engine. ChannelId: {}",
                ctx.channel().id().asShortText());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
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