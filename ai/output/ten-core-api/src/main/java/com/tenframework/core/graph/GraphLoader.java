package com.tenframework.core.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;

public class GraphLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * 从JSON字符串加载图配置。
     *
     * @param jsonString JSON格式的图配置字符串
     * @return 解析后的GraphConfig对象
     * @throws JsonProcessingException 如果JSON解析失败
     */
    public static GraphConfig loadGraphConfigFromJson(String jsonString) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(jsonString, GraphConfig.class);
    }

    /**
     * 将GraphConfig对象转换为JSON字符串。
     *
     * @param graphConfig GraphConfig对象
     * @return JSON格式的图配置字符串
     * @throws JsonProcessingException 如果JSON序列化失败
     */
    public static String toJson(GraphConfig graphConfig) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(graphConfig);
    }
}