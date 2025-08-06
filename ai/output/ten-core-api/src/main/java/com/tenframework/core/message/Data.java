package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 数据消息类
 * 代表实时信息载荷和数据管道传输
 * 对应C语言中的ten_data_t结构
 */
@lombok.Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class Data extends AbstractMessage {

    @JsonProperty("data")
    private ByteBuf data;

    @JsonProperty("is_eof")
    private boolean isEof = false;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("encoding")
    private String encoding;

    /**
     * 默认构造函数
     */
    public Data() {
        super();
        this.data = Unpooled.EMPTY_BUFFER;
    }

    /**
     * 创建数据消息的构造函数
     */
    public Data(String name) {
        this();
        setName(name);
    }

    /**
     * 创建数据消息的构造函数，带数据
     */
    public Data(String name, ByteBuf data) {
        this(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
    }

    /**
     * 创建数据消息的构造函数，带字节数组数据
     */
    public Data(String name, byte[] data) {
        this(name);
        this.data = data != null ? Unpooled.wrappedBuffer(data) : Unpooled.EMPTY_BUFFER;
    }

    /**
     * JSON反序列化构造函数
     */
    @JsonCreator
    public Data(
            @JsonProperty("name") String name,
            @JsonProperty("data") ByteBuf data,
            @JsonProperty("is_eof") Boolean isEof,
            @JsonProperty("content_type") String contentType,
            @JsonProperty("encoding") String encoding) {
        super();
        setName(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
        this.isEof = isEof != null ? isEof : false;
        this.contentType = contentType;
        this.encoding = encoding;
    }

    /**
     * 拷贝构造函数
     */
    private Data(com.tenframework.core.message.Data other) {
        super(other);
        // 深拷贝ByteBuf数据
        this.data = other.data != null ? other.data.copy() : Unpooled.EMPTY_BUFFER;
        this.isEof = other.isEof;
        this.contentType = other.contentType;
        this.encoding = other.encoding;
    }

    @Override
    public MessageType getType() {
        return MessageType.DATA;
    }

    /**
     * 获取数据大小（字节数）
     */
    public int getDataSize() {
        return data != null ? data.readableBytes() : 0;
    }

    /**
     * 获取数据的字节数组拷贝 - 使用现代Java特性
     */
    public byte[] getDataBytes() {
        return Optional.ofNullable(data)
                .filter(ByteBuf::isReadable)
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), bytes);
                    return bytes;
                })
                .orElse(new byte[0]);
    }

    /**
     * 设置数据（字节数组）- 使用现代Java特性
     */
    public void setDataBytes(byte[] bytes) {
        Optional.ofNullable(data)
                .filter(buf -> buf.refCnt() > 0)
                .ifPresent(ByteBuf::release); // 释放原有的ByteBuf

        this.data = Optional.ofNullable(bytes)
                .map(Unpooled::wrappedBuffer)
                .orElse(Unpooled.EMPTY_BUFFER);
    }

    /**
     * 检查是否有数据
     */
    public boolean hasData() {
        return data != null && data.isReadable();
    }

    /**
     * 检查是否为空数据
     */
    public boolean isEmpty() {
        return !hasData();
    }

    /**
     * 创建文本数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data text(String name, String text) {
        com.tenframework.core.message.Data data = new com.tenframework.core.message.Data(name);
        if (text != null) {
            data.setDataBytes(text.getBytes());
            data.setContentType("text/plain");
            data.setEncoding("UTF-8");
        }
        return data;
    }

    /**
     * 创建JSON数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data json(String name, String json) {
        com.tenframework.core.message.Data data = new com.tenframework.core.message.Data(name);
        if (json != null) {
            data.setDataBytes(json.getBytes());
            data.setContentType("application/json");
            data.setEncoding("UTF-8");
        }
        return data;
    }

    /**
     * 创建二进制数据的静态工厂方法
     */
    public static com.tenframework.core.message.Data binary(String name, byte[] bytes) {
        com.tenframework.core.message.Data data = new com.tenframework.core.message.Data(name, bytes);
        data.setContentType("application/octet-stream");
        return data;
    }

    /**
     * 创建EOF标记数据
     */
    public static com.tenframework.core.message.Data eof(String name) {
        com.tenframework.core.message.Data data = new com.tenframework.core.message.Data(name);
        data.setEof(true);
        return data;
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(getName(), "数据消息名称") &&
                Optional.ofNullable(data).isPresent();
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

    /**
     * 资源清理 - 释放ByteBuf
     */
    public void release() {
        if (data != null && data.refCnt() > 0) {
            data.release();
        }
    }

    /**
     * 实现AutoCloseable接口，支持try-with-resources
     */
    public void close() {
        release();
    }
}