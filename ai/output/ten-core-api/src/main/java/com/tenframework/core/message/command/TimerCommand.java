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
 * 定时器命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_TIMER。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_timer/cmd.h
 * (L24-31)
 * ```c
 * typedef struct ten_cmd_timer_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头)
 * ten_value_t timer_id; // int64 // 定时器ID
 * ten_value_t timeout_us; // int64 // 超时时间（微秒）
 * ten_value_t times; // int32 // 重复次数
 * } ten_cmd_timer_t;
 * ```
 *
 * Java 实现中，我们将这些字段直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TimerCommand extends Message {

    /**
     * 定时器ID。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `timer_id` 字段。
     */
    @JsonProperty("timer_id")
    private Long timerId;

    /**
     * 超时时间（微秒）。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `timeout_us` 字段。
     */
    @JsonProperty("timeout_us")
    private Long timeoutUs;

    /**
     * 重复次数。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `times` 字段。
     */
    @JsonProperty("times")
    private Integer times;

    /**
     * 全参构造函数，用于创建定时器命令消息。
     *
     * @param id         消息ID。
     * @param srcLoc     源位置。
     * @param type       消息类型 (应为 CMD_TIMER)。
     * @param destLocs   目的位置。
     * @param properties 消息属性。
     * @param timestamp  消息时间戳。
     * @param timerId    定时器ID。
     * @param timeoutUs  超时时间（微秒）。
     * @param times      重复次数。
     */
    public TimerCommand(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            Long timerId, Long timeoutUs, Integer times) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.timerId = timerId;
        this.timeoutUs = timeoutUs;
        this.times = times;
    }

    /**
     * 用于内部创建的简化构造函数。
     *
     * @param id        消息ID。
     * @param srcLoc    源位置。
     * @param destLocs  目的位置。
     * @param timerId   定时器ID。
     * @param timeoutUs 超时时间（微秒）。
     * @param times     重复次数。
     */
    public TimerCommand(String id, Location srcLoc, List<Location> destLocs, Long timerId, Long timeoutUs,
            Integer times) {
        super(id, MessageType.CMD_TIMER, srcLoc, destLocs);
        this.timerId = timerId;
        this.timeoutUs = timeoutUs;
        this.times = times;
    }

}