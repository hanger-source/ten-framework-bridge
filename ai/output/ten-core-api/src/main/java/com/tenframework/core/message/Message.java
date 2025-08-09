package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 所有消息类型的抽象基类，对齐C/Python中的ten_msg_t结构体。
 * 定义了消息的基本属性，并通过Jackson注解支持多态序列化/反序列化。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
                @JsonSubTypes.Type(value = CommandResult.class, name = "CMD_RESULT"),
                @JsonSubTypes.Type(value = DataMessage.class, name = "DATA_MESSAGE"),
                @JsonSubTypes.Type(value = VideoFrameMessage.class, name = "VIDEO_FRAME"),
                @JsonSubTypes.Type(value = AudioFrameMessage.class, name = "AUDIO_FRAME"),
                @JsonSubTypes.Type(value = CloseAppCommand.class, name = "CMD_CLOSE_APP"),
                @JsonSubTypes.Type(value = StartGraphCommand.class, name = "CMD_START_GRAPH"),
                @JsonSubTypes.Type(value = StopGraphCommand.class, name = "CMD_STOP_GRAPH"),
                @JsonSubTypes.Type(value = TimerCommand.class, name = "CMD_TIMER"),
                @JsonSubTypes.Type(value = TimeoutCommand.class, name = "CMD_TIMEOUT"),
                @JsonSubTypes.Type(value = AddExtensionToGraphCommand.class, name = "CMD_ADD_EXTENSION_TO_GRAPH"),
                @JsonSubTypes.Type(value = RemoveExtensionFromGraphCommand.class, name = "CMD_REMOVE_EXTENSION_FROM_GRAPH"),
})
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段，增加兼容性
@Data // Lombok 注解
@NoArgsConstructor // 需要默认构造函数供Jackson使用
@Accessors(chain = true) // 允许链式设置
public abstract class Message {

        @JsonProperty("type")
        protected MessageType type;

        @JsonProperty("id")
        protected String id;

        @JsonProperty("src_loc")
        protected Location srcLoc;

        @JsonProperty("dest_locs")
        protected List<Location> destLocs;

        @JsonProperty("properties")
        protected Map<String, Object> properties;

        @JsonProperty("timestamp")
        protected long timestamp;

        // 全参构造函数，方便子类调用
        public Message(String id, Location srcLoc, MessageType type, List<Location> destLocs,
                        Map<String, Object> properties, long timestamp) {
                this.id = id;
                this.srcLoc = srcLoc;
                this.type = type;
                this.destLocs = destLocs;
                this.properties = properties;
                this.timestamp = timestamp;
        }

        // 保护性构造函数，供 Lombok @NoArgsConstructor 之外的初始化使用
        protected Message(MessageType type, Location srcLoc, List<Location> destLocs) {
                this.type = type;
                this.srcLoc = srcLoc;
                this.destLocs = destLocs;
                this.id = UUID.randomUUID().toString(); // 生成 UUID
                this.properties = new HashMap<>(); // 初始化 properties Map
                this.timestamp = System.currentTimeMillis(); // 设置时间戳
        }

        // Getters (Lombok 会生成，但为了清晰性，这里保留部分关键 Getter)
        public String getId() {
                return id;
        }

        public MessageType getType() {
                return type;
        }

        public Location getSrcLoc() {
                return srcLoc;
        }

        public List<Location> getDestLocs() {
                return destLocs;
        }

        public Map<String, Object> getProperties() {
                return properties;
        }

        public long getTimestamp() {
                return timestamp;
        }

        // Setters (Lombok 会生成，但为了清晰性，这里保留部分关键 Setter)
        public void setProperties(Map<String, Object> properties) {
                this.properties = properties;
        }

        // 抽象方法 toPayload() 将不再需要，因为 payload 直接作为 Message 的属性
        // 如果需要，可以在这里定义一个获取 payload 的方法，例如 getPayloadContent()
}