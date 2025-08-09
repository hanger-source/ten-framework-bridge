package com.tenframework.core.command.app;

import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.command.Command;
import lombok.extern.slf4j.Slf4j;

/**
 * `StopGraphCommandHandler` 处理 `StopGraphCommand` 命令，负责停止 Engine。
 */
@Slf4j
public class StopGraphCommandHandler implements AppCommandHandler {

    @Override
    public Object handle(App app, Command command, Connection connection) {
        // StopGraphCommand 可能没有特定的 Command 子类，这里直接从 Command 中获取 graphId
        String graphIdToStop = command.getDestLocs() != null && !command.getDestLocs().isEmpty()
                ? command.getDestLocs().get(0).getGraphId()
                : null;

        if (graphIdToStop != null && app.getEngines().containsKey(graphIdToStop)) {
            Engine engine = app.getEngines().get(graphIdToStop);
            log.info("App: 收到 StopGraphCommand，将停止指定 Engine: {}", graphIdToStop);
            engine.stop();
            app.getEngines().remove(graphIdToStop);
            log.info("App: Engine {} 已停止并从 App 中移除。", graphIdToStop);
            // 返回成功结果
            if (connection != null) {
                CommandResult successResult = CommandResult.success(command.getId(),
                        "Engine " + graphIdToStop + " stopped successfully.");
                connection.sendOutboundMessage(successResult);
            }
        } else {
            log.warn("App: 无法停止 Engine，因为指定的 Graph ID {} 不存在。", graphIdToStop);
            // 返回错误结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(),
                        "Failed to stop Engine: Graph ID " + graphIdToStop + " not found.");
                connection.sendOutboundMessage(errorResult);
            }
        }
        return null; // App 级别命令通常不直接返回结果，而是通过 Connection.sendOutboundMessage 发送
    }
}