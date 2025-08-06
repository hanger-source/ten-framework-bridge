package com.tenframework.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        out.add(new BinaryWebSocketFrame(msg.retain())); // Wrap the ByteBuf in a BinaryWebSocketFrame
        log.debug("ByteBufToWebSocketFrameEncoder: Encoded ByteBuf to BinaryWebSocketFrame, size: {}",
                msg.readableBytes());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("ByteBufToWebSocketFrameEncoder encountered an exception", cause);
        ctx.close();
    }
}