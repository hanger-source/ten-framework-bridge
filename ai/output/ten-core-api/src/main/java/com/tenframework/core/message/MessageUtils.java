package com.tenframework.core.message;

/**
 * 消息相关的工具类和常量。
 */
public class MessageUtils {

    /**
     * MsgPack自定义扩展类型，用于TEN框架内部消息。
     * 对齐C/Python中的 TEN_MSGPACK_EXT_TYPE_MSG。
     */
    public static final byte TEN_MSGPACK_EXT_TYPE_MSG = (byte) 0x00;

    // 私有构造函数，防止实例化
    private MessageUtils() {
    }
}