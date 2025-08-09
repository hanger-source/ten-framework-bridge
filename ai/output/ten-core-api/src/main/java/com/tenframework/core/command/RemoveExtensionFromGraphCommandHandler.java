package com.tenframework.core.command;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.RemoveExtensionFromGraphCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * `RemoveExtensionFromGraphCommandHandler` 负责处理
 * `RemoveExtensionFromGraphCommand` 命令。
 * 该命令用于动态地从一个运行中的 Graph (Engine) 移除 Extension 实例。
 */
@Slf4j
public class RemoveExtensionFromGraphCommandHandler implements GraphEventCommandHandler {

    /**
     * 通用处理方法，将命令分发给具体的处理方法。
     *
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    @Override
    public Object handle(Engine engine, Command command) {
        if (command instanceof RemoveExtensionFromGraphCommand) {
            return handleRemoveExtensionFromGraphCommand(engine, (RemoveExtensionFromGraphCommand) command);
        } else {
            log.warn("RemoveExtensionFromGraphCommandHandler: 收到非 RemoveExtensionFromGraphCommand 命令: {}",
                    command.getType());
            return CommandResult.fail(command.getId(), "Unsupported command type.");
        }
    }

    @Override
    public Object handleStartGraphCommand(Engine engine, StartGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleStopGraphCommand(Engine engine, StopGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    @Override
    public Object handleAddExtensionToGraphCommand(Engine engine, AddExtensionToGraphCommand command) {
        return CommandResult.fail(command.getId(), "Not supported.");
    }

    /**
     * 处理 `RemoveExtensionFromGraphCommand` 命令。
     *
     * @param engine  Engine 实例。
     * @param command `RemoveExtensionFromGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    @Override
    public Object handleRemoveExtensionFromGraphCommand(Engine engine, RemoveExtensionFromGraphCommand command) {
        String graphId = command.getDestLocs().stream()
                .filter(loc -> loc.getGraphId() != null)
                .map(Location::getGraphId)
                .findFirst()
                .orElse(null);

        if (graphId == null || !graphId.equals(engine.getGraphId())) {
            log.error(
                    "RemoveExtensionFromGraphCommand: 命令目标 graphId 不匹配当前 Engine 或未指定. Command DestLocs: {}, Engine GraphId: {}",
                    command.getDestLocs(), engine.getGraphId());
            return CommandResult.fail(command.getId(), "Graph ID mismatch or not specified.");
        }

        // 获取要移除的 Extension 名称
        String extensionName = command.getExtensionName();
        if (extensionName == null || extensionName.isEmpty()) {
            log.error("RemoveExtensionFromGraphCommand: 缺少 Extension 名称. Command: {}", command);
            return CommandResult.fail(command.getId(), "Missing Extension name.");
        }

        log.info("RemoveExtensionFromGraphCommand: Engine {} 尝试移除 Extension: {}", engine.getEngineId(), extensionName);

        try {
            // 1. 更新 Engine 的 GraphDefinition (如果需要动态更新)
            GraphDefinition currentGraphDefinition = engine.getGraphDefinition();
            if (currentGraphDefinition != null) {
                // 检查是否已存在同名 Extension
                boolean exists = currentGraphDefinition.getExtensionsInfo().stream()
                        .anyMatch(extInfo -> extInfo.getExtensionName().equals(extensionName));
                if (!exists) {
                    log.warn("RemoveExtensionFromGraphCommand: Engine {} 中不存在 Extension: {}. 跳过移除.",
                            engine.getEngineId(), extensionName);
                    return CommandResult.success(command.getId(), "Extension does not exist.");
                }

                // 复制并移除对应的 ExtensionInfo
                List<ExtensionInfo> updatedExtensionsInfo = new ArrayList<>(currentGraphDefinition.getExtensionsInfo());
                updatedExtensionsInfo.removeIf(extInfo -> extInfo.getExtensionName().equals(extensionName));
                currentGraphDefinition.setExtensionsInfo(updatedExtensionsInfo);
                log.info("RemoveExtensionFromGraphCommand: Engine {} 的 GraphDefinition 已更新，移除 Extension: {}",
                        engine.getEngineId(), extensionName);
            }

            // 2. 卸载 Extension 实例 (通过 ExtensionContext)
            engine.getExtensionContext().unloadExtension(extensionName);

            log.info("RemoveExtensionFromGraphCommand: Engine {} 成功移除 Extension: {}", engine.getEngineId(),
                    extensionName);
            return CommandResult.success(command.getId(), "Extension removed successfully.");

        } catch (Exception e) {
            log.error("RemoveExtensionFromGraphCommand: Engine {} 移除 Extension {} 失败: {}", engine.getEngineId(),
                    extensionName, e.getMessage(), e);
            return CommandResult.fail(command.getId(), "Failed to remove extension: " + e.getMessage());
        }
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