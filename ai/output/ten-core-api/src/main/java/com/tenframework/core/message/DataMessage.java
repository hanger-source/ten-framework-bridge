package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tenframework.core.message.serializer.DataMessageDeserializer;
import com.tenframework.core.message.serializer.DataMessageSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 数据消息，对齐C/Python中的TEN_MSG_TYPE_DATA。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/data/data.h (L18-23)
 * ```c
 * typedef struct ten_data_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t data; // buf // (实际数据内容)
 * } ten_data_t;
 * ```
 *
 * **重要提示：**
 * - C端 `ten_data_t` 中的 `data` 字段，在C端序列化时（通过 `ten_raw_data_loop_all_fields` 函数，见
 * `core/src/ten_runtime/msg/data/data.c` 大约L100），
 * 会被放入 `ten_msg_t` 的 `properties` 字段下的 `"ten"` 子对象中，即序列化路径为
 * `properties.ten.data`。
 * - 为了确保与C端协议的互操作性，并避免在Java类结构中引入C端序列化细节（即不使用 `DataMessageTenProperties` 嵌套类），
 * **需要为 `DataMessage` 实现自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。**
 * 这些自定义序列化器将负责在序列化时将Java `data` 字段正确地映射到 `properties.ten.data`，
 * 并在反序列化时从 `properties.ten.data` 中读取数据并设置到Java `data` 字段。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
@JsonSerialize(using = DataMessageSerializer.class)
@JsonDeserialize(using = DataMessageDeserializer.class)
public class DataMessage extends Message {

    /**
     * 实际的数据内容。
     * 对应C端 `ten_data_t` 结构体中的 `data` 字段。
     * C类型: `ten_value_t` (内部为 `buf`，即字节缓冲区)
     * C源码定义: `core/include_internal/ten_runtime/msg/data/data.h` (L22)
     * C源码序列化处理: `core/src/ten_runtime/msg/data/data.c` (约L100,
     * `ten_raw_data_loop_all_fields` 函数)
     */
    @JsonProperty("data") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化为 properties.ten.data 需要自定义序列化器。
    private byte[] data;

    /**
     * 全参构造函数，用于创建数据消息。
     *
     * @param id        消息ID，对应C端 `ten_msg_t.name`。
     * @param srcLoc    源位置，对应C端 `ten_msg_t.src_loc`。
     * @param timestamp 消息时间戳，对应C端 `ten_msg_t.timestamp`。
     * @param data      实际数据内容，对应C端 `ten_data_t.data`。
     */
    public DataMessage(String id, Location srcLoc, long timestamp, byte[] data) {
        // 对于数据消息，基类的properties字段保持为空Map，因为其特定数据通过data字段承载
        super(id, srcLoc, MessageType.DATA, Collections.emptyList(), Collections.emptyMap(), timestamp);
        this.data = data;
    }

    /**
     * 获取数据大小（字节数）。
     */
    public int getDataSize() {
        return data != null ? data.length : 0;
    }

    /**
     * 获取数据字节数组的拷贝。
     */
    public byte[] getDataBytes() {
        return data != null ? data.copyOf(data.length) : new byte[0]; // 使用 copyOf 进行深拷贝
    }

    /**
     * 检查是否有实际数据。
     */
    public boolean hasData() {
        return data != null && data.length > 0;
    }

    /**
     * 检查数据是否为空。
     */
    public boolean isEmpty() {
        return !hasData();
    }

    @Override
    public boolean checkIntegrity() {
        // 对于 DataMessage，需要确保基本消息完整性以及数据内容非空
        return super.checkIntegrity() &&
                data != null; // data不能为null
    }

    @Override
    public DataMessage clone() {
        return new DataMessage(this.getId(), this.getSrcLoc(), this.getTimestamp(), this.getDataBytes());
    }

    @Override
    public String toDebugString() {
        return String.format("DataMessage[id=%s, size=%d bytes, src=%s, dest=%d]",
                getId(),
                getDataSize(),
                getSrcLoc() != null ? getSrcLoc().toDebugString() : "null",
                getDestLocs() != null ? getDestLocs().size() : 0);
    }

    @Override
    public Object toPayload() {
        return null; // 有效载荷现在通过DataMessage的data字段处理（结合自定义序列化器实现）
    }
}