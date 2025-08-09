package com.tenframework.core.command.app;

import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.command.Command;
import lombok.extern.slf4j.Slf4j; // 修复 slf44j 为 slf4j

/**
 * `CloseAppCommandHandler` 处理 `CloseAppCommand` 命令，负责关闭整个 App。
 */
@Slf4j
public class CloseAppCommandHandler implements AppCommandHandler {

    @Override
    public Object handle(App app, Command command, Connection connection) {
        log.info("App: 收到 CloseAppCommand，即将关闭App。Command ID: {}", command.getId());
        app.stop(); // 调用 App 的停止方法
        // 返回成功结果
        if (connection != null) {
            CommandResult successResult = CommandResult.success(command.getId(), "App closed successfully.");
            connection.sendOutboundMessage(successResult);
        }
        return null; // App 级别命令通常不直接返回结果，而是通过 Connection.sendOutboundMessage 发送
    }
}