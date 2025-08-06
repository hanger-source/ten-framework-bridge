package com.tenframework.core.server;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty Channel Inbound Handler，负责将解码后的TEN消息提交到Engine
 */
@Slf4j
public class EngineChannelInboundHandler extends SimpleChannelInboundHandler<Message> {

    private final Engine engine;

    public EngineChannelInboundHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        log.debug("EngineChannelInboundHandler: 收到消息，提交到Engine. messageType={}, messageName={}",
                msg.getType(), msg.getName());
        // 将消息提交给Engine处理，并带上Channel ID
        boolean success = engine.submitMessage(msg, ctx.channel().id().asShortText());
        if (!success) {
            log.warn("EngineChannelInboundHandler: 提交消息到Engine失败，队列可能已满. messageType={}, messageName={}",
                    msg.getType(), msg.getName());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        engine.addChannel(ctx.channel());
        log.info("Channel已激活并添加到Engine: channelId={}", ctx.channel().id().asShortText());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        engine.removeChannel(ctx.channel().id().asShortText());
        engine.handleChannelDisconnected(ctx.channel().id().asShortText()); // 处理断开连接事件
        log.info("Channel已失活并从Engine移除: channelId={}", ctx.channel().id().asShortText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("EngineChannelInboundHandler: 处理消息时发生异常. channelId={}", ctx.channel().id().asShortText(), cause);
        ctx.close(); // 发生异常时关闭连接
    }
}