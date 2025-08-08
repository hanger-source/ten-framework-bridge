package com.tenframework.core.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author fuhangbo.hanger.uhfun
 * 客户端位置URI解析工具类
 * 用于解析格式为 mock_front://test_app/graph_name/graph_id@channel_id 的URI
 */
public final class ClientLocationUriUtils {

    // 正则表达式，使用命名捕获组
    private static final String URI_REGEX =
        "^(?<appUri>[^/]+://[^/]+)/(?<graphName>[^/]+)/(?<graphId>[^@]+)@(?<channelId>.+)$";

    private static final Pattern URI_PATTERN = Pattern.compile(URI_REGEX);

    /**
     * 禁用构造函数，确保工具类不能被实例化
     */
    private ClientLocationUriUtils() {
    }

    /**
     * 解析给定的URI字符串，提取出其中的各个组成部分。
     * * @param uri 格式为 mock_front://test_app/graph_name/graph_id@channel_id 的URI字符串。
     * @return 包含解析结果的Map，如果URI格式不匹配，则返回一个空的Map。
     */
    public static Map<String, String> parse(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Matcher matcher = URI_PATTERN.matcher(uri);

        if (matcher.matches()) {
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("appUri", matcher.group("appUri"));
            resultMap.put("graphName", matcher.group("graphName"));
            resultMap.put("graphId", matcher.group("graphId"));
            resultMap.put("channelId", matcher.group("channelId"));
            return resultMap;
        }

        return Collections.emptyMap();
    }

    public static String getAppUri(String uri) {
        return get(uri, "appUri");
    }

    public static String getGraphName(String uri) {
        return get(uri, "graphName");
    }

    public static String getGraphId(String uri) {
        return get(uri, "graphId");
    }

    public static String getChannelId(String uri) {
        return get(uri, "channelId");
    }

    /**
     * 辅助方法，用于在获取特定值时避免空指针异常。
     * @param uri 要解析的URI。
     * @param key 要获取的键（例如："graph_id"）。
     * @return 对应键的值，如果不存在则返回null。
     */
    public static String get(String uri, String key) {
        Map<String, String> parsed = parse(uri);
        return parsed.get(key);
    }
}
