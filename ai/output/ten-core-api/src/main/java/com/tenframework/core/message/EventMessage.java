package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 事件消息，对齐C/Python中的事件消息类型。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class EventMessage extends Message {

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    public EventMessage(String id, Location srcLoc, String eventName, Map<String, Object> payload) {
        super.setType(MessageType.EVENT);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.eventName = eventName;
        this.payload = payload;
    }

    @Override
    public Object toPayload() {
        return payload; // 事件消息的Payload是事件数据
    }
}