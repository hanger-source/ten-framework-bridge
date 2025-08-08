package com.tenframework.server.message;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import io.netty.buffer.ByteBuf;
import org.msgpack.core.MessageType;

import static com.tenframework.core.message.MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG;

/**
 * TEN框架消息编码器，将内部Message对象编码为MsgPack格式的字节流
 * 继承Netty的MessageToMessageEncoder，处理Message到BinaryWebSocketFrame的转换
 */
@Slf4j
public class MessageEncoder extends MessageToMessageEncoder<Message> { // 修改泛型类型

    private final ObjectMapper objectMapper;

    public MessageEncoder() {
        // 使用TenMessagePackMapperProvider获取预配置的ObjectMapper实例
        this.objectMapper = TenMessagePackMapperProvider.getObjectMapper();
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception { // 修改方法签名
        if (msg == null) {
            log.warn("尝试编码空消息");
            return;
        }

        if (!msg.checkIntegrity()) {
            log.warn("消息完整性检查失败，跳过编码: {}", msg.toDebugString());
            return;
        }

        try {
            // 1. 将Message对象序列化为普通的MsgPack字节数组
            // Jackson现在应该会根据@JsonTypeInfo和@JsonSubTypes自动添加"type"字段
            byte[] innerBytes = objectMapper.writeValueAsBytes(msg);
            log.debug("MessageEncoder: 图事件消息 '{}' (type: {}) 序列化为 {} 字节", msg.getName(), msg.getType(),
                    innerBytes.length);

            // 2. 将内部MsgPack字节数组封装为自定义的EXT类型
            ExtensionTypeHeader extHeader = new ExtensionTypeHeader(MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG,
                    innerBytes.length);

            // 3. 将EXT类型序列化到MessageBufferPacker
            MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
            packer.packExtensionTypeHeader(extHeader.getType(), extHeader.getLength());
            packer.writePayload(innerBytes);
            packer.close();

            byte[] finalBytes = packer.toByteArray();
            log.debug("MessageEncoder: 最终EXT消息 '{}' 编码为 {} 字节", msg.getName(), finalBytes.length);

            log.debug("消息编码成功: type={}, name={}, originalSize={}, finalSize={} bytes",
                    msg.getType(), msg.getName(), innerBytes.length, finalBytes.length);

            // 将编码后的ByteBuf封装为BinaryWebSocketFrame并添加到out列表
            out.add(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(finalBytes)));
        } catch (Exception e) {
            log.error("消息编码失败: type={}, name={}", msg.getType(), msg.getName(), e);
            throw e;
        }
    }
}