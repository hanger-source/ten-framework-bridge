package com.tenframework.core.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.ExtensionInfo;
import com.tenframework.core.message.command.RemoveExtensionFromGraphCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * `AddExtensionToGraphCommandHandler` 负责处理 `AddExtensionToGraphCommand` 命令。
 * 该命令用于动态地向一个运行中的 Graph (Engine) 添加新的 Extension 实例。
 */
@Slf4j
public class AddExtensionToGraphCommandHandler implements GraphEventCommandHandler {

    /**
     * 通用处理方法，将命令分发给具体的处理方法。
     * 
     * @param engine  Engine 实例。
     * @param command 要处理的命令。
     * @return 命令处理结果。
     */
    @Override
    public Object handle(Engine engine, Command command) {
        if (command instanceof AddExtensionToGraphCommand) {
            return handleAddExtensionToGraphCommand(engine, (AddExtensionToGraphCommand) command);
        } else {
            log.warn("AddExtensionToGraphCommandHandler: 收到非 AddExtensionToGraphCommand 命令: {}", command.getType());
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

    /**
     * 处理 `AddExtensionToGraphCommand` 命令。
     * 
     * @param engine  Engine 实例。
     * @param command `AddExtensionToGraphCommand` 命令对象。
     * @return 命令处理结果。
     */
    @Override
    public Object handleAddExtensionToGraphCommand(Engine engine, AddExtensionToGraphCommand command) {
        String graphId = command.getDestLocs().stream()
                .filter(loc -> loc.getGraphId() != null)
                .map(Location::getGraphId)
                .findFirst()
                .orElse(null);

        if (graphId == null || !graphId.equals(engine.getGraphId())) {
            log.error(
                    "AddExtensionToGraphCommand: 命令目标 graphId 不匹配当前 Engine 或未指定. Command DestLocs: {}, Engine GraphId: {}",
                    command.getDestLocs(), engine.getGraphId());
            return CommandResult.fail(command.getId(), "Graph ID mismatch or not specified.");
        }

        // 获取要添加的 Extension 信息
        ExtensionInfo newExtensionInfo = command.getExtensionInfo();
        if (newExtensionInfo == null || newExtensionInfo.getLoc() == null
                || newExtensionInfo.getLoc().getNodeId() == null) {
            log.error("AddExtensionToGraphCommand: 缺少 Extension 信息或 Extension ID. Command: {}", command);
            return CommandResult.fail(command.getId(), "Missing Extension info or ID.");
        }
        String newExtensionId = newExtensionInfo.getLoc().getNodeId();

        log.info("AddExtensionToGraphCommand: Engine {} 尝试添加 Extension: {}", engine.getEngineId(), newExtensionId);

        try {
            // 1. 更新 Engine 的 GraphDefinition (如果需要动态更新)
            // 注意: 实际系统中，GraphDefinition 可能是不可变的，或者需要专门的机制来更新。
            // 这里简化处理，假设我们可以直接在 Engine 内部更新其持有的 GraphDefinition。
            // 更好的方式可能是通过 Engine 的一个方法来触发 GraphDefinition 的更新和 Extension 的加载。
            GraphDefinition currentGraphDefinition = engine.getGraphDefinition();
            if (currentGraphDefinition != null) {
                // 检查是否已存在同名 Extension
                if (currentGraphDefinition.getExtensionsInfo().stream()
                        .anyMatch(extInfo -> extInfo.getLoc().getNodeId().equals(newExtensionId))) {
                    log.warn("AddExtensionToGraphCommand: Engine {} 中已存在 Extension: {}. 跳过添加.", engine.getEngineId(),
                            newExtensionId);
                    return CommandResult.success(command.getId(), "Extension already exists.");
                }

                // 复制并添加新的 ExtensionInfo
                List<ExtensionInfo> updatedExtensionsInfo = new ArrayList<>(currentGraphDefinition.getExtensionsInfo());
                updatedExtensionsInfo.add(newExtensionInfo);
                currentGraphDefinition.setExtensionsInfo(updatedExtensionsInfo);
                log.info("AddExtensionToGraphCommand: Engine {} 的 GraphDefinition 已更新，添加 Extension: {}",
                        engine.getEngineId(), newExtensionId);
            }

            // 2. 加载并初始化新的 Extension 实例
            Extension newExtension = engine.getExtensionContext().loadExtension(newExtensionInfo);
            if (newExtension == null) {
                log.error("AddExtensionToGraphCommand: 无法加载 Extension: {}. 可能addon未注册或信息不完整.", newExtensionId);
                return CommandResult.fail(command.getId(), "Failed to load new extension.");
            }
            // onInit 和 onStart 会在 loadExtension 内部或 ExtensionContext 的管理中被调用
            log.info("AddExtensionToGraphCommand: Engine {} 成功添加并加载 Extension: {}", engine.getEngineId(),
                    newExtensionId);
            return CommandResult.success(command.getId(), "Extension added successfully.");

        } catch (Exception e) {
            log.error("AddExtensionToGraphCommand: Engine {} 添加 Extension {} 失败: {}", engine.getEngineId(),
                    newExtensionId, e.getMessage(), e);
            return CommandResult.fail(command.getId(), "Failed to add extension: " + e.getMessage());
        }
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