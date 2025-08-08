package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 命令消息，对齐C/Python中的命令消息类型。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CommandMessage extends Message {

    @JsonProperty("command_name")
    private String commandName;

    @JsonProperty("args")
    private Map<String, Object> args;

    public CommandMessage(String id, Location srcLoc, String commandName, Map<String, Object> args) {
        super.setType(MessageType.CMD);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.commandName = commandName;
        this.args = args;
    }

    @Override
    public Object toPayload() {
        return args; // 命令消息的Payload通常是其参数
    }
}