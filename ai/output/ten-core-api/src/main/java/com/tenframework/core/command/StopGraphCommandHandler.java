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
        // Default handle method, should ideally be overridden by specific handlers.
        if (!(command instanceof StopGraphCommandMessage)) {
            log.warn("StopGraphCommandHandler 收到非 StopGraphCommandMessage 类型命令，将尝试通用处理: {}", command.getName());
            return;
        }
        StopGraphCommandMessage stopGraphCommand = (StopGraphCommandMessage) command;

        String graphId = stopGraphCommand.getGraphId();
        // appUri 和 clientLocationUri 可以在这里根据需要构建或传递
        // 目前 StopGraphCommandMessage 中只包含 graphId，如果 appUri 或 clientLocationUri 是必须的，可能需要更新命令类
        // 暂时从原始command的properties中获取，或者通过graphId从GraphInstance中推断
        String clientLocationUri = command.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String appUri = ClientLocationUriUtils.getAppUri(clientLocationUri); // 尝试从clientLocationUri中提取

        log.info("Engine收到stop_graph命令: graphId={}, appUri={}, clientLocationUri={}",
                graphId, appUri, clientLocationUri);

        CommandResult commandResult;
        if (graphId == null || graphId.isEmpty()) {
            commandResult = CommandResult.error(stopGraphCommand.getId(), "stop_graph命令缺少graph_id");
            log.error("stop_graph命令参数缺失: graphId=null");
        } else {
            // 根据graphId获取并移除图实例
            Optional<GraphInstance> optional = engine.getGraphInstance(graphId);
            if (optional.isPresent()) {
                try {
                    engine.removeGraphInstance(graphId);
                    optional.get().cleanupAllExtensions();
                    // 清理PathManager中与此图实例相关的PathOut
                    engine.cleanup(graphId);
                    commandResult = CommandResult.success(stopGraphCommand.getId(),
                            Map.of("message", "Graph stopped successfully.",
                                    MessageConstants.PROPERTY_CLIENT_GRAPH_ID, graphId));
                    // 如果有clientLocationUri，也将其添加到结果中
                    if (clientLocationUri != null) {
                        commandResult.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                    }
                    log.info("图实例停止成功: graphId={}", graphId);
                } catch (Exception e) {
                    commandResult = CommandResult.error(stopGraphCommand.getId(), "停止图实例失败: " + e.getMessage());
                    log.error("停止图实例时发生异常: graphId={}", graphId, e);
                }
            } else {
                commandResult = CommandResult.error(stopGraphCommand.getId(), "图实例不存在或未加载: " + graphId);
                log.warn("尝试停止不存在的图实例: graphId={}", graphId);
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

    @Override
    public void handle(StopGraphCommandMessage command, Engine engine) {
        // 复制通用处理逻辑到这里，并使用强类型 command
        String graphId = command.getGraphId();
        String clientLocationUri = command.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, String.class);
        String appUri = ClientLocationUriUtils.getAppUri(clientLocationUri); // 从clientLocationUri中提取

        log.info("Engine收到stop_graph命令 (强类型): graphId={}, appUri={}, clientLocationUri={}",
                graphId, appUri, clientLocationUri);

        CommandResult commandResult;
        if (graphId == null || graphId.isEmpty()) {
            commandResult = CommandResult.error(command.getId(), "stop_graph命令缺少graph_id");
            log.error("stop_graph命令参数缺失 (强类型): graphId=null");
        } else {
            Optional<GraphInstance> optional = engine.getGraphInstance(graphId);
            if (optional.isPresent()) {
                try {
                    engine.removeGraphInstance(graphId);
                    optional.get().cleanupAllExtensions();
                    engine.cleanup(graphId);
                    commandResult = CommandResult.success(command.getId(),
                            Map.of("message", "Graph stopped successfully.",
                                    MessageConstants.PROPERTY_CLIENT_GRAPH_ID, graphId));
                    if (clientLocationUri != null) {
                        commandResult.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, clientLocationUri);
                    }
                    log.info("图实例停止成功 (强类型): graphId={}", graphId);
                } catch (Exception e) {
                    commandResult = CommandResult.error(command.getId(), "停止图实例失败: " + e.getMessage());
                    log.error("停止图实例时发生异常 (强类型): graphId={}", graphId, e);
                }
            } else {
                commandResult = CommandResult.error(command.getId(), "图实例不存在或未加载 (强类型): " + graphId);
                log.warn("尝试停止不存在的图实例 (强类型): graphId={}", graphId);
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