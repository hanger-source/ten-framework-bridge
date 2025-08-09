package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static java.util.Collections.emptyList;

/**
 * 表示一个消息处理图的静态配置或蓝图。
 * 它不包含任何运行时状态，仅定义图的结构、Extension 配置和连接路由规则。
 * 对应C语言中的ten_graph_t结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Slf4j
public class GraphDefinition {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonProperty("graph_id")
    private String graphId; // 图的唯一标识符

    @JsonProperty("app_uri")
    private String appUri; // 关联的 App URI

    @JsonProperty("graph_name")
    private String graphName; // 图的名称

    @JsonProperty("extension_groups_info")
    private List<ExtensionGroupInfo> extensionGroupsInfo;

    @JsonProperty("extensions_info")
    private List<ExtensionInfo> extensionsInfo;

    @JsonProperty("connections")
    private List<ConnectionConfig> connections;

    // 新增字段用于存储原始 JSON 内容
    private String jsonContent;

    public GraphDefinition(String appUri, String graphJsonDefinition) {
        this.appUri = appUri;
        this.jsonContent = graphJsonDefinition; // 保存原始 JSON

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(graphJsonDefinition);

            this.graphId = Optional.ofNullable(rootNode.get("graph_id"))
                    .map(JsonNode::asText)
                    .orElse(UUID.randomUUID().toString()); // 如果 JSON 中没有，则生成 UUID

            this.graphName = Optional.ofNullable(rootNode.get("graph_name"))
                    .map(JsonNode::asText)
                    .orElse("" + graphId); // 如果 JSON 中没有，则使用 graphId 作为名称

            // 解析 extension_groups_info
            JsonNode extGroupsNode = rootNode.get("extension_groups_info");
            if (extGroupsNode != null && extGroupsNode.isArray()) {
                this.extensionGroupsInfo = OBJECT_MAPPER.readerForListOf(ExtensionGroupInfo.class)
                        .readValue(extGroupsNode);
            } else {
                this.extensionGroupsInfo = new ArrayList<>();
            }

            // 解析 extensions_info
            JsonNode extsNode = rootNode.get("extensions_info");
            if (extsNode != null && extsNode.isArray()) {
                this.extensionsInfo = OBJECT_MAPPER.readerForListOf(ExtensionInfo.class).readValue(extsNode);
            } else {
                this.extensionsInfo = new ArrayList<>();
            }

            // 解析 connections
            JsonNode connectionsNode = rootNode.get("connections");
            if (connectionsNode != null && connectionsNode.isArray()) {
                this.connections = OBJECT_MAPPER.readerForListOf(ConnectionConfig.class).readValue(connectionsNode);
            } else {
                this.connections = new ArrayList<>();
            }

        } catch (JsonProcessingException e) {
            log.error("解析 Graph JSON 定义失败: {}", e.getMessage(), e);
            // 出现解析错误时，提供默认或空的列表，并生成随机 ID
            this.graphId = UUID.randomUUID().toString();
            this.graphName = "InvalidGraph-" + this.graphId;
            this.extensionGroupsInfo = new ArrayList<>();
            this.extensionsInfo = new ArrayList<>();
            this.connections = new ArrayList<>();
        } catch (Exception e) {
            log.error("处理 Graph JSON 定义时发生未知错误: {}", e.getMessage(), e);
            this.graphId = UUID.randomUUID().toString();
            this.graphName = "ErrorGraph-" + this.graphId;
            this.extensionGroupsInfo = new ArrayList<>();
            this.extensionsInfo = new ArrayList<>();
            this.connections = new ArrayList<>();
        }
    }
}