package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 所有消息类型的抽象基类，对齐C/Python中的ten_msg_t结构体。
 * 定义了消息的基本属性，并通过Jackson注解支持多态序列化/反序列化。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
                @JsonSubTypes.Type(value = CommandMessage.class, name = "CMD"),
                @JsonSubTypes.Type(value = CommandResultMessage.class, name = "CMD_RESULT"),
                @JsonSubTypes.Type(value = DataMessage.class, name = "DATA"),
                @JsonSubTypes.Type(value = VideoFrameMessage.class, name = "VIDEO_FRAME"),
                @JsonSubTypes.Type(value = AudioFrameMessage.class, name = "AUDIO_FRAME")
})
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段，增加兼容性
@Data
@NoArgsConstructor // 需要默认构造函数供Jackson使用
@Accessors(chain = true) // 允许链式设置
public abstract class Message {

        @JsonProperty("type")
        private MessageType type;

        @JsonProperty("id")
        private String id;

        @JsonProperty("src_loc")
        private Location srcLoc;

        @JsonProperty("dest_locs")
        private List<Location> destLocs;

        @JsonProperty("properties")
        private Map<String, Object> properties;

        /**
         * 获取消息的实际内容（Payload）。
         * 具体实现由子类提供。
         *
         * @return 消息的Payload对象
         */
        public abstract Object toPayload();
}