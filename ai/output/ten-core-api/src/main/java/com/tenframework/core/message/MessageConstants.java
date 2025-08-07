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
    public static final String APP_URI_SYSTEM = "ten.app.system"; // 新增系统应用URI

    /** 通用消息属性键 表示来自客户端 **/
    public static final String PROPERTY_CLIENT_LOCATION_URI = "__client_location_uri__";
    /** 属性键: 客户端的Channel ID */
    public static final String PROPERTY_CLIENT_CHANNEL_ID = "__channel_id__";

    // 新增：Data 消息的路径 ID
    public static final String PROPERTY_DATA_PATH_ID = "__data_path_id__";

    /** 属性键: 客户端原始消息来源的应用URI 因为交由netty handler处理之后统一变成了system app */
    public static final String PROPERTY_CLIENT_APP_URI = "__client_app_uri__";
    /** 属性键: 客户端原始消息来源的图ID */
    public static final String PROPERTY_CLIENT_GRAPH_ID = "__client_graph_id__";

    // 消息优先级属性
    public static final String PROPERTY_MESSAGE_PRIORITY = "__message_priority__";
    // Data消息名称常量
    public static final String DATA_NAME_ECHO_DATA = "echo_data";
}