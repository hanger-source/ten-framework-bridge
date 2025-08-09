package com.tenframework.core.message;

public final class MessageConstants {

    public static final String NOT_APPLICABLE = "N/A";

    // ==================== 系统 ================
    public static final String SYS_APP_URI = "sys://app";
    public static final String SYS_GRAPH_ID = "000000";
    public static final String SYS_EXTENSION_NAME = "sys_engine";

    // MsgPack 扩展类型，用于 Message 的序列化
    public static final byte TEN_MSGPACK_EXT_TYPE_MSG = 0;

    // ==================== 客户端 ================
    // public static final String PROPERTY_CLIENT_LOCATION_URI =
    // "__client_location_uri__";
    /**
     * 通用消息属性键 表示来自客户端
     **/
    public static final String PROPERTY_CLIENT_APP_URI = "__client_app_uri__";
    /** 属性键: 客户端原始消息来源的图ID */
    public static final String PROPERTY_CLIENT_GRAPH_ID = "__client_graph_id__";
    /** 属性键: 客户端原始消息来源的图名称 */
    public static final String PROPERTY_CLIENT_GRAPH_NAME = "__client_graph_name__";
    /** 属性键: 客户端 Channel ID */
    public static final String PROPERTY_CLIENT_CHANNEL_ID = "__client_channel_id__";

    // 消息优先级属性
    public static final String PROPERTY_MESSAGE_PRIORITY = "__message_priority__";

    // ==================== TEST ================
    // Data消息名称常量
    public static final String DATA_NAME_ECHO_DATA = "echo_data";

    private MessageConstants() {
        // 私有构造函数，防止实例化
    }
}