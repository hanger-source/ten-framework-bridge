package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.graph.NodeConfig;
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
 * 处理 "start_graph" 命令的处理器。
 */
@Slf4j
public class StartGraphCommandHandler implements InternalCommandHandler {

    @Override
    public void handle(Command command, Engine engine, CompletableFuture<Object> resultFuture) {
        String graphId = (String) command.getProperties().get("graph_id");
        String appUri = (String) command.getProperties().get("app_uri");
        Object graphJsonObj = command.getProperties().get("graph_json");
        String associatedChannelId = (String) command.getProperties().get("__channel_id__");

        // 从原始命令中获取客户端上下文属性
        String clientLocationUriFromCommand = command.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI,
                String.class);
        String clientAppUriFromCommand = command.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
        String clientGraphIdFromCommand = command.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

        log.info("Engine收到start_graph命令: graphId={}, appUri={}", graphId, appUri);

        CommandResult result;
        if (graphId == null || graphId.isEmpty() || appUri == null || appUri.isEmpty() || graphJsonObj == null) {
            result = CommandResult.error(command.getCommandId(),
                    Map.of("error", "start_graph命令缺少graph_id, app_uri或graph_json属性").get("error"));
            log.error("start_graph命令参数缺失: graphId={}, appUri={}, graphJson={}", graphId, appUri, graphJsonObj);
        } else if (!(graphJsonObj instanceof String graphJson)) {
            result = CommandResult.error(command.getCommandId(),
                    Map.of("error", "graph_json属性不是有效的JSON字符串").get("error"));
            log.error("start_graph命令graph_json类型错误: graphId={}, appUri={}, graphJsonType={}", graphId, appUri,
                    graphJsonObj.getClass().getName());
        } else if (engine.getGraphInstance(graphId).isPresent()) { // 使用engine.getGraphInstance
            result = CommandResult.error(command.getCommandId(),
                    Map.of("error", "图实例已存在: " + graphId).get("error"));
            log.warn("尝试启动已存在的图实例: graphId={}", graphId);
        } else {
            try {
                GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                if (graphConfig.getGraphId() != null && !graphConfig.getGraphId().equals(graphId)) {
                    log.warn("命令中的graphId与GraphConfig不一致，使用命令中的: commandGraphId={}, configGraphId={}", graphId,
                            graphConfig.getGraphId());
                }
                graphConfig.setGraphId(graphId);

                GraphInstance newGraphInstance = new GraphInstance(graphId, appUri, engine, graphConfig);

                if (graphConfig.getNodes() != null) {
                    for (NodeConfig nodeConfig : graphConfig.getNodes()) {
                        String extensionName = nodeConfig.getName();
                        String extensionType = nodeConfig.getType();
                        Map<String, Object> nodeProperties = nodeConfig.getProperties();

                        if (extensionType != null && !extensionType.isEmpty()) {
                            try {
                                Class<?> clazz = Class.forName(extensionType);
                                Object instance = clazz.getDeclaredConstructor().newInstance();

                                if (!(instance instanceof Extension)) {
                                    throw new IllegalArgumentException(
                                            "Extension class does not implement Extension interface: " + extensionType);
                                }
                                Extension extension = (Extension) instance;

                                // 如果是ClientConnectionExtension，则注入客户端上下文属性
                                if (ClientConnectionExtension.class.getName().equals(extensionType)) {
                                    Map<String, Object> clientConnectionProperties = new java.util.HashMap<>(
                                            nodeProperties != null ? nodeProperties : Collections.emptyMap());
                                    if (clientLocationUriFromCommand != null) {
                                        clientConnectionProperties.put(MessageConstants.PROPERTY_CLIENT_LOCATION_URI,
                                                clientLocationUriFromCommand);
                                    }
                                    if (clientAppUriFromCommand != null) {
                                        clientConnectionProperties.put(MessageConstants.PROPERTY_CLIENT_APP_URI,
                                                clientAppUriFromCommand);
                                    }
                                    if (clientGraphIdFromCommand != null) {
                                        clientConnectionProperties.put(MessageConstants.PROPERTY_CLIENT_GRAPH_ID,
                                                clientGraphIdFromCommand);
                                    }
                                    if (associatedChannelId != null) {
                                        clientConnectionProperties.put(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID,
                                                associatedChannelId);
                                    }
                                    newGraphInstance.registerExtension(extensionName, extension,
                                            clientConnectionProperties);
                                    log.info(
                                            "ClientConnectionExtension已注册到图，并注入客户端上下文: graphId={}, extensionName={}, clientLocationUri={}",
                                            graphId, extensionName, clientLocationUriFromCommand);
                                } else {
                                    newGraphInstance.registerExtension(extensionName, extension, nodeProperties);
                                    log.info("Extension已注册到图: graphId={}, extensionName={}, extensionType={}",
                                            graphId, extensionName, extensionType);
                                }
                            } catch (Exception e) {
                                log.error("实例化或注册Extension失败: graphId={}, extensionType={}, extensionName={}",
                                        graphId, extensionType, extensionName, e);
                                newGraphInstance.markStartFailed();
                                throw new RuntimeException("Failed to instantiate or register Extension", e);
                            }
                        }
                    }
                }

                engine.addGraphInstance(graphId, newGraphInstance);

                result = CommandResult.success(command.getCommandId(),
                        Map.of("message", "Graph started successfully.", "graph_id", graphId));
                log.info("图实例启动成功: graphId={}, appUri={}", graphId, appUri);

            } catch (Exception e) {
                result = CommandResult.error(command.getCommandId(),
                        (String) Map.of("error", "启动图实例失败: " + e.getMessage()).get("error"));
                log.error("启动图实例时发生异常: graphId={}, appUri={}", graphId, appUri, e);
            }
        }

        result.setSourceLocation(Location.builder().appUri(appUri).graphId(graphId).extensionName("engine").build());
        // 目的地设置为原始命令的sourceLocation
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
            log.debug("Engine: start_graph命令结果路由到ClientConnectionExtension. ChannelId: {}, Result: {}",
                    associatedChannelId, result);
        }
    }

    @Override
    public String getCommandName() {
        return "start_graph";
    }
}