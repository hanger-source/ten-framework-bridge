package com.tenframework.core.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.ConnectionConfig;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.graph.NodeConfig;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.MessageType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_APP_URI;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_NAME;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;
import static com.tenframework.core.message.MessageConstants.SYS_EXTENSION_NAME;

/**
 * 处理 "start_graph" 命令的处理器。
 */
@Slf4j
public class StartGraphCommandHandler implements InternalCommandHandler {

    private static void registerGraph(Engine engine,
        GraphInstance graphInstance,
        GraphConfig graphConfig, String clientAppUri, String clientLocationUri)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException {

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
                    graphInstance.getConnectionRoutes().put(SYS_EXTENSION_NAME, connectionConfigs);
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
                    clientConnectionProperties.put(PROPERTY_CLIENT_GRAPH_ID, graphInstance.getGraphId());
                    if (!(instance instanceof Extension extension)) {
                        throw new IllegalArgumentException(
                            "Extension class does not implement Extension interface: " + extensionType);
                    }
                    nodeProperties.putAll(clientConnectionProperties);
                    graphInstance.registerExtension(extensionName, extension, nodeProperties);
                    log.info("Extension已注册到图: graphId={}, extensionName={}, extensionType={}",
                        graphInstance.getGraphId(), extensionName, extensionType);
                }
            }
        }

        engine.addGraphInstance(clientLocationUri, graphInstance);
    }

    @SneakyThrows
    @Override
    public void handle(Command command, Engine engine) {
        String graphName = (String)command.getProperties().get(PROPERTY_CLIENT_GRAPH_NAME);
        String clientAppUri = command.getProperty(PROPERTY_CLIENT_APP_URI, String.class);
        Object graphJsonObj = command.getProperties().get("graph_json");
        String channelId = (String)command.getProperties().get(PROPERTY_CLIENT_CHANNEL_ID);


        log.info("Engine收到start_graph命令: graphName={}, clientAppUri={}", graphName, clientAppUri);

        CommandResult commandResult;

        if (graphName == null || graphName.isEmpty() || clientAppUri == null || clientAppUri.isEmpty() || graphJsonObj == null) {
            commandResult = CommandResult.error(command.getCommandId(),
                "start_graph命令缺少graphName, app_uri或graph_json属性");
            log.error("start_graph命令参数缺失: graphName={}, clientAppUri={}, graphJson={}", graphName, clientAppUri, graphJsonObj);
        } else if (!(graphJsonObj instanceof String graphJson)) {
            commandResult = CommandResult.error(command.getCommandId(),
                Map.of("error", "graph_json属性不是有效的JSON字符串").get("error"));
            log.error("start_graph命令graph_json类型错误: graphName={}, clientAppUri={}, graphJsonType={}", graphName, clientAppUri,
                graphJsonObj.getClass().getName());
        } else {
            try {
                GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                GraphInstance instance = new GraphInstance(clientAppUri, graphConfig, engine);

                String clientLocationUri = clientAppUri + "/" + graphName + "/" + instance.getGraphId() + "@" + channelId;
                registerGraph(engine, instance, graphConfig, clientLocationUri, clientLocationUri);
                commandResult = CommandResult.success(command.getCommandId(),
                    Map.of("message", "Graph started successfully.",
                        PROPERTY_CLIENT_LOCATION_URI, clientLocationUri));
                commandResult.setProperty(PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                commandResult.setProperty(PROPERTY_CLIENT_GRAPH_ID, instance.getGraphId());

                log.info("图实例启动成功: graphName={}, graphId={}, appUri={}", graphName, instance.getGraphId(), clientAppUri);

            } catch (Exception e) {
                commandResult = CommandResult.error(command.getCommandId(),"启动图实例失败: " + e.getMessage());
            }
        }

        Map<String, Object> properties = new HashMap<>(command.getProperties());
        properties.remove("graph_json");
        properties.remove(PROPERTY_CLIENT_GRAPH_ID);
        properties.putAll(commandResult.getProperties());
        commandResult.setName(command.getName());
        commandResult.setProperties(properties);
        engine.submitMessage(commandResult);
    }

    @Override
    public String getCommandName() {
        return "start_graph";
    }
}