package com.tenframework.server.message;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;

import static com.tenframework.core.message.MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG;

@Slf4j
public class WebSocketMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final ObjectMapper objectMapper;

    public WebSocketMessageDecoder() {
        // 使用TenMessagePackMapperProvider获取预配置的ObjectMapper实例
        this.objectMapper = TenMessagePackMapperProvider.getObjectMapper();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return; // 没有可读字节，等待更多数据
        }

        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes); // 读取所有可读字节

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            // 检查是否为扩展类型
            if (unpacker.hasNext() && unpacker.getNextFormat().getValueType() == ValueType.EXTENSION) {
                // 读取扩展类型头部
                org.msgpack.core.ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
                if (extHeader.getType() == TEN_MSGPACK_EXT_TYPE_MSG) {
                    // 读取扩展类型的数据负载
                    byte[] innerBytes = unpacker.readPayload(extHeader.getLength());
                    // 将内部字节数组反序列化为 Message 对象
                    Message decodedMessage = objectMapper.readValue(innerBytes, Message.class);
                    log.debug("WebSocketMessageDecoder: 消息解码成功: type={}, name={}", decodedMessage.getType(),
                            decodedMessage.getName());
                    out.add(decodedMessage);
                } else {
                    log.warn("WebSocketMessageDecoder: 收到未知MsgPack EXT类型: type={}", extHeader.getType());
                }
            } else {
                log.warn("WebSocketMessageDecoder: 接收到非MsgPack EXT格式的消息");
                // 如果不是EXT格式，尝试直接反序列化，但可能会失败，取决于编码器行为
                // 为了健壮性，这里可以直接抛出异常或者进一步检查
                Message decodedMessage = objectMapper.readValue(bytes, Message.class);
                out.add(decodedMessage);
                log.debug("WebSocketMessageDecoder: 消息解码成功 (非EXT格式): type={}, name={}",
                        decodedMessage.getType(),
                        decodedMessage.getName());
            }
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