package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;
import com.tenframework.core.graph.RoutingRule;

@Data
public class ConnectionConfig {
    /**
     * 连接的源节点名称
     */
    private String source;

    /**
     * 连接的目标节点名称列表
     */
    private List<String> destinations;

    /**
     * 连接的类型 (例如 "data", "command")
     */
    private String type;

    /**
     * 可选的条件表达式，用于消息路由。
     * 例如： "message.properties.someValue > 10"
     */
    private String condition;

    /**
     * 消息内容路由规则列表。
     * 如果匹配，将优先使用这些规则定义的targets。
     */
    @JsonProperty("routing_rules")
    private List<RoutingRule> routingRules;

    /**
     * 是否进行广播。如果为true，将忽略condition和routingRules，消息发送给所有destinations。
     */
    private boolean broadcast = false;

    /**
     * 连接优先级。当存在多条匹配的连接时，优先级高的连接优先（值越大优先级越高）。
     * 默认为0。
     */
    private int priority = 0;

    /**
     * 路由此连接所需的最小消息优先级。
     * 只有当消息的优先级大于或等于此值时，此连接才会被考虑。
     * 默认为0。
     */
    @JsonProperty("min_priority")
    private Integer minPriority = 0;
}