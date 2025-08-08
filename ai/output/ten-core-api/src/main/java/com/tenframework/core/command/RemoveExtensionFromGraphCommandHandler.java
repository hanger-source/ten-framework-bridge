package com.tenframework.core.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理 "remove_extension_from_graph" 命令的处理器。
 */
@Slf4j
public class RemoveExtensionFromGraphCommandHandler implements GraphEventCommandHandler {

    @Override
    public void handle(Command command, Engine engine) {
        String graphId = (String) command.getProperties().get("graph_id");
        String extensionName = (String) command.getProperties().get("extension_name");
        String appUri = (String) command.getProperties().get("app_uri");
        String associatedChannelId = (String) command.getProperties().get("__channel_id__");

        log.info("Engine收到remove_extension_from_graph命令: graphId={}, extensionName={}, appUri={}",
                graphId, extensionName, appUri);

        CommandResult result;
        if (graphId == null || graphId.isEmpty() || extensionName == null || extensionName.isEmpty()) {
            result = CommandResult.error(command.getCommandId(),
                    (String) Map.of("error", "remove_extension_from_graph命令缺少graph_id或extension_name属性").get("error"));
            log.error("remove_extension_from_graph命令参数缺失: graphId={}, extensionName={}", graphId, extensionName);
        } else if (engine.getGraphInstance(graphId).isEmpty()) {
            result = CommandResult.error(command.getCommandId(),
                    (String) Map.of("error", "图实例不存在: " + graphId).get("error"));
            log.warn("尝试移除扩展到不存在的图实例: graphId={}", graphId);
        } else {
            GraphInstance graphInstance = engine.getGraphInstance(graphId).orElse(null);
            if (graphInstance == null) {
                result = CommandResult.error(command.getCommandId(),
                        (String) Map.of("error", "图实例不存在: " + graphId).get("error"));
                log.warn("尝试移除扩展到不存在的图实例: graphId={}", graphId);
            } else {
                boolean removed = graphInstance.removeExtension(extensionName);
                if (removed) {
                    result = CommandResult.success(command.getCommandId(), Map.of("message",
                            "Extension removed successfully.", "graph_id", graphId, "extension_name", extensionName));
                    log.info("扩展移除成功: graphId={}, extensionName={}", graphId, extensionName);
                } else {
                    result = CommandResult.error(command.getCommandId(),
                            (String) Map.of("error", "Extension not found in graph: " + extensionName).get("error"));
                    log.warn("尝试移除不存在的扩展: graphId={}, extensionName={}", graphId, extensionName);
                }
            }
        }

        result.setSourceLocation(Location.builder().appUri(appUri).graphId(graphId).extensionName("engine").build());
        result.setDestinationLocations(Collections.singletonList(command.getSourceLocation()));
        if (associatedChannelId != null) {
            // Fallback: 如果没有CompletableFuture，且有ChannelId，则通过ClientConnectionExtension回传
            result.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, associatedChannelId);
            Location clientLoc = command.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class) != null
                    ? new Location(command.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class),
                            command.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class),
                            ClientConnectionExtension.NAME)
                    : null;

            if (clientLoc != null) {
                result.setDestinationLocations(List.of(clientLoc));
            } else {
                result.setDestinationLocations(List.of(Location.builder()
                        .appUri("system-app")
                        .graphId("system-graph")
                        .extensionName(ClientConnectionExtension.NAME)
                        .build()));
            }
            engine.submitMessage(result);
            log.debug("Engine: remove_extension_from_graph命令结果路由到ClientConnectionExtension. ChannelId: {}, Result: {}",
                    associatedChannelId, result);
        }
    }

}