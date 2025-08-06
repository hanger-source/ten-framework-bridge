package com.tenframework.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.core.MessagePack;
import org.msgpack.core.ExtensionTypeHeader;

import lombok.extern.slf4j.Slf4j;

/**
 * TEN框架消息编码器，将内部Message对象编码为MsgPack格式的字节流
 * 继承Netty的MessageToByteEncoder，处理Message到ByteBuf的转换
 */
@Slf4j
public class MessageEncoder extends MessageToByteEncoder<Message> {

    private final ObjectMapper objectMapper;

    public MessageEncoder() {
        // 使用MessagePackFactory创建ObjectMapper，用于MsgPack序列化
        this.objectMapper = new ObjectMapper(new MessagePackFactory());

        // 配置ObjectMapper忽略未知属性
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);

        // 注册自定义的ByteBuf序列化器
        SimpleModule byteBufModule = new SimpleModule();
        byteBufModule.addSerializer(ByteBuf.class, new ByteBufSerializer());
        this.objectMapper.registerModule(byteBufModule);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
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
            byte[] innerBytes = objectMapper.writeValueAsBytes(msg);
            log.debug("MessageEncoder: 内部消息 '{}' 序列化为 {} 字节", msg.getName(), innerBytes.length);

            // 2. 将内部MsgPack字节数组封装为自定义的EXT类型
            ExtensionTypeHeader extHeader = new ExtensionTypeHeader(MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG,
                    innerBytes.length);

            // 3. 将EXT类型序列化到ByteBuf
            // 需要使用MessagePackFactory的MessagePacker来写入EXT类型
            MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
            packer.packExtensionTypeHeader(extHeader.getType(), extHeader.getLength());
            packer.writePayload(innerBytes);
            packer.close();

            byte[] finalBytes = packer.toByteArray();
            out.writeBytes(finalBytes);
            log.debug("MessageEncoder: 最终EXT消息 '{}' 编码为 {} 字节", msg.getName(), finalBytes.length);

            log.debug("消息编码成功: type={}, name={}, originalSize={}, finalSize={} bytes",
                    msg.getType(), msg.getName(), innerBytes.length, finalBytes.length);
        } catch (Exception e) {
            log.error("消息编码失败: type={}, name={}", msg.getType(), msg.getName(), e);
            throw e; // 重新抛出异常，由Netty处理
        }
    }
}