package com.tenframework.core.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.ConnectionConfig;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.graph.NodeConfig;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_APP_URI;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;
import static com.tenframework.core.message.MessageConstants.SYS_EXTENSION_NAME;
import static com.tenframework.core.message.MessageConstants.VALUE_SERVER_SYS_APP_URI;

/**
 * 处理 "start_graph" 命令的处理器。
 */
@Slf4j
public class StartGraphCommandHandler implements InternalCommandHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static void registerGraph(Engine engine, String graphJson, String graphId, String appUri,
        String clientLocationUri, String clientAppUri, String associatedChannelId)
        throws JsonProcessingException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
        GraphInstance newGraphInstance = new GraphInstance(graphId, appUri, engine, graphConfig);

        if (graphConfig.getNodes() != null) {
            for (NodeConfig nodeConfig : graphConfig.getNodes()) {
                String extensionName = nodeConfig.getName();
                String extensionType = nodeConfig.getType();
                Map<String, Object> nodeProperties = nodeConfig.getProperties() != null
                    ? nodeConfig.getProperties() : new HashMap<>();
                Object instance = null;
                if (ClientConnectionExtension.NAME.equals(extensionName)) {
                    extensionType = ClientConnectionExtension.class.getName();
                    instance = new ClientConnectionExtension(engine);
                    // 系统扩展到ClientConnectionExtension
                    ConnectionConfig connectionConfig = new ConnectionConfig();
                    connectionConfig.setSource(SYS_EXTENSION_NAME);
                    connectionConfig.setType(MessageType.DATA.getValue());
                    connectionConfig.setDestinations(List.of(ClientConnectionExtension.NAME));
                    List<ConnectionConfig> connectionConfigs = new java.util.ArrayList<>();
                    connectionConfigs.add(connectionConfig);
                    newGraphInstance.getConnectionRoutes().put(SYS_EXTENSION_NAME, connectionConfigs);
                }
                if (instance == null && extensionType != null && !extensionType.isEmpty()) {
                    Class<?> clazz = Class.forName(extensionType);
                    instance = clazz.newInstance();
                }
                if (instance != null) {
                    Map<String, Object> clientConnectionProperties = new HashMap<>();
                    clientConnectionProperties.put(PROPERTY_CLIENT_LOCATION_URI,
                        clientLocationUri);
                    clientConnectionProperties.put(PROPERTY_CLIENT_APP_URI, clientAppUri);
                    clientConnectionProperties.put(PROPERTY_CLIENT_GRAPH_ID, graphId);
                    clientConnectionProperties.put(PROPERTY_CLIENT_CHANNEL_ID, associatedChannelId);
                    if (!(instance instanceof Extension extension)) {
                        throw new IllegalArgumentException(
                            "Extension class does not implement Extension interface: " + extensionType);
                    }
                    nodeProperties.putAll(clientConnectionProperties);
                    newGraphInstance.registerExtension(extensionName, extension, nodeProperties);
                    log.info("Extension已注册到图: graphId={}, extensionName={}, extensionType={}",
                        graphId, extensionName, extensionType);
                }
            }
        }

        engine.addGraphInstance(graphId, newGraphInstance);
    }

    @SneakyThrows
    @Override
    public void handle(Command command, Engine engine) {
        String graphId = (String)command.getProperties().get(PROPERTY_CLIENT_GRAPH_ID);
        String appUri = (String)command.getProperties().get(PROPERTY_CLIENT_APP_URI);
        Object graphJsonObj = command.getProperties().get("graph_json");
        String associatedChannelId = (String)command.getProperties().get("__channel_id__");

        // 从原始命令中获取客户端上下文属性
        String clientLocationUri = command.getProperty(PROPERTY_CLIENT_LOCATION_URI,
            String.class);
        String clientAppUri = command.getProperty(PROPERTY_CLIENT_APP_URI, String.class);

        log.info("Engine收到start_graph命令: graphId={}, appUri={}", graphId, appUri);

        Map<String, Object> returnResult;

        if (graphId == null || graphId.isEmpty() || appUri == null || appUri.isEmpty() || graphJsonObj == null) {
            returnResult = Map.of("error", "start_graph命令缺少graph_id, app_uri或graph_json属性");
            log.error("start_graph命令参数缺失: graphId={}, appUri={}, graphJson={}", graphId, appUri, graphJsonObj);
        } else if (!(graphJsonObj instanceof String graphJson)) {
            returnResult = Map.of("error", "graph_json属性不是有效的JSON字符串");
            log.error("start_graph命令graph_json类型错误: graphId={}, appUri={}, graphJsonType={}", graphId, appUri,
                graphJsonObj.getClass().getName());
        } else if (engine.getGraphInstance(graphId).isPresent()) { // 使用engine.getGraphInstance
            returnResult = Map.of("error", "图实例已存在: " + graphId);
            log.warn("尝试启动已存在的图实例: graphId={}", graphId);
        } else {
            try {
                registerGraph(engine, graphJson, graphId, appUri, clientLocationUri, clientAppUri, associatedChannelId);

                returnResult = Map.of("message", "Graph started successfully.",
                    PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                log.info("图实例启动成功: graphId={}, appUri={}", graphId, appUri);

            } catch (Exception e) {
                returnResult = Map.of("error", "启动图实例失败: " + e.getMessage());
            }
        }

        Data data = Data.json("return", OBJECT_MAPPER.writeValueAsString(returnResult));
        data.setSourceLocation(new Location(VALUE_SERVER_SYS_APP_URI, graphId, SYS_EXTENSION_NAME));
        data.setDestinationLocation(new Location(appUri, graphId, ClientConnectionExtension.NAME));
        engine.submitMessage(data);
    }

    @Override
    public String getCommandName() {
        return "start_graph";
    }
}