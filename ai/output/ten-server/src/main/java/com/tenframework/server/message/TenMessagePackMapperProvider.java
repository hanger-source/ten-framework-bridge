package com.tenframework.server.message;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.tenframework.core.message.ByteBufDeserializer;
import com.tenframework.core.message.ByteBufSerializer;
import io.netty.buffer.ByteBuf;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * 提供预配置的ObjectMapper实例，用于MessagePack序列化和反序列化。
 * 确保ByteBuf的自定义序列化器和反序列化器被正确注册。
 */
public final class TenMessagePackMapperProvider {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());

        // 配置ObjectMapper忽略未知属性
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 注册自定义的ByteBuf序列化器和反序列化器
        SimpleModule byteBufModule = new SimpleModule();
        byteBufModule.addDeserializer(ByteBuf.class, new ByteBufDeserializer());
        byteBufModule.addSerializer(ByteBuf.class, new ByteBufSerializer());
        OBJECT_MAPPER.registerModule(byteBufModule);
    }

    private TenMessagePackMapperProvider() {
        // 私有构造函数，防止实例化
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}