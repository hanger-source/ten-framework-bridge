package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 数据消息，对齐C/Python中的数据消息类型。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DataMessage extends Message {

    @JsonProperty("data")
    private byte[] data; // 可以是原始字节数据

    public DataMessage(String id, Location srcLoc, byte[] data) {
        super.setType(MessageType.DATA);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.data = data;
    }

    @Override
    public Object toPayload() {
        return data; // 数据消息的Payload是原始数据
    }
}