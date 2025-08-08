package com.tenframework.core.command;

import java.util.HashMap;
import java.util.Map;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_APP_URI;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_NAME;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;

/**
 * 处理 "start_graph" 命令的处理器。
 */
@Slf4j
public class StartGraphCommandHandler implements GraphEventCommandHandler {

    @SneakyThrows
    @Override
    public void handle(Command command, Engine engine) {
        String graphName = (String) command.getProperties().get(PROPERTY_CLIENT_GRAPH_NAME);
        String clientAppUri = command.getProperty(PROPERTY_CLIENT_APP_URI, String.class);
        Object graphJsonObj = command.getProperties().get("graph_json");
        String channelId = (String) command.getProperties().get(PROPERTY_CLIENT_CHANNEL_ID);

        log.info("Engine收到start_graph命令: graphName={}, clientAppUri={}", graphName, clientAppUri);

        CommandResult commandResult;

        if (graphName == null || graphName.isEmpty() || clientAppUri == null || clientAppUri.isEmpty()
                || graphJsonObj == null) {
            commandResult = CommandResult.error(command.getCommandId(),
                    "start_graph命令缺少graphName, app_uri或graph_json属性");
            log.error("start_graph命令参数缺失: graphName={}, clientAppUri={}, graphJson={}", graphName, clientAppUri,
                    graphJsonObj);
        } else if (!(graphJsonObj instanceof String graphJson)) {
            commandResult = CommandResult.error(command.getCommandId(),
                    Map.of("error", "graph_json属性不是有效的JSON字符串").get("error"));
            log.error("start_graph命令graph_json类型错误: graphName={}, clientAppUri={}, graphJsonType={}", graphName,
                    clientAppUri,
                    graphJsonObj.getClass().getName());
        } else {
            try {
                GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                GraphInstance instance = new GraphInstance(clientAppUri, graphConfig, engine);

                String clientLocationUri = clientAppUri + "/" + graphName + "/" + instance.getGraphId() + "@"
                        + channelId;
                engine.getGraphInstances().registerGraph(clientLocationUri, instance);
                commandResult = CommandResult.success(command.getCommandId(),
                        Map.of("message", "Graph started successfully.",
                                PROPERTY_CLIENT_LOCATION_URI, clientLocationUri));
                commandResult.setProperty(PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                commandResult.setProperty(PROPERTY_CLIENT_GRAPH_ID, instance.getGraphId());

                log.info("图实例启动成功: graphName={}, graphId={}, appUri={}", graphName, instance.getGraphId(),
                        clientAppUri);

            } catch (Exception e) {
                commandResult = CommandResult.error(command.getCommandId(), "启动图实例失败: " + e.getMessage());
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
}