package com.tenframework.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageUtils;
import org.msgpack.value.ValueType;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * TEN框架消息解码器，将MsgPack格式的字节流解码为内部Message对象
 * 继承Netty的MessageToMessageDecoder，处理BinaryWebSocketFrame到Message的转换
 */
@Slf4j
public class MessageDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    private final ObjectMapper objectMapper;

    public MessageDecoder() {
        this.objectMapper = new ObjectMapper(new MessagePackFactory());
        // 配置ObjectMapper忽略未知属性
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        // 注册自定义的ByteBuf反序列化器
        SimpleModule byteBufModule = new SimpleModule();
        byteBufModule.addDeserializer(ByteBuf.class, new ByteBufDeserializer()); // 这里的ByteBufDeserializer是自定义的，需要确保它在同一包或已导入
        this.objectMapper.registerModule(byteBufModule);
    }

    @Override
    public void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (!(frame instanceof BinaryWebSocketFrame)) {
            log.warn("MessageDecoder只处理BinaryWebSocketFrame，收到: {}", frame.getClass().getName());
            return; // 或抛出UnsupportedOperationException
        }

        ByteBuf msg = ((BinaryWebSocketFrame) frame).content();

        if (msg == null || !msg.isReadable()) {
            log.warn("尝试解码空或不可读的ByteBuf");
            return;
        }

        try {
            // 将ByteBuf转换为字节数组
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);

            // 1. 使用MessagePack Unpacker解析外部EXT类型
            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes);
            MessageFormat format = unpacker.getNextFormat();

            if (format.getValueType() != ValueType.EXTENSION) {
                log.warn("期望MsgPack EXT类型，但收到: {}", format);
                unpacker.skipValue(); // 添加这行，跳过当前值
                return;
            }

            ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
            if (extHeader.getType() != MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG) { // 使用MessageUtils常量
                log.warn("期望TEN自定义EXT类型 ({}), 但收到: {}", MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG, extHeader.getType());
                return;
            }

            // 2. 提取内部消息的字节数组
            byte[] innerBytes = unpacker.readPayload(extHeader.getLength());
            unpacker.close();

            // 3. 将内部字节数组反序列化为Message对象
            Message message = objectMapper.readValue(innerBytes, Message.class); // 这里的Message需要导入

            if (message != null) {
                log.debug("消息解码成功: type={}, name={}, originalSize={}, finalSize={} bytes",
                        message.getType(), message.getName(), bytes.length, innerBytes.length);
                out.add(message); // 将解码后的Message对象添加到out列表
            } else {
                log.warn("解码Message对象为空");
            }
        } catch (org.msgpack.core.MessageInsufficientBufferException e) {
            log.warn("收到不完整MsgPack数据，等待更多: {}", e.getMessage());
            // 在MessageToMessageDecoder中，返回null表示需要更多数据
            return;
        } catch (Exception e) {
            log.error("消息解码失败: {}", e.getMessage(), e);
            throw e; // 重新抛出其他异常
        }
    }
}