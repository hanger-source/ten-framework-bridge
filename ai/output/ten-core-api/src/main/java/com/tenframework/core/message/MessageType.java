package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息类型枚举
 * 对应C语言中的TEN_MSG_TYPE枚举
 */
public enum MessageType {

    /**
     * 无效消息类型
     */
    INVALID("invalid"),

    /**
     * 命令消息 - 控制流和业务意图传递
     */
    COMMAND("cmd"),

    /**
     * 命令结果消息 - 命令执行结果的回溯
     */
    COMMAND_RESULT("cmd_result"),

    /**
     * 关闭应用命令
     */
    COMMAND_CLOSE_APP("cmd_close_app"),

    /**
     * 启动图命令
     */
    COMMAND_START_GRAPH("cmd_start_graph"),

    /**
     * 停止图命令
     */
    COMMAND_STOP_GRAPH("cmd_stop_graph"),

    /**
     * 定时器命令
     */
    COMMAND_TIMER("cmd_timer"),

    /**
     * 超时命令
     */
    COMMAND_TIMEOUT("cmd_timeout"),

    /**
     * 数据消息 - 实时信息载荷传输
     */
    DATA("data"),

    /**
     * 视频帧消息
     */
    VIDEO_FRAME("video_frame"),

    /**
     * 音频帧消息
     */
    AUDIO_FRAME("audio_frame");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 从字符串值解析消息类型
     */
    public static MessageType fromValue(String value) {
        if (value == null) {
            return INVALID;
        }

        for (MessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("未知的消息类型: " + value);
    }

    /**
     * 检查是否是命令类型消息
     */
    public boolean isCommand() {
        return this == COMMAND ||
                this == COMMAND_CLOSE_APP ||
                this == COMMAND_START_GRAPH ||
                this == COMMAND_STOP_GRAPH ||
                this == COMMAND_TIMER ||
                this == COMMAND_TIMEOUT;
    }

    /**
     * 检查是否是命令结果类型消息
     */
    public boolean isCommandResult() {
        return this == COMMAND_RESULT;
    }

    /**
     * 检查是否是数据类型消息（包括音视频帧）
     */
    public boolean isData() {
        return this == DATA || this == VIDEO_FRAME || this == AUDIO_FRAME;
    }

    /**
     * 检查是否是媒体帧消息
     */
    public boolean isMediaFrame() {
        return this == VIDEO_FRAME || this == AUDIO_FRAME;
    }

    /**
     * 检查是否需要在引擎关闭时继续处理
     */
    public boolean shouldHandleWhenClosing() {
        return this == COMMAND_CLOSE_APP ||
                this == COMMAND_STOP_GRAPH ||
                this == COMMAND_TIMEOUT;
    }
}