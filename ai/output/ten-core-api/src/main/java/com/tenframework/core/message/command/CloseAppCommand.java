package com.tenframework.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 关闭应用命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_CLOSE_APP。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_close_app/cmd.h
 * (L24-26)
 * ```c
 * typedef struct ten_cmd_close_app_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头，对应 CommandMessage 基类字段)
 * } ten_cmd_close_app_t;
 * ```
 *
 * **重要提示：**
 * - C端 `ten_cmd_close_app_t` 结构体中**没有**额外的硬编码字段。
 * 其语义完全由 `TEN_MSG_TYPE_CMD_CLOSE_APP` 消息类型和 `CMD_CLOSE_APP` 命令名称定义。
 * - 因此，本Java类无需定义额外的字段，也不需要自定义Jackson序列化器/反序列化器。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CloseAppCommand extends Message {

    /**
     * 全参构造函数，用于创建关闭应用命令消息。
     *
     * @param id         消息ID，对应C端 `ten_msg_t.name`。
     * @param srcLoc     源位置，对应C端 `ten_msg_t.src_loc`。
     * @param timestamp  消息时间戳，对应C端 `ten_msg_t.timestamp`。
     * @param destLocs   目的位置，对应C端 `ten_msg_t.dest_locs`。
     * @param properties 消息属性，对应C端 `ten_msg_t.properties`。
     */
    public CloseAppCommand(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
    }

    /**
     * 用于内部创建的简化构造函数。
     * 
     * @param srcLoc 源位置。
     */
    public CloseAppCommand(Location srcLoc) {
        super(MessageType.CMD_CLOSE_APP, srcLoc, Collections.emptyList());
    }

}