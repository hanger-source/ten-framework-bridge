package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Command;

/**
 * 内部命令处理器的接口。
 */
public interface InternalCommandHandler {
    /**
     * 处理给定的内部命令。
     *
     * @param command 要处理的命令
     * @param engine  Engine实例，用于命令处理中可能需要的Engine操作
     */
    void handle(Command command, Engine engine);

    /**
     * 获取此处理器可以处理的命令名称。
     *
     * @return 命令名称
     */
    String getCommandName();
}