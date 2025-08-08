package com.tenframework.core.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.util.ClientLocationUriUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理 "stop_graph" 命令的处理器。
 */
@Slf4j
public class StopGraphCommandHandler implements GraphEventCommandHandler {

    @Override
    public void handle(Command command, Engine engine) {

        String clientLocationUri = command.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String graphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
        String appUri = ClientLocationUriUtils.getAppUri(clientLocationUri);

        log.info("Engine收到stop_graph命令: graphId={}, appUri={}, clientLocationUri={}",
                graphId, appUri, clientLocationUri);

        CommandResult commandResult;
        if (clientLocationUri == null) {
            commandResult = CommandResult.error(command.getCommandId(), "stop_graph命令缺少clientLocationUri");
            log.error("stop_graph命令参数缺失: clientLocationUri=null");
        } else {
            // 根据clientLocationUri获取并移除图实例
            Optional<GraphInstance> optional = engine.getGraphInstance(clientLocationUri);
            if (optional.isPresent()) {
                try {
                    engine.removeGraphInstance(clientLocationUri);
                    optional.get().cleanupAllExtensions();
                    // 清理PathManager中与此图实例相关的PathOut
                    engine.cleanup(graphId);
                    commandResult = CommandResult.success(command.getCommandId(),
                            Map.of("message", "Graph stopped successfully.",
                                    MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri,
                                    MessageConstants.PROPERTY_CLIENT_GRAPH_ID, graphId));
                    log.info("图实例停止成功: graphId={}", graphId);
                } catch (Exception e) {
                    commandResult = CommandResult.error(command.getCommandId(), "停止图实例失败: " + e.getMessage());
                    log.error("停止图实例时发生异常: graphId={}", graphId, e);
                }
            } else {
                commandResult = CommandResult.error(command.getCommandId(), "图实例不存在或未加载: " + graphId);
                log.warn("尝试停止不存在的图实例: graphId={}", graphId);
            }
        }

        Map<String, Object> properties = new HashMap<>(command.getProperties());
        properties.putAll(commandResult.getProperties());
        commandResult.setCommandId(command.getCommandId());
        commandResult.setProperties(properties);
        commandResult.setName(command.getName());
        engine.submitMessage(commandResult);
    }

    @Override
    public String getCommandName() {
        return GraphEventCommandType.STOP_GRAPH.getCommandName();
    }
}