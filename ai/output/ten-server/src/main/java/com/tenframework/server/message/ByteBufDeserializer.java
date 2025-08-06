package com.tenframework.server.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;

/**
 * 自定义Jackson反序列化器，用于将字节数组反序列化为Netty ByteBuf
 */
public class ByteBufDeserializer extends JsonDeserializer<ByteBuf> {

    @Override
    public ByteBuf deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        byte[] bytes = p.readValueAs(byte[].class);
        if (bytes == null) {
            return null;
        }
        return Unpooled.wrappedBuffer(bytes);
    }
}