package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphLoader;
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
 * 处理 "add_extension_to_graph" 命令的处理器。
 */
@Slf4j
public class AddExtensionToGraphCommandHandler implements InternalCommandHandler {

    @Override
    public void handle(Command command, Engine engine, CompletableFuture<Object> resultFuture) {
        String graphId = (String) command.getProperties().get("graph_id");
        String extensionType = (String) command.getProperties().get("extension_type");
        String extensionName = (String) command.getProperties().get("extension_name");
        String appUri = (String) command.getProperties().get("app_uri");
        String graphJson = (String) command.getProperties().get("graph_json");
        String associatedChannelId = (String) command.getProperties().get("__channel_id__");

        log.info("Engine收到add_extension_to_graph命令: graphId={}, extensionType={}, extensionName={}, appUri={}",
                graphId, extensionType, extensionName, appUri);

        CommandResult result;
        if (graphId == null || graphId.isEmpty() || extensionType == null || extensionType.isEmpty()
                || extensionName == null || extensionName.isEmpty()) {
            result = CommandResult.error(command.getCommandId(),
                    (String) Map.of("error", "add_extension_to_graph命令缺少graph_id, extension_type, extension_name属性")
                            .get("error"));
            log.error("add_extension_to_graph命令参数缺失: graphId={}, extensionType={}, extensionName={}", graphId,
                    extensionType, extensionName);
        } else if (engine.getGraphInstance(graphId).isEmpty()) { // 使用engine.getGraphInstance
            result = CommandResult.error(command.getCommandId(),
                    (String) Map.of("error", "图实例不存在: " + graphId).get("error"));
            log.warn("尝试添加扩展到不存在的图实例: graphId={}", graphId);
        } else {
            try {
                GraphConfig graphConfig = null;
                if (graphJson != null && !graphJson.isEmpty()) {
                    graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                    if (graphConfig.getGraphId() != null && !graphConfig.getGraphId().equals(graphId)) {
                        log.warn("命令中的graphId与GraphConfig不一致，使用命令中的: commandGraphId={}, configGraphId={}", graphId,
                                graphConfig.getGraphId());
                    }
                    graphConfig.setGraphId(graphId);
                }

                GraphInstance graphInstance = engine.getGraphInstance(graphId).orElse(null);
                if (graphInstance == null) {
                    result = CommandResult.error(command.getCommandId(),
                            (String) Map.of("error", "图实例不存在: " + graphId).get("error"));
                    log.warn("尝试添加扩展到不存在的图实例: graphId={}", graphId);
                } else {
                    try {
                        Class<?> clazz = Class.forName(extensionType);
                        Object instance = clazz.getDeclaredConstructor().newInstance();

                        if (instance instanceof Extension extension) {
                            boolean registered = graphInstance.registerExtension(extensionName, extension, null);
                            if (!registered) {
                                log.error("注册Extension失败: graphId={}, extensionName={}", graphId, extensionName);
                                throw new RuntimeException("Extension注册失败");
                            }
                            result = CommandResult.success(command.getCommandId(),
                                    Map.of("message", "Extension added successfully.", "graph_id", graphId,
                                            "extension_name", extensionName));
                            log.info("扩展添加成功: graphId={}, extensionName={}", graphId, extensionName);
                        } else {
                            log.error("加载的类不是Extension类型: graphId={}, extensionType={}", graphId, extensionType);
                            result = CommandResult.error(command.getCommandId(),
                                    (String) Map.of("error", "加载的类不是Extension类型: " + extensionType).get("error"));
                        }
                    } catch (Exception e) {
                        log.error("实例化或注册Extension失败: graphId={}, extensionType={}, extensionName={}",
                                graphId, extensionType, extensionName, e);
                        result = CommandResult.error(command.getCommandId(),
                                (String) Map.of("error", "Extension实例化/注册失败: " + e.getMessage()).get("error"));
                    }
                }
            } catch (Exception e) {
                result = CommandResult.error(command.getCommandId(),
                        (String) Map.of("error", "添加扩展失败: " + e.getMessage()).get("error"));
                log.error("添加扩展时发生异常: graphId={}, extensionType={}, extensionName={}", graphId, extensionType,
                        extensionName, e);
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
            log.debug("Engine: add_extension_to_graph命令结果路由到ClientConnectionExtension. ChannelId: {}, Result: {}",
                    associatedChannelId, result);
        }
    }

    @Override
    public String getCommandName() {
        return "add_extension_to_graph";
    }
}