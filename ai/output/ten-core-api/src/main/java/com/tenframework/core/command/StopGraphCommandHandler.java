package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 处理 "stop_graph" 命令的处理器。
 */
@Slf4j
public class StopGraphCommandHandler implements InternalCommandHandler {

    @Override
    public void handle(Command command, Engine engine, CompletableFuture<Object> resultFuture) {
        String graphId = (String) command.getProperties().get("graph_id");
        String appUri = (String) command.getProperties().get("app_uri");
        String associatedChannelId = (String) command.getProperties().get("__channel_id__");

        log.info("Engine收到stop_graph命令: graphId={}, appUri={}", graphId, appUri);

        CommandResult result;
        if (graphId == null || graphId.isEmpty()) {
            result = CommandResult.error(command.getCommandId(),
                    Map.of("error", "stop_graph命令缺少graph_id属性").get("error"));
            log.error("stop_graph命令参数缺失: graphId={}", graphId);
        } else {
            GraphInstance removedGraph = engine.removeGraphInstance(graphId);
            if (removedGraph != null) {
                try {
                    removedGraph.cleanupAllExtensions();
                    result = CommandResult.success(command.getCommandId(),
                            Map.of("message", "Graph stopped successfully.", "graph_id", graphId));
                    log.info("图实例停止成功: graphId={}", graphId);
                } catch (Exception e) {
                    result = CommandResult.error(command.getCommandId(),
                            Map.of("error", "停止图实例失败: " + e.getMessage()).get("error"));
                    log.error("停止图实例时发生异常: graphId={}", graphId, e);
                }
            } else {
                result = CommandResult.error(command.getCommandId(),
                        Map.of("error", "图实例不存在: " + graphId).get("error"));
                log.warn("尝试停止不存在的图实例: graphId={}", graphId);
            }
        }

        result.setSourceLocation(Location.builder().appUri(appUri).graphId(graphId).extensionName("engine").build());
        result.setDestinationLocations(Collections.singletonList(command.getSourceLocation()));

        if (resultFuture != null && !resultFuture.isDone()) {
            if (result.isSuccess()) {
                resultFuture.complete(result.getResult());
            } else {
                resultFuture.completeExceptionally(new RuntimeException(result.getError()));
            }
        } else if (associatedChannelId != null) {
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
            log.debug("Engine: stop_graph命令结果路由到ClientConnectionExtension. ChannelId: {}, Result: {}",
                    associatedChannelId, result);
        }
    }

    @Override
    public String getCommandName() {
        return "stop_graph";
    }
}