package com.tenframework.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * 添加扩展到图命令消息，对齐C/Python中类似动态添加扩展的功能。
 *
 * C端可能通过自定义命令或通用命令 (`CMD`) 的 properties 传递参数。
 * Java 实现中，我们将 ExtensionInfo 直接作为类的字段，方便结构化管理。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AddExtensionToGraphCommand extends Command {

    /**
     * 要添加的 Extension 的信息。
     */
    @JsonProperty("extension_info")
    private ExtensionInfo extensionInfo;

    /**
     * 全参构造函数，用于创建添加扩展到图命令消息。
     *
     * @param id        消息ID。
     * @param srcLoc    源位置。
     * @param type      消息类型 (应为 CMD_ADD_EXTENSION_TO_GRAPH)。
     * @param destLocs  目的位置。
     * @param properties 消息属性。
     * @param timestamp 消息时间戳。
     * @param extensionInfo 要添加的 Extension 信息。
     */
    public AddExtensionToGraphCommand(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp, ExtensionInfo extensionInfo) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.extensionInfo = extensionInfo;
    }

    /**
     * 用于内部创建的简化构造函数。
     * @param srcLoc 源位置。
     * @param destLocs 目的位置。
     * @param extensionInfo 要添加的 Extension 信息。
     */
    public AddExtensionToGraphCommand(Location srcLoc, List<Location> destLocs, ExtensionInfo extensionInfo) {
        super(MessageType.CMD_ADD_EXTENSION_TO_GRAPH, srcLoc, destLocs);
        this.extensionInfo = extensionInfo;
    }

}