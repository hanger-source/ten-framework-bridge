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

/**
 * 从图中移除扩展命令消息，对齐C/Python中类似动态移除扩展的功能。
 *
 * C端可能通过自定义命令或通用命令 (`CMD`) 的 properties 传递参数。
 * Java 实现中，我们将 `graphId` 和 `extensionName` 直接作为类的字段，方便结构化管理。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RemoveExtensionFromGraphCommand extends Command {

    /**
     * 目标图的 ID。
     */
    @JsonProperty("graph_id")
    private String graphId;

    /**
     * 要移除的 Extension 的名称（或 ID）。
     */
    @JsonProperty("extension_name")
    private String extensionName;

    /**
     * 全参构造函数，用于创建移除扩展命令消息。
     *
     * @param id            消息ID。
     * @param srcLoc        源位置。
     * @param type          消息类型 (应为 CMD_REMOVE_EXTENSION_FROM_GRAPH)。
     * @param destLocs      目的位置。
     * @param properties    消息属性。
     * @param timestamp     消息时间戳。
     * @param graphId       目标图的 ID。
     * @param extensionName 要移除的 Extension 的名称。
     */
    public RemoveExtensionFromGraphCommand(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp, String graphId, String extensionName) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.graphId = graphId;
        this.extensionName = extensionName;
    }

    /**
     * 用于内部创建的简化构造函数。
     * 
     * @param srcLoc        源位置。
     * @param destLocs      目的位置。
     * @param graphId       目标图的 ID。
     * @param extensionName 要移除的 Extension 的名称。
     */
    public RemoveExtensionFromGraphCommand(Location srcLoc, List<Location> destLocs, String graphId,
            String extensionName) {
        super(MessageType.CMD_REMOVE_EXTENSION_FROM_GRAPH, srcLoc, destLocs);
        this.graphId = graphId;
        this.extensionName = extensionName;
    }

}