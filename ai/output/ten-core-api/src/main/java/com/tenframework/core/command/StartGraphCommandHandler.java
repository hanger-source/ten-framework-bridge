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
 * `StartGraphCommandHandler` 负责处理 `StartGraphCommand` 命令。
 * 该命令用于启动一个新的 Graph (Engine) 实例。
 */
@Slf4j
public class StartGraphCommandHandler implements EngineCommandHandler {

    /**
     * 通用处理方法，将命令分发给具体的处理方法。
     *
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    @Override
    public Object handle(Engine engine, Command command) {
        if (command instanceof StartGraphCommand) {
            return handleStartGraphCommand(engine, (StartGraphCommand) command);
        } else {
            log.warn("StartGraphCommandHandler: 收到非 StartGraphCommand 命令: {}", command.getType());
            return CommandResult.fail(command.getId(), "Unsupported command type.");
        }
    }

    /**
     * 处理 `StartGraphCommand` 命令。
     *
     * @param engine  Engine 实例。
     * @param command `StartGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    @Override
    public Object handleStartGraphCommand(Engine engine, StartGraphCommand command) {
        String graphJson = command.getGraphJsonDefinition();
        String graphId = command.getDestLocs().stream()
                .filter(loc -> loc.getGraphId() != null)
                .map(Location::getGraphId)
                .findFirst()
                .orElse(null);

        if (graphId == null || !graphId.equals(engine.getGraphId())) {
            log.error(
                    "StartGraphCommand: 命令目标 graphId 不匹配当前 Engine 或未指定. Command DestLocs: {}, Engine GraphId: {}",
                    command.getDestLocs(), engine.getGraphId());
            return CommandResult.fail(command.getId(), "Graph ID mismatch or not specified.");
        }

        log.info("StartGraphCommand: Engine {} 收到启动图命令，graphJson: {}", engine.getEngineId(), graphJson);

        try {
            // Engine 的 initializeEngineRuntime 已经处理了 GraphDefinition 的加载和 Extension 的初始化
            // 因此这里只需返回成功，或者根据需要触发 Engine 的启动（如果尚未启动）
            if (!engine.isReadyToHandleMsg()) {
                log.warn("Engine {}: 收到 StartGraphCommand 但尚未完全初始化并准备好处理消息。", engine.getEngineId());
                // 这里的逻辑可能需要更细致的协调，例如等待 Engine 完全初始化
                // 目前假定 App 在创建 Engine 时已经调用了 initializeEngineRuntime
            }
            return CommandResult.success(command.getId(), "Graph start command received and processing.");
        } catch (Exception e) {
            log.error("StartGraphCommand: Engine {} 处理启动图命令失败: {}", engine.getEngineId(), e.getMessage(), e);
            return CommandResult.fail(command.getId(), "Failed to process start graph command: " + e.getMessage());
        }
    }

    @Override
    public Object handleStopGraphCommand(Engine engine, StopGraphCommand command) {
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
}