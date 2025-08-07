package com.tenframework.core.message;

public final class MessageConstants {
    // 新增：Data 消息的路径 ID
    public static final String PROPERTY_DATA_PATH_ID = "__data_path_id__";

    public static final String VALUE_SERVER_SYS_APP_URI = "sys_app";
    public static final String SYS_EXTENSION_NAME = "sys_engine";

    //  ====================  客户端  ================
    /**
     * 通用消息属性键 表示来自客户端
     **/
    public static final String PROPERTY_CLIENT_LOCATION_URI = "__client_location_uri__";
    public static final String PROPERTY_CLIENT_APP_URI = "__client_app_uri__";
    /** 属性键: 客户端原始消息来源的图ID */
    public static final String PROPERTY_CLIENT_GRAPH_ID = "__client_graph_id__";

    //  ================ Netty ==================
    /**
     * 属性键: Channel ID
     */
    public static final String PROPERTY_CLIENT_CHANNEL_ID = "__channel_id__";

    // 消息优先级属性
    public static final String PROPERTY_MESSAGE_PRIORITY = "__message_priority__";

    // ====================  TEST  ================
    // Data消息名称常量
    public static final String DATA_NAME_ECHO_DATA = "echo_data";

    private MessageConstants() {
        // 私有构造函数，防止实例化
    }
}