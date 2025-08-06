package com.tenframework.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.ExtensionTypeHeader;

import lombok.extern.slf4j.Slf4j;
import org.msgpack.value.ValueType;

import java.util.List;

/**
 * TEN框架消息解码器，将MsgPack格式的字节流解码为内部Message对象
 * 继承Netty的MessageToMessageDecoder，处理ByteBuf到Message的转换
 */
@Slf4j
public class MessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final ObjectMapper objectMapper;

    public MessageDecoder() {
        // 使用MessagePackFactory创建ObjectMapper，用于MsgPack反序列化
        this.objectMapper = new ObjectMapper(new MessagePackFactory());

        // 配置ObjectMapper忽略未知属性
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);

        // 注册自定义的ByteBuf反序列化器
        SimpleModule byteBufModule = new SimpleModule();
        byteBufModule.addDeserializer(ByteBuf.class, new ByteBufDeserializer());
        this.objectMapper.registerModule(byteBufModule);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return; // 没有可读字节，等待更多数据
        }

        // 将ByteBuf转换为字节数组以便MsgPack处理
        // 注意：这里进行了拷贝，后续可优化为零拷贝
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        log.debug("MessageDecoder: 收到 {} 字节数据", bytes.length);

        try (
                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            if (!unpacker.hasNext()) {
                log.warn("MsgPack数据不完整或为空");
                return;
            }

            // 读取下一个MsgPack格式
            MessageFormat format = unpacker.getNextFormat();
            log.debug("MessageDecoder: 解包到MsgPack格式: {}", format);

            if (format.getValueType() == ValueType.EXTENSION) {
                // 是EXT类型，进一步处理
                ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
                log.debug("MessageDecoder: 发现EXT类型，typeCode={}, length={}", extHeader.getType(), extHeader.getLength());

                if (extHeader.getType() == MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG) {
                    // 匹配TEN框架的自定义EXT类型
                    byte[] innerBytes = unpacker.readPayload(extHeader.getLength());
                    log.debug("MessageDecoder: 提取内部消息负载 {} 字节", innerBytes.length);

                    // 进行二次反序列化，将EXT内部的数据解码为Message对象
                    Message message = objectMapper.readValue(innerBytes, Message.class);

                    if (message != null && message.checkIntegrity()) {
                        out.add(message);
                        log.debug("MessageDecoder: 消息解码成功 (EXT类型): type={}, name={}", message.getType(),
                                message.getName());
                    } else {
                        log.warn("EXT消息解码后完整性检查失败或消息为空");
                    }
                } else {
                    log.warn("未知或不支持的EXT类型: typeCode={}, 跳过此消息", extHeader.getType());
                }
            } else {
                log.warn("期望的MsgPack数据应为EXT类型，但实际为: {}", format);
                // 如果不是EXT类型，根据TEN框架设计，这是一个错误情况，不进行后续处理
            }
        } catch (Exception e) {
            log.error("消息解码失败，可能MsgPack格式错误或消息结构不匹配", e);
            // 捕获异常并记录，不将异常传播到管道，丢弃当前帧，避免影响后续消息处理
            out.clear();
        }
    }
}