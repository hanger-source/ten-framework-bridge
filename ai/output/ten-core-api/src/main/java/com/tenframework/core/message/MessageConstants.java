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

    // 通用消息属性键
    public static final String PROPERTY_CLIENT_CHANNEL_ID = "__client_channel_id__";
    public static final String PROPERTY_CLIENT_LOCATION_URI = "__client_location_uri__"; // 新增
    public static final String PROPERTY_MESSAGE_PRIORITY = "priority"; // 新增消息优先级属性

    // Data消息名称常量
    public static final String DATA_NAME_ECHO_DATA = "echo_data";
}