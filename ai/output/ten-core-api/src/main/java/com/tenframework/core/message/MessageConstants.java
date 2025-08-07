package com.tenframework.core.message;

public final class MessageConstants {
    private MessageConstants() {
        // 私有构造函数，防止实例化
    }

    // 消息类型值，与Message.java中的枚举对应
    public static final String MESSAGE_TYPE_COMMAND = "COMMAND";
    public static final String MESSAGE_TYPE_COMMAND_RESULT = "COMMAND_RESULT";
    public static final String MESSAGE_TYPE_DATA = "DATA";
    public static final String MESSAGE_TYPE_AUDIO_FRAME = "AUDIO_FRAME";
    public static final String MESSAGE_TYPE_VIDEO_FRAME = "VIDEO_FRAME";

    // App URI常量
    public static final String APP_URI_TEST_CLIENT = "ten.app.test-client";
    public static final String APP_URI_HTTP_CLIENT = "ten.app.http-client";
    public static final String APP_URI_SYSTEM = "system-app"; // 新增系统应用URI

    // 通用消息属性键
    public static final String PROPERTY_CLIENT_LOCATION_URI = "client_location_uri";
    /** 属性键: 客户端的Channel ID */
    public static final String PROPERTY_CLIENT_CHANNEL_ID = "client_channel_id";
    // 消息优先级属性
    public static final String PROPERTY_MESSAGE_PRIORITY = "message_priority";

    // --- Extension 类型 --- 1
    public static final String EXTENSION_TYPE_CLIENT_CONNECTION = "client_connection";

    // Data消息名称常量
    public static final String DATA_NAME_ECHO_DATA = "echo_data";
}