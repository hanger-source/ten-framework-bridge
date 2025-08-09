package com.tenframework.core.command.app;

import java.util.Map;

import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.PredefinedGraphEntry;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.StartGraphCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * `StartGraphCommandHandler` 处理 `StartGraphCommand` 命令，负责启动 Engine。
 */
@Slf4j
public class StartGraphCommandHandler implements AppCommandHandler {

    @Override
    public Object handle(App app, Command command, Connection connection) {
        if (!(command instanceof StartGraphCommand)) {
            log.warn("StartGraphCommandHandler 收到非 StartGraphCommand 命令: {}", command.getType());
            // 返回失败结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(),
                        "Unexpected command type for StartGraphHandler.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        StartGraphCommand startCommand = (StartGraphCommand) command;
        String targetGraphId = startCommand.getGraphId(); // 从命令中获取目标 graphId

        // 优先从预定义图中查找 GraphDefinition
        GraphDefinition graphDefinition = null;
        if (targetGraphId != null && app.getPredefinedGraphsByName().containsKey(targetGraphId)) {
            PredefinedGraphEntry entry = app.getPredefinedGraphsByName().get(targetGraphId);
            graphDefinition = entry.getGraphDefinition();
            log.info("App: 从预定义图中找到 GraphDefinition (graphId: {})。", targetGraphId);
        } else if (startCommand.getGraphJsonDefinition() != null) {
            // 如果命令中包含 JSON 定义，则解析它
            graphDefinition = new GraphDefinition(app.getAppUri(), startCommand.getGraphJsonDefinition());
            log.info("App: 从 StartGraphCommand 中解析 GraphDefinition (graphId: {})。", graphDefinition.getGraphId());
        }

        if (graphDefinition == null) {
            log.error("App: 无法获取 GraphDefinition，无法启动 Engine。StartGraphCommand ID: {}", startCommand.getId());
            // 返回错误结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(startCommand.getId(), "Failed to get GraphDefinition.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        String actualGraphId = graphDefinition.getGraphId(); // 确保使用解析后的 graphId

        Engine engine = app.getEngines().get(actualGraphId);
        if (engine == null) {
            log.info("App: 创建新的 Engine 实例，Graph ID: {}", actualGraphId);
            engine = new Engine(actualGraphId, graphDefinition, app, true);
            app.getEngines().put(actualGraphId, engine);
            engine.start(); // 启动 Engine 及其 Runloop
            log.info("App: Engine {} 已启动。", actualGraphId);
        } else {
            log.info("App: Engine {} 已存在，重用现有实例。", actualGraphId);
        }

        // 迁移 Connection 到 Engine
        if (connection != null) {
            if (app.getOrphanConnections().remove(connection)) {
                log.info("App: 孤立连接 {} (Channel ID: {}) 已从孤立列表中移除。", connection.getRemoteAddress(),
                        connection.getChannel().id().asShortText());
            }
            log.info("App: 正在将连接 {} 迁移到 Engine {}。", connection.getRemoteAddress(), actualGraphId);
            connection.migrate(engine.getRunloop(),
                    new Location().setAppUri(app.getAppUri()).setGraphId(actualGraphId)); // 迁移到
            // Engine
            // 的
            // Runloop
            // 在连接迁移成功后，Engine 会处理连接的后续消息
            log.info("App: 连接 {} 已成功迁移到 Engine {}.", connection.getRemoteAddress(), actualGraphId);

            // 重要：更新 Connection 的 remoteLocation 为 StartGraphCommand 中的 srcLoc
            // 这表示 Connection 现在代表的是这个特定的远程客户端
            connection.setRemoteLocation(startCommand.getSrcLoc());

            // 返回成功的 CommandResult 给发起方
            CommandResult successResult = CommandResult.success(startCommand.getId(),
                    Map.of("graph_id", actualGraphId, "message", "Engine started and connection migrated."));
            connection.sendOutboundMessage(successResult);
            log.info("App: 发送 StartGraphCommand 成功结果给连接 {}。", connection.getRemoteAddress());
        } else {
            // 如果没有连接 (例如是内部自动启动的图)
            log.info("App: StartGraphCommand {} 处理完成，Engine {} 已启动。", startCommand.getId(), actualGraphId);
        }
        return null; // App 级别命令通常不直接返回结果，而是通过 Connection.sendOutboundMessage 发送
    }
}