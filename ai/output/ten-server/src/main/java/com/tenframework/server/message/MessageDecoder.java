package com.tenframework.server.message;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;
import com.tenframework.server.message.TenMessagePackMapperProvider;

/**
 * TEN框架消息解码器，将MsgPack格式的字节流解码为内部Message对象
 * 继承Netty的MessageToMessageDecoder，处理BinaryWebSocketFrame到Message的转换
 */
@Slf4j
public class MessageDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    private final ObjectMapper objectMapper;

    public MessageDecoder() {
        this.objectMapper = TenMessagePackMapperProvider.getObjectMapper();
    }

    @Override
    public void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (!(frame instanceof BinaryWebSocketFrame)) {
            log.warn("MessageDecoder只处理BinaryWebSocketFrame，收到: {}", frame.getClass().getName());
            return;
        }

        ByteBuf msg = frame.content();

        if (msg == null || !msg.isReadable()) {
            log.warn("尝试解码空或不可读的ByteBuf");
            return;
        }

        try {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);

            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes);
            MessageFormat format = unpacker.getNextFormat();

            if (format.getValueType() != ValueType.EXTENSION) {
                log.warn("期望MsgPack EXT类型，但收到: {}", format);
                unpacker.skipValue();
                return;
            }

            ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
            if (extHeader.getType() != MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG) {
                log.warn("期望TEN自定义EXT类型 ({}), 但收到: {}", MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG, extHeader.getType());
                return;
            }

            byte[] innerBytes = unpacker.readPayload(extHeader.getLength());
            unpacker.close();

            Message message = objectMapper.readValue(innerBytes, Message.class);

            if (message != null) {
                log.debug("消息解码成功: type={}, name={}, originalSize={}, finalSize={} bytes",
                        message.getType(), message.getName(), bytes.length, innerBytes.length);
                out.add(message);
            } else {
                log.warn("解码Message对象为空");
            }
        } catch (org.msgpack.core.MessageInsufficientBufferException e) {
            log.warn("收到不完整MsgPack数据，等待更多: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("消息解码失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("MessageDecoder encountered an exception", cause);
        ctx.close();
    }
}