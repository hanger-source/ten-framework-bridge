package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 命令结果消息，对齐C/Python中的TEN_MSG_TYPE_CMD_RESULT。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CommandResultMessage extends Message {

    @JsonProperty("command_id")
    private String commandId; // 对应原始命令的ID

    @JsonProperty("result_code")
    private int resultCode;

    @JsonProperty("result_message")
    private String resultMessage;

    @JsonProperty("payload")
    private Map<String, Object> payload; // 原始命令的返回数据

    public CommandResultMessage(String id, Location srcLoc, String commandId, int resultCode, String resultMessage, Map<String, Object> payload) {
        super.setType(MessageType.CMD_RESULT);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.commandId = commandId;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.payload = payload;
    }

    @Override
    public Object toPayload() {
        return payload;
    }
}