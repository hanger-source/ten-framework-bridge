package com.tenframework.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.util.List;

@Slf4j
public class WebSocketMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final ObjectMapper objectMapper;

    public WebSocketMessageDecoder() {
        this.objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return; // 没有可读字节，等待更多数据
        }

        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes); // 读取所有可读字节

        try {
            // 直接将字节数组反序列化为 Message 对象
            Message decodedMessage = objectMapper.readValue(bytes, Message.class);
            out.add(decodedMessage);
            log.debug("WebSocketMessageDecoder: 消息解码成功: type={}, name={}", decodedMessage.getType(),
                    decodedMessage.getName());
        } catch (Exception e) {
            log.error("WebSocketMessageDecoder: 消息解码失败", e);
            // 可以在这里选择关闭连接或丢弃错误消息
            ctx.fireExceptionCaught(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageDecoder encountered an exception", cause);
        ctx.close();
    }
}