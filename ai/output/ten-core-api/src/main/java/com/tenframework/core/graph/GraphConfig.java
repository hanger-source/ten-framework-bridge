package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 表示 Ten 框架的整体配置，可以包含预定义的图、日志级别等。
 * 对应 property.json 的顶层 "ten" 字段下的内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GraphConfig { // 重命名为 TenConfig 更合适，但为了保持与用户提及的 GraphConfig 一致，暂时保留

    /**
     * 预定义的图配置列表。
     * 对应 property.json 中的 "predefined_graphs" 数组。
     */
    @JsonProperty("predefined_graphs")
    private List<PredefinedGraphEntry> predefinedGraphs;

    /**
     * 日志配置。
     * 对应 property.json 中的 "log" 字段。
     */
    @JsonProperty("log")
    private LogConfig logConfig; // 新增 LogConfig DTO 来处理日志配置

    // 移除了 graphName, nodes, connections, properties, envProperties，
    // 因为这些现在应该包含在 GraphDefinition 中或者更高级的配置中。

    // 您之前提到的 nodes 和 connections 字段应在 GraphDefinition 中定义
    // 如果 GraphConfig 还需要包含它们，则应该是在更高级别的配置对象中，例如 TenFrameworkConfig
}

// 假设的 LogConfig 类，用于反序列化 "log" 字段
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
class LogConfig {
    @JsonProperty("level")
    private int level;
}