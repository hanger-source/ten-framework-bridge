package com.tenframework.server.message;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * 自定义Jackson序列化器，用于将Netty ByteBuf序列化为字节数组
 */
public class ByteBufSerializer extends JsonSerializer<ByteBuf> {

    @Override
    public void serialize(ByteBuf value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        byte[] bytes = new byte[value.readableBytes()];
        value.getBytes(value.readerIndex(), bytes);
        gen.writeBinary(bytes);
    }
}