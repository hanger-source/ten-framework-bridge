package com.tenframework.server.message;

import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;

import static com.tenframework.core.message.MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG;

@Slf4j
public class WebSocketMessageDecoder extends MessageToMessageDecoder<ByteBuf> { // 确保这里是ByteBuf

    private final ObjectMapper objectMapper;

    public WebSocketMessageDecoder() {
        objectMapper = TenMessagePackMapperProvider.getObjectMapper();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return; // 没有可读字节，等待更多数据
        }

        ByteBuffer buffer = in.nioBuffer();
        Message decodedMessage = null;
        // 使用 ByteBufInputStream，避免手动复制到 byte[] 并正确管理 ByteBuf 的生命周期
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(buffer)) {
            MessageFormat format = unpacker.getNextFormat();
            if (format.getValueType() == ValueType.EXTENSION) {
                // 读取扩展类型头部
                ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
                if (extHeader.getType() == TEN_MSGPACK_EXT_TYPE_MSG) {
                    // 读取扩展类型的数据负载
                    byte[] innerBytes = unpacker.readPayload(extHeader.getLength());
                    decodedMessage = objectMapper.readValue(innerBytes, Message.class);
                } else {
                    log.warn("WebSocketMessageDecoder: 收到未知MsgPack EXT类型: type={}", extHeader.getType());
                }
            } else if (format.getValueType() == ValueType.STRING) {
                decodedMessage = objectMapper.readValue(unpacker.unpackString(), Message.class);
            }
            if (decodedMessage != null) {
                out.add(decodedMessage);
                log.debug("WebSocketMessageDecoder: 消息解码成功 (非EXT格式): type={}, name={}",
                    decodedMessage.getType(),
                    decodedMessage.getName());
            }
        } catch (Exception e) {
            log.error("WebSocketMessageDecoder: 消息解码失败", e);
            ctx.fireExceptionCaught(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageDecoder encountered an exception", cause);
        ctx.close();
    }
}