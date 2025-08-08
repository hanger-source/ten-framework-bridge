package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public interface GraphEventCommandResultHandler {
    /**
     * 处理给定的图事件
     *
     * @param command 要处理的图事件
     * @param engine  Engine实例，用于命令处理中可能需要的Engine操作
     */
    void handle(CommandResult command, Engine engine);
}
