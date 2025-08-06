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
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame; // 新增导入

import lombok.extern.slf4j.Slf4j;
import org.msgpack.value.ValueType;

import java.util.List;
import java.util.ArrayList;

/**
 * TEN框架消息解码器，将MsgPack格式的字节流解码为内部Message对象
 * 继承Netty的MessageToMessageDecoder，处理ByteBuf到Message的转换
 */
@Slf4j
public class MessageDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> { // 修改泛型类型

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
    public void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame frame, List<Object> out) throws Exception { // 修改方法签名
        ByteBuf in = frame.content(); // 从BinaryWebSocketFrame中获取ByteBuf
        // 将这个方法改为返回 Message，而不是操作List<Object> out
        // 逻辑保持不变，只是返回方式改变
        Message decodedMessage = null;
        if (!in.isReadable()) {
            return; // 没有可读字节，等待更多数据
        }

        // 将ByteBuf转换为字节数组以便MsgPack处理
        // 注意：这里进行了拷贝，后续可优化为零拷贝
        byte[] bytes = new byte[in.readableBytes()];
        int originalReaderIndex = in.readerIndex(); // 记录原始readerIndex
        in.readBytes(bytes);
        log.debug("MessageDecoder: 收到 {} 字节数据", bytes.length);

        try (
                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            if (!unpacker.hasNext()) {
                log.warn("MsgPack数据不完整或为空");
                in.readerIndex(originalReaderIndex); // 数据不完整，恢复readerIndex
                return; // 只是不完整，不作为错误处理
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
                        decodedMessage = message;
                        log.debug("MessageDecoder: 消息解码成功 (EXT类型): type={}, name={}", message.getType(),
                                message.getName());
                    } else {
                        log.warn("EXT消息解码后完整性检查失败或消息为空");
                    }
                } else {
                    log.warn("未知或不支持的EXT类型: typeCode={}, 跳过此消息", extHeader.getType());
                    // 消费掉未知EXT类型的数据，避免下次重复读取
                    unpacker.skipValue();
                }
            } else {
                log.warn("期望的MsgPack数据应为EXT类型，但实际为: {}", format);
                // 如果不是EXT类型，根据TEN框架设计，这是一个错误情况，不进行后续处理
                // 但是，为了避免无限循环或重复警告，需要消费掉当前的值
                unpacker.skipValue();
            }
        } catch (org.msgpack.core.MessageInsufficientBufferException e) {
            // 数据不足，等待更多数据，不清除out列表，并恢复readerIndex
            log.debug("MessageDecoder: 接收到不完整MsgPack数据，等待更多: {}", e.getMessage());
            in.readerIndex(originalReaderIndex); // 恢复readerIndex以便下次能从头开始读取
            return; // 返回null，表示当前无法解码出完整消息
        } catch (Exception e) {
            log.error("消息解码失败，可能MsgPack格式错误或消息结构不匹配", e);
            // 捕获异常并记录，不将异常传播到管道
            // 这里的in.readerIndex() 不应该恢复，因为已经尝试消费，应该前进
            // Netty的MessageToMessageDecoder会确保in被正确消费
            return; // 返回null，表示解码失败
        } finally {
            // 如果在try块中没有返回，或者异常被捕获，并且成功解码出消息，则添加到out列表
            if (decodedMessage != null) {
                out.add(decodedMessage);
            }
        }
    }

    // 提供一个直接解码ByteBuf的方法，用于WebSocketMessageFrameHandler或其他非管道场景
    // 这个方法现在不需要了，因为我们直接处理 BinaryWebSocketFrame
    // public Message decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception
    // {
    // List<Object> tempOut = new ArrayList<>();
    // decode(ctx, in, tempOut); // 调用原始decode方法
    // return tempOut.isEmpty() ? null : (Message) tempOut.get(0);
    // }
}