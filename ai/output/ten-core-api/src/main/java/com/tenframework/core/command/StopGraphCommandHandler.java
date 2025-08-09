package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.RemoveExtensionFromGraphCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.message.command.TimerCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * `StopGraphCommandHandler` 负责处理 `StopGraphCommand` 命令。
 * 该命令用于停止一个运行中的 Graph (Engine) 实例。
 */
@Slf4j
public class StopGraphCommandHandler implements EngineCommandHandler {

    /**
     * 通用处理方法，将命令分发给具体的处理方法。
     *
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    @Override
    public Object handle(Engine engine, Command command) {
        if (command instanceof StopGraphCommand) {
            return handleStopGraphCommand(engine, (StopGraphCommand) command);
        } else {
            log.warn("StopGraphCommandHandler: 收到非 StopGraphCommand 命令: {}", command.getType());
            return CommandResult.fail(command.getId(), "Unsupported command type.");
        }
    }

    @Override
    public Object handleStartGraphCommand(Engine engine, StartGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleAddExtensionToGraphCommand(Engine engine, AddExtensionToGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleRemoveExtensionFromGraphCommand(Engine engine, RemoveExtensionFromGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleTimerCommand(Engine engine, TimerCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleTimeoutCommand(Engine engine, TimeoutCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleCloseAppCommand(Engine engine, CloseAppCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    /**
     * 处理 `StopGraphCommand` 命令。
     *
     * @param engine  Engine 实例。
     * @param command `StopGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    @Override
    public Object handleStopGraphCommand(Engine engine, StopGraphCommand command) {
        String graphId = command.getDestLocs().stream()
                .filter(loc -> loc.getGraphId() != null)
                .map(Location::getGraphId)
                .findFirst()
                .orElse(null);

        if (graphId == null || !graphId.equals(engine.getGraphId())) {
            log.error(
                    "StopGraphCommand: 命令目标 graphId 不匹配当前 Engine 或未指定. Command DestLocs: {}, Engine GraphId: {}",
                    command.getDestLocs(), engine.getGraphId());
            return CommandResult.fail(command.getId(), "Graph ID mismatch or not specified.");
        }

        log.info("StopGraphCommand: Engine {} 收到停止图命令，graphId: {}", engine.getEngineId(), graphId);

        try {
            // 调用 Engine 的 stop 方法来停止它
            engine.stop();
            return CommandResult.success(command.getId(), "Graph stop command received and processing.");
        } catch (Exception e) {
            log.error("StopGraphCommand: Engine {} 处理停止图命令失败: {}", engine.getEngineId(), e.getMessage(), e);
            return CommandResult.fail(command.getId(), "Failed to process stop graph command: " + e.getMessage());
        }
    }
}