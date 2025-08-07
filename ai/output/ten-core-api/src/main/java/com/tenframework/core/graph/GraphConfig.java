package com.tenframework.core.graph;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GraphConfig {
    /**
     * 图的名称
     */
    @JsonProperty("graph_name")
    private String graphName;

    /**
     * 预定义的图配置，键为图ID
     */
    @JsonProperty("predefined_graphs")
    private Map<String, GraphConfig> predefinedGraphs;

    /**
     * 图中的节点列表
     */
    private List<NodeConfig> nodes;

    /**
     * 节点之间的连接列表
     */
    private List<ConnectionConfig> connections;

    /**
     * 全局属性，传递给Engine和所有Extension
     */
    private Map<String, Object> properties;

    /**
     * 环境变量，传递给Engine和所有Extension
     */
    @JsonProperty("env_properties")
    private Map<String, String> envProperties;
}