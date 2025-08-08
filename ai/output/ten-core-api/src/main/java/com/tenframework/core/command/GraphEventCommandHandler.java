package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Command;

/**
 * 图事件处理器的接口。
 */
public interface GraphEventCommandHandler {
    /**
     * 处理给定的图事件
     *
     * @param command 要处理的图事件
     * @param engine  Engine实例，用于命令处理中可能需要的Engine操作
     */
    void handle(Command command, Engine engine);
}