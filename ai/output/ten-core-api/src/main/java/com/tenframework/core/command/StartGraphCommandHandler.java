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

    @Override
    public void handle(Command command, Engine engine) {
        // Default handle method, should ideally be overridden by specific handlers.
        // If we reach here, it means a more specific handle method wasn't called.
        if (!(command instanceof StartGraphCommandMessage)) {
            log.warn("StartGraphCommandHandler 收到非 StartGraphCommandMessage 类型命令，将尝试通用处理: {}", command.getName());
            // Fallback to the original generic handling if this is unexpected for this handler
            // Or throw an UnsupportedOperationException if this should never happen
            // For now, we will simply not process it further in this handler if it's not the specific type
            // The Engine's dispatch logic should ensure the correct handle method is called.
            return; // Exit if not the expected type
        }
        StartGraphCommandMessage startGraphCommand = (StartGraphCommandMessage) command;

        String graphName = startGraphCommand.getPredefinedGraphName();
        String clientAppUri = startGraphCommand.getAppUri(); // Assuming appUri is part of startGraphCommand
        String graphJson = startGraphCommand.getGraphJson();

        // The channelId might still come from properties if it's a transient property not part of the core command structure.
        // Or, we might need to decide if it belongs in the StartGraphCommandMessage itself.
        String channelId = (String) command.getProperties().get(PROPERTY_CLIENT_CHANNEL_ID);

        log.info("Engine收到start_graph命令: graphName={}, clientAppUri={}", graphName, clientAppUri);

        CommandResult commandResult;

        if (graphName == null || graphName.isEmpty() || clientAppUri == null || clientAppUri.isEmpty()
                || graphJson == null || graphJson.isEmpty()) {
            commandResult = CommandResult.error(startGraphCommand.getId(),
                    "start_graph命令缺少graphName, app_uri或graph_json属性");
            log.error("start_graph命令参数缺失: graphName={}, clientAppUri={}, graphJson={}", graphName, clientAppUri,
                    graphJson);
        } else if (engine.getGraphInstance(graphName).isEmpty()) { // 使用engine.getGraphInstance
            commandResult = CommandResult.error(startGraphCommand.getId(),
                    "图实例不存在: " + graphName);
            log.warn("尝试添加扩展到不存在的图实例: graphId={}", graphName);
        } else {
            try {
                GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                GraphInstance instance = new GraphInstance(clientAppUri, graphConfig, engine);

                String clientLocationUri = clientAppUri + "/" + graphName + "/" + instance.getGraphId();
                if (channelId != null && !channelId.isEmpty()) {
                    clientLocationUri += "@" + channelId;
                }
                engine.getGraphInstances().registerGraph(clientLocationUri, instance);
                commandResult = CommandResult.success(startGraphCommand.getId(),
                        Map.of("message", "Graph started successfully.",
                                PROPERTY_CLIENT_LOCATION_URI, clientLocationUri));
                commandResult.setProperty(PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                commandResult.setProperty(PROPERTY_CLIENT_GRAPH_ID, instance.getGraphId());

                log.info("图实例启动成功: graphName={}, graphId={}, appUri={}", graphName, instance.getGraphId(),
                        clientAppUri);

            } catch (Exception e) {
                commandResult = CommandResult.error(startGraphCommand.getId(), "启动图实例失败: " + e.getMessage());
                log.error("启动图实例失败: graphName={}, clientAppUri={}", graphName, clientAppUri, e);
            }
        }

        // 所有的原始properties都应该被保留，并合并commandResult的properties
        Map<String, Object> finalProperties = new HashMap<>(command.getProperties());
        finalProperties.putAll(commandResult.getProperties());
        commandResult.setProperties(finalProperties);
        commandResult.setName(command.getName()); // 保持命令名称
        commandResult.setSourceLocation(command.getSrcLoc()); // 保持源Location
        commandResult.setDestinationLocations(command.getDestLocs()); // 保持目的Location

        engine.submitMessage(commandResult);
    }

    @SneakyThrows
    @Override
    public void handle(StartGraphCommandMessage command, Engine engine) {
        // 复制通用处理逻辑到这里，并使用强类型 command
        String graphName = command.getPredefinedGraphName();
        String clientAppUri = command.getAppUri();
        String graphJson = command.getGraphJson();
        String channelId = (String) command.getProperties().get(PROPERTY_CLIENT_CHANNEL_ID); // channelId still from properties for now

        log.info("Engine收到start_graph命令 (强类型): graphName={}, clientAppUri={}", graphName, clientAppUri);

        CommandResult commandResult;

        if (graphName == null || graphName.isEmpty() || clientAppUri == null || clientAppUri.isEmpty()
                || graphJson == null || graphJson.isEmpty()) {
            commandResult = CommandResult.error(command.getId(),
                    "start_graph命令缺少graphName, app_uri或graph_json属性");
            log.error("start_graph命令参数缺失 (强类型): graphName={}, clientAppUri={}, graphJson={}", graphName, clientAppUri,
                    graphJson);
        } else if (engine.getGraphInstance(graphName).isEmpty()) {
            commandResult = CommandResult.error(command.getId(),
                    "图实例不存在: " + graphName);
            log.warn("尝试添加扩展到不存在的图实例 (强类型): graphId={}", graphName);
        } else {
            try {
                GraphConfig graphConfig = GraphLoader.loadGraphConfigFromJson(graphJson);
                GraphInstance instance = new GraphInstance(clientAppUri, graphConfig, engine);

                String clientLocationUri = clientAppUri + "/" + graphName + "/" + instance.getGraphId();
                if (channelId != null && !channelId.isEmpty()) {
                    clientLocationUri += "@" + channelId;
                }
                engine.getGraphInstances().registerGraph(clientLocationUri, instance);
                commandResult = CommandResult.success(command.getId(),
                        Map.of("message", "Graph started successfully.",
                                PROPERTY_CLIENT_LOCATION_URI, clientLocationUri));
                commandResult.setProperty(PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                commandResult.setProperty(PROPERTY_CLIENT_GRAPH_ID, instance.getGraphId());

                log.info("图实例启动成功 (强类型): graphName={}, graphId={}, appUri={}", graphName, instance.getGraphId(),
                        clientAppUri);

            } catch (Exception e) {
                commandResult = CommandResult.error(command.getId(), "启动图实例失败: " + e.getMessage());
                log.error("启动图实例失败 (强类型): graphName={}, clientAppUri={}", graphName, clientAppUri, e);
            }
        }

        Map<String, Object> finalProperties = new HashMap<>(command.getProperties());
        finalProperties.putAll(commandResult.getProperties());
        commandResult.setProperties(finalProperties);
        commandResult.setName(command.getName());
        commandResult.setSourceLocation(command.getSrcLoc());
        commandResult.setDestinationLocations(command.getDestLocs());

        engine.submitMessage(commandResult);
    }
}