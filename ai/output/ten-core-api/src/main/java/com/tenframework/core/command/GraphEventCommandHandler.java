package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.RemoveExtensionFromGraphCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * `GraphEventCommandHandler` 接口定义了处理与 Graph 事件相关命令的方法。
 * 这是一个抽象接口，具体的命令处理器将实现它。
 */
@Slf4j
public interface GraphEventCommandHandler {

    /**
     * 处理任意类型的命令。
     * 
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    Object handle(Engine engine, Command command);

    /**
     * 处理 `StartGraphCommand` 命令，用于启动一个新的 Graph 实例。
     * 
     * @param engine  Engine 实例。
     * @param command `StartGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleStartGraphCommand(Engine engine, StartGraphCommand command);

    /**
     * 处理 `StopGraphCommand` 命令，用于停止一个运行中的 Graph 实例。
     * 
     * @param engine  Engine 实例。
     * @param command `StopGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleStopGraphCommand(Engine engine, StopGraphCommand command);

    /**
     * 处理 `AddExtensionToGraphCommand` 命令，用于动态向图中添加 Extension。
     * 
     * @param engine  Engine 实例。
     * @param command `AddExtensionToGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleAddExtensionToGraphCommand(Engine engine, AddExtensionToGraphCommand command);

    /**
     * 处理 `RemoveExtensionFromGraphCommand` 命令，用于动态从图中移除 Extension。
     * 
     * @param engine  Engine 实例。
     * @param command `RemoveExtensionFromGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleRemoveExtensionFromGraphCommand(Engine engine, RemoveExtensionFromGraphCommand command);

    /**
     * 处理 `TimerCommand` 命令。
     * 
     * @param engine  Engine 实例。
     * @param command `TimerCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleTimerCommand(Engine engine, TimerCommand command);

    /**
     * 处理 `TimeoutCommand` 命令。
     * 
     * @param engine  Engine 实例。
     * @param command `TimeoutCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleTimeoutCommand(Engine engine, TimeoutCommand command);

    /**
     * 处理 `CloseAppCommand` 命令，用于关闭 App。
     * 
     * @param engine  Engine 实例。
     * @param command `CloseAppCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleCloseAppCommand(Engine engine, CloseAppCommand command);

    /**
     * 默认的命令处理实现，会根据命令类型分发到具体的 handle 方法。
     * 
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    static Object defaultHandle(Engine engine, Command command) {
        return switch (command.getType()) {
            case CMD_START_GRAPH -> ((StartGraphCommand) command);
            case CMD_STOP_GRAPH -> ((StopGraphCommand) command);
            case CMD_ADD_EXTENSION_TO_GRAPH -> ((AddExtensionToGraphCommand) command);
            case CMD_REMOVE_EXTENSION_FROM_GRAPH -> ((RemoveExtensionFromGraphCommand) command);
            case CMD_TIMER -> ((TimerCommand) command);
            case CMD_TIMEOUT -> ((TimeoutCommand) command);
            case CMD_CLOSE_APP -> ((CloseAppCommand) command);
            default -> {
                log.warn("GraphEventCommandHandler: 未知命令类型: {}", command.getType());
                throw new UnsupportedOperationException("未知命令类型: " + command.getType());
            }
        };
    }
}