package com.tenframework.core.message;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tenframework.core.Location;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
// import java.util.Arrays; // 移除导入，不再需要

/**
 * 数据消息类
 * 代表实时信息载荷和数据管道传输
 * 对应C语言中的ten_data_t结构
 */
@lombok.Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class Data extends AbstractMessage {

    // 将data字段从byte[]改回ByteBuf，并恢复Jackson自定义序列化/反序列化注解
    @JsonProperty("data")
    @JsonSerialize(using = ByteBufSerializer.class)
    @JsonDeserialize(using = ByteBufDeserializer.class)
    private ByteBuf data;

    @JsonProperty("is_eof")
    private boolean isEof;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("encoding")
    private String encoding;

    /**
     * 默认构造函数（用于Jackson反序列化，如果JsonCreator未完全覆盖所有场景）
     * 内部使用，不推荐直接调用
     */
    protected Data() {
        super();
        data = Unpooled.EMPTY_BUFFER; // 初始化为空ByteBuf
        isEof = false;
    }

    /**
     * JSON反序列化和Builder使用的全参数构造函数
     */
    @Builder
    @JsonCreator
    public Data(
            @JsonProperty("name") String name,
        @JsonProperty("data") ByteBuf data, // 参数类型改为ByteBuf
            @JsonProperty("is_eof") Boolean isEof,
            @JsonProperty("content_type") String contentType,
            @JsonProperty("encoding") String encoding,
            @JsonProperty("source_location") Location sourceLocation,
            @JsonProperty("destination_locations") List<Location> destinationLocations,
            @JsonProperty("properties") Map<String, Object> properties, // 继承自AbstractMessage
            @JsonProperty("timestamp") Long timestamp // 继承自AbstractMessage
    ) {
        super(sourceLocation, destinationLocations);
        setName(name);
        setProperties(properties);
        if (timestamp != null) {
            setTimestamp(timestamp);
        }
        this.data = data != null ? data.retain() : Unpooled.EMPTY_BUFFER; // 赋值ByteBuf，并调用retain()
        this.isEof = isEof != null ? isEof : false;
        this.contentType = contentType;
        this.encoding = encoding;
    }

    /**
     * 拷贝构造函数
     */
    private Data(com.tenframework.core.message.Data other) {
        super(other);
        // 深拷贝ByteBuf，确保引用计数正确
        data = other.data != null ? other.data.retainedDuplicate() : Unpooled.EMPTY_BUFFER;
        isEof = other.isEof;
        contentType = other.contentType;
        encoding = other.encoding;
    }

    /**
     * 创建文本数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data text(String name, String text) {
        DataBuilder builder = Data.builder()
                .name(name)
                .contentType("text/plain")
                .encoding("UTF-8");
        if (text != null) {
            builder.data(Unpooled.copiedBuffer(text, java.nio.charset.StandardCharsets.UTF_8)); // 从字符串创建ByteBuf
        }
        return builder.build();
    }

    /**
     * 创建JSON数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data json(String name, String json) {
        DataBuilder builder = Data.builder()
                .name(name)
                .contentType("application/json")
                .encoding("UTF-8");
        if (json != null) {
            builder.data(Unpooled.copiedBuffer(json, java.nio.charset.StandardCharsets.UTF_8)); // 从字符串创建ByteBuf
        }
        return builder.build();
    }

    /**
     * 创建二进制数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data binary(String name, byte[] bytes) {
        DataBuilder builder = Data.builder()
                .name(name)
                .contentType("application/octet-stream");
        if (bytes != null) {
            builder.data(Unpooled.copiedBuffer(bytes)); // 从字节数组创建ByteBuf
        }
        return builder.build();
    }

    /**
     * 创建二进制数据的静态工厂方法 (接受ByteBuf)
     */
    public static com.tenframework.core.message.Data binary(String name, ByteBuf buffer) {
        DataBuilder builder = Data.builder()
            .name(name)
            .contentType("application/octet-stream");
        if (buffer != null) {
            builder.data(buffer.retain()); // 接受ByteBuf并调用retain()
        }
        return builder.build();
    }

    /**
     * 创建EOF标记数据
     */
    public static com.tenframework.core.message.Data eof(String name) {
        return Data.builder()
                .name(name)
                .isEof(true)
                .build();
    }

    @Override
    public MessageType getType() {
        return MessageType.DATA;
    }

    /**
     * 获取数据大小（字节数）
     */
    @JsonIgnore
    public int getDataSize() {
        return data != null ? data.readableBytes() : 0; // 使用ByteBuf的readableBytes()
    }

    /**
     * 获取数据的字节数组拷贝 - 使用现代Java特性
     */
    public byte[] getDataBytes() {
        if (data == null || data.readableBytes() == 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), bytes); // 从ByteBuf读取字节到数组
        return bytes;
    }

    /**
     * 设置数据（字节数组）- 使用现代Java特性
     */
    public void setDataBytes(byte[] bytes) {
        if (data != null) {
            data.release(); // 释放旧的ByteBuf
        }
        data = bytes != null ? Unpooled.copiedBuffer(bytes) : Unpooled.EMPTY_BUFFER; // 从字节数组创建ByteBuf
    }

    /**
     * 获取数据的ByteBuf
     * 注意：返回的ByteBuf需要使用者负责release
     *
     * @return 数据的ByteBuf
     */
    public ByteBuf getDataBuffer() {
        return data != null ? data.retain() : Unpooled.EMPTY_BUFFER;
    }

    /**
     * 设置数据（ByteBuf）
     * 注意：传入的ByteBuf所有权转移给此对象，此对象会负责release
     *
     * @param buffer 数据的ByteBuf
     */
    public void setDataBuffer(ByteBuf buffer) {
        if (data != null) {
            data.release(); // 释放旧的ByteBuf
        }
        data = buffer != null ? buffer.retain() : Unpooled.EMPTY_BUFFER; // 赋值新的ByteBuf，并调用retain()
    }

    /**
     * 检查是否有数据
     */
    public boolean hasData() {
        return data != null && data.readableBytes() > 0; // 检查ByteBuf的可读字节数
    }

    /**
     * 检查是否为空数据
     */
    @JsonIgnore
    public boolean isEmpty() {
        return !hasData();
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
            MessageUtils.validateStringField(getName(), "数据消息名称"); // 移除对data的检查
    }

    @Override
    public com.tenframework.core.message.Data clone() {
        return new com.tenframework.core.message.Data(this);
    }

    @Override
    public String toDebugString() {
        return String.format("Data[name=%s, size=%d bytes, eof=%s, contentType=%s, src=%s, dest=%s]",
                getName(),
                getDataSize(),
                isEof,
                contentType,
                getSourceLocation(),
                getDestinationLocations().size());
    }

    // 增加ByteBuf相关的资源清理方法
    /**
     * 释放此Data对象持有的ByteBuf资源。
     * 在不再需要此Data对象时，应调用此方法以避免内存泄漏。
     */
    public void release() {
        if (data != null) {
            data.release();
            data = null; // 释放后置为null，避免重复释放
        }
    }

    /**
     * 实现AutoCloseable接口，使其可以在try-with-resources语句中使用。
     */
    public void close() {
        release();
    }
}