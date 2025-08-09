package com.tenframework.core.message.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 所有命令消息的抽象基类，继承自 Message。
 * 提供命令特有的基本属性和 Jackson 多态序列化/反序列化配置。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name") // 使用 'name' 字段作为类型识别
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartGraphCommand.class, name = "CMD_START_GRAPH"),
        @JsonSubTypes.Type(value = StopGraphCommand.class, name = "CMD_STOP_GRAPH"),
        @JsonSubTypes.Type(value = CloseAppCommand.class, name = "CMD_CLOSE_APP"),
        @JsonSubTypes.Type(value = TimerCommand.class, name = "CMD_TIMER"),
        @JsonSubTypes.Type(value = TimeoutCommand.class, name = "CMD_TIMEOUT"),
        @JsonSubTypes.Type(value = AddExtensionToGraphCommand.class, name = "CMD_ADD_EXTENSION_TO_GRAPH"),
        @JsonSubTypes.Type(value = RemoveExtensionFromGraphCommand.class, name = "CMD_REMOVE_EXTENSION_FROM_GRAPH"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public abstract class Command extends Message {

    @JsonProperty("name")
    protected String name; // 命令名称，用于 Jackson 多态识别

    public Command(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp, String name) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.name = name;
    }

    protected Command(MessageType type, Location srcLoc, List<Location> destLocs, String name) {
        super(type, srcLoc, destLocs);
        this.name = name;
    }
}