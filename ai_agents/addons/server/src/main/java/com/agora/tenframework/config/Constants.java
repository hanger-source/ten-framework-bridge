package com.agora.tenframework.config;

import com.agora.tenframework.model.Prop;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TEN Framework 配置常量类
 *
 * 该类定义了TEN Framework的核心配置常量，包括：
 *
 * 1. 扩展模块名称 - 定义支持的AI扩展模块
 * 2. 配置文件路径 - 指定property.json配置文件位置
 * 3. Token过期时间 - 设置Agora Token的有效期
 * 4. Worker超时设置 - 定义Worker进程的超时策略
 * 5. 属性映射关系 - 将HTTP请求参数映射到property.json配置
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的config.go中的常量定义
 * - START_PROP_MAP对应Go版本的startPropMap
 * - 使用Java字段名（camelCase）替代Go字段名（PascalCase）
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Constants {

        // ==================== 扩展模块名称 ====================
        /**
         * Agora RTC扩展模块名称
         * 用于实时音视频通话功能，处理音频/视频流
         */
        public static final String EXTENSION_NAME_AGORA_RTC = "agora_rtc";

        /**
         * Agora RTM扩展模块名称
         * 用于实时消息传输功能，处理文本消息和信令
         */
        public static final String EXTENSION_NAME_AGORA_RTM = "agora_rtm";

        /**
         * HTTP服务器扩展模块名称
         * 用于Worker进程内部的HTTP服务，提供API接口
         */
        public static final String EXTENSION_NAME_HTTP_SERVER = "http_server";

        // ==================== 配置文件设置 ====================
        /**
         * property.json配置文件路径
         * 该文件包含AI代理的完整配置，包括图形定义、扩展模块配置等
         * 对应Go版本：PropertyJsonFile = "./agents/property.json"
         */
        public static final String PROPERTY_JSON_FILE = "./agents/property.json";

        // ==================== Token配置 ====================
        /**
         * Token过期时间（秒）
         * Agora Token的有效期，默认24小时
         * 对应Go版本：tokenExpirationInSeconds = uint32(86400)
         */
        public static final int TOKEN_EXPIRATION_IN_SECONDS = 86400;

        // ==================== Worker超时设置 ====================
        /**
         * Worker超时无限值
         * 表示Worker进程永不超时，用于长期运行的AI代理
         * 对应Go版本：WORKER_TIMEOUT_INFINITY = -1
         */
        public static final int WORKER_TIMEOUT_INFINITY = -1;

        /**
         * Gemini Worker最大数量
         * 限制同时运行的Gemini AI代理数量，防止资源耗尽
         * 对应Go版本：MAX_GEMINI_WORKER_COUNT = 3
         */
        public static final int MAX_GEMINI_WORKER_COUNT = 3;

        // ==================== 属性映射配置 ====================
        /**
         * 启动属性映射表
         * 将HTTP请求中的参数映射到property.json中的具体配置项
         *
         * 映射规则：
         * - Key: HTTP请求中的字段名（Java camelCase格式）
         * - Value: 包含扩展模块名和属性名的Prop对象列表
         *
         * 对应Go版本的startPropMap，但使用Java字段名
         */
        public static final Map<String, List<Prop>> START_PROP_MAP = new HashMap<>();

        /**
         * 静态初始化块
         * 在类加载时初始化START_PROP_MAP映射关系
         */
        static {
                // 频道名称映射 - 将请求中的channelName映射到RTC和RTM的channel属性
                START_PROP_MAP.put("channelName", Arrays.asList(
                                new Prop(EXTENSION_NAME_AGORA_RTC, "channel"), // RTC频道名
                                new Prop(EXTENSION_NAME_AGORA_RTM, "channel"))); // RTM频道名

                // 远程流ID映射 - 将请求中的remoteStreamId映射到RTC的remote_stream_id属性
                START_PROP_MAP.put("remoteStreamId", Arrays.asList(
                                new Prop(EXTENSION_NAME_AGORA_RTC, "remote_stream_id")));

                // 机器人流ID映射 - 将请求中的botStreamId映射到RTC的stream_id属性
                START_PROP_MAP.put("botStreamId", Arrays.asList(
                                new Prop(EXTENSION_NAME_AGORA_RTC, "stream_id")));

                // Token映射 - 将请求中的token映射到RTC和RTM的token属性
                START_PROP_MAP.put("token", Arrays.asList(
                                new Prop(EXTENSION_NAME_AGORA_RTC, "token"), // RTC Token
                                new Prop(EXTENSION_NAME_AGORA_RTM, "token"))); // RTM Token

                // Worker HTTP服务器端口映射 - 将请求中的workerHttpServerPort映射到HTTP服务器的listen_port属性
                START_PROP_MAP.put("workerHttpServerPort", Arrays.asList(
                                new Prop(EXTENSION_NAME_HTTP_SERVER, "listen_port")));
        }
}