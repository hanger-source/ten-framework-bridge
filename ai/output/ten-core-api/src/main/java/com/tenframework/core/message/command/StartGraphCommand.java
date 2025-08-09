package com.tenframework.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tenframework.core.graph.ExtensionGroupInfo;
import com.tenframework.core.graph.ExtensionInfo;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class StartGraphCommand extends Message {

    @JsonProperty("long_running_mode")
    private Boolean longRunningMode;

    @JsonProperty("predefined_graph_name")
    private String predefinedGraphName;

    @JsonProperty("extension_groups_info")
    private List<ExtensionGroupInfo> extensionGroupsInfo;

    @JsonProperty("extensions_info")
    private List<ExtensionInfo> extensionsInfo;

    @JsonProperty("graph_json")
    private String graphJson;

    // 兼容 Lombok @NoArgsConstructor 的全参构造函数（为了Jackson）
    // 实际内部创建时使用自定义构造函数
    public StartGraphCommand(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            Boolean longRunningMode, String predefinedGraphName,
            List<ExtensionGroupInfo> extensionGroupsInfo, List<ExtensionInfo> extensionsInfo, String graphJson) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.longRunningMode = longRunningMode;
        this.predefinedGraphName = predefinedGraphName;
        this.extensionGroupsInfo = extensionGroupsInfo;
        this.extensionsInfo = extensionsInfo;
        this.graphJson = graphJson;
    }

    // 用于内部创建的构造函数，简化参数
    public StartGraphCommand(Location srcLoc, List<Location> destLocs, String graphJsonDefinition,
            boolean longRunningMode) {
        super(MessageType.CMD_START_GRAPH, srcLoc, destLocs);
        this.longRunningMode = longRunningMode;
        this.graphJson = graphJsonDefinition;
        // 其他属性可以根据需要设置，或在 Message 的 properties 中进行映射
    }

    // 辅助方法：获取 graphJsonDefinition (与 C 端字段名对齐)
    public String getGraphJsonDefinition() {
        return this.graphJson;
    }

    public boolean isLongRunningMode() {
        return this.longRunningMode != null ? this.longRunningMode : false; // 默认为 false
    }
}