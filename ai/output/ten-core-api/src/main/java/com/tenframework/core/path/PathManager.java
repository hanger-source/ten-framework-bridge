package com.tenframework.core.path;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.server.ChannelDisconnectedException;
import com.tenframework.core.server.GraphStoppedException;
import lombok.extern.slf4j.Slf4j;

/**
 * 路径管理器，负责管理命令和数据在Engine内部的流转路径 (PathOut, DataInPath)。
 * 对应C语言中的ten_path_table_t结构。
 * 核心功能包括：
 * 1. 创建和管理命令的输出路径 (PathOut)，用于结果回溯。
 * 2. 处理数据消息的输入路径 (DataInPath)，存储客户端上下文。
 * 3. 根据不同的结果返回策略处理CommandResult。
 * 4. 清理断开连接的Channel相关的路径。
 */
@Slf4j
public class PathManager {

    /**
     * 维护commandId到PathOut的映射，用于命令结果的回溯。
     */
    private final ConcurrentMap<Long, PathOut> pathOuts = new ConcurrentHashMap<>();

    /**
     * 维护channelId到相关联的Command ID集合的映射，用于断开连接时清理PathOut。
     */
    private final ConcurrentMap<String, ConcurrentSkipListSet<Long>> channelToCommandIdsMap = new ConcurrentHashMap<>();

    /**
     * 维护dataPathId到DataInPath的映射，用于数据回溯或关联。
     */
    private final ConcurrentMap<UUID, DataInPath> dataInPaths = new ConcurrentHashMap<>();

    private final MessageSubmitter messageSubmitter;

    public PathManager(MessageSubmitter messageSubmitter) {
        this.messageSubmitter = messageSubmitter;
    }

    /**
     * 创建一个PathOut实例并添加到路径表中。
     *
     * @param commandId           命令ID
     * @param parentCommandId     父命令ID (可选)
     * @param commandName         命令名称
     * @param sourceLocation      命令源位置
     * @param destinationLocation 命令目标位置
     * @param resultFuture        用于完成命令结果的CompletableFuture
     * @param returnPolicy        结果返回策略
     * @param channelId           关联的Channel ID (可选)
     */
    public void createOutPath(long commandId, long parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, String channelId) {
        PathOut pathOut = new PathOut(commandId, parentCommandId, commandName, sourceLocation,
                destinationLocation, resultFuture, returnPolicy, channelId);
        pathOuts.put(commandId, pathOut);

        if (channelId != null) {
            channelToCommandIdsMap.computeIfAbsent(channelId, k -> new ConcurrentSkipListSet<>())
                    .add(commandId);
            log.debug("PathManager: 关联Command {} 到 Channel {}", commandId, channelId);
        }
        log.debug("PathManager: 创建PathOut: commandId={}, parentCommandId={}, source={}, dest={}",
                commandId, parentCommandId, sourceLocation, destinationLocation);
    }

    /**
     * 根据Command ID获取PathOut实例。
     *
     * @param commandId 命令ID
     * @return PathOut的Optional，如果不存在则为空
     */
    public Optional<PathOut> getOutPath(long commandId) {
        return Optional.ofNullable(pathOuts.get(commandId));
    }

    /**
     * 从路径表中移除PathOut实例。
     *
     * @param commandId 要移除的命令ID
     */
    public void removeOutPath(long commandId) {
        PathOut removedPath = pathOuts.remove(commandId);
        if (removedPath != null) {
            log.debug("PathManager: 移除PathOut: commandId={}", commandId);
            if (removedPath.getChannelId() != null) {
                ConcurrentSkipListSet<Long> commandIds = channelToCommandIdsMap.get(removedPath.getChannelId());
                if (commandIds != null) {
                    commandIds.remove(commandId);
                    if (commandIds.isEmpty()) {
                        channelToCommandIdsMap.remove(removedPath.getChannelId());
                    }
                }
            }
        }
    }

    /**
     * 创建一个DataInPath实例并添加到路径表中。
     *
     * @param dataPathId     数据路径ID
     * @param clientLocation 客户端Location
     * @param channelId      关联的Channel ID
     */
    public void createInPath(UUID dataPathId, Location clientLocation, String channelId) {
        DataInPath dataInPath = new DataInPath(dataPathId, clientLocation, channelId);
        dataInPaths.put(dataPathId, dataInPath);
        log.debug("PathManager: 创建DataInPath: dataPathId={}, clientLocation={}, channelId={}",
                dataPathId, clientLocation, channelId);
    }

    /**
     * 根据Data Path ID获取DataInPath实例。
     *
     * @param dataPathId 数据路径ID
     * @return DataInPath的Optional，如果不存在则为空
     */
    public Optional<DataInPath> getInPath(UUID dataPathId) {
        return Optional.ofNullable(dataInPaths.get(dataPathId));
    }

    /**
     * 从路径表中移除DataInPath实例。
     *
     * @param dataPathId 要移除的数据路径ID
     */
    public void removeInPath(UUID dataPathId) {
        DataInPath removedPath = dataInPaths.remove(dataPathId);
        if (removedPath != null) {
            log.debug("PathManager: 移除DataInPath: dataPathId={}", dataPathId);
        }
    }

    /**
     * 处理结果返回策略
     * 根据PathOut中配置的ResultReturnPolicy来决定如何处理命令结果
     */
    public void handleResultReturnPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        ResultReturnPolicy policy = pathOut.getReturnPolicy();

        switch (policy) {
            case FIRST_ERROR_OR_LAST_OK -> handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            case EACH_OK_AND_ERROR -> handleEachOkAndErrorPolicy(pathOut, commandResult);
            default -> {
                log.warn("PathManager: 未知的结果返回策略: policy={}, commandId={}",
                        policy, commandResult.getCommandId());
                // 默认使用FIRST_ERROR_OR_LAST_OK策略
                handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            }
        }

        // 如果PathOut关联了channelId，并且不是FIRST_ERROR_OR_LAST_OK策略的最终结果，则将结果回传给客户端
        if (pathOut.getChannelId() != null) {
            // 如果是EACH_OK_AND_ERROR策略，或者FIRST_ERROR_OR_LAST_OK策略但还未收到最终结果，则直接发送
            if (policy == ResultReturnPolicy.EACH_OK_AND_ERROR || !commandResult.isFinal()) {
                log.debug(
                        "PathManager: handleResultReturnPolicy通过ClientConnectionExtension回传. ChannelId: {}, Result: {}",
                        pathOut.getChannelId(), commandResult);
                // 路由到ClientConnectionExtension
                // 从CommandResult的properties中获取client相关的location信息
                String clientChannelId = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID,
                        String.class);
                String clientLocationUri = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI,
                        String.class);
                String clientAppUri = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
                String clientGraphId = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID,
                        String.class);

                Location clientLoc = null;
                if (clientLocationUri != null && clientAppUri != null && clientGraphId != null) {
                    clientLoc = Location.builder()
                            .appUri(clientAppUri)
                            .graphId(clientGraphId)
                            .extensionName(ClientConnectionExtension.NAME)
                            .build();
                }

                if (clientLoc != null) {
                    commandResult.setDestinationLocations(List.of(clientLoc));
                } else { // 如果没有clientLoc，但有channelId，说明是直接返回到channel，路由到ClientConnectionExtension
                    commandResult.setDestinationLocations(List.of(Location.builder()
                            .appUri("system-app")
                            .graphId("system-graph")
                            .extensionName(ClientConnectionExtension.NAME)
                            .build()));
                }
                messageSubmitter.submitMessage(commandResult);
            }
        }
    }

    /**
     * 处理FIRST_ERROR_OR_LAST_OK策略
     * 优先返回第一个错误，或等待所有OK结果并返回最后一个OK结果
     */
    private void handleFirstErrorOrLastOkPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        // 如果是错误结果且还未收到最终结果，立即完成Future
        if (!commandResult.isSuccess() && !pathOut.isHasReceivedFinalCommandResult()) {
            log.debug("PathManager: 收到错误结果，立即返回: commandId={}, error={}",
                    commandResult.getCommandId(), commandResult.getError());
            completeCommandResult(pathOut, commandResult);
            return;
        }

        // 如果是成功结果，缓存起来
        if (commandResult.isSuccess()) {
            pathOut.setCachedCommandResult(commandResult);
            log.debug("PathManager: 缓存成功结果: commandId={}",
                    commandResult.getCommandId());
        }

        // 如果是最终结果，使用缓存的结果或当前结果
        if (commandResult.isFinal()) {
            log.debug("PathManager: FIRST_ERROR_OR_LAST_OK最终结果. CommandId: {}", commandResult.getCommandId());
            CommandResult finalResult = pathOut.getCachedCommandResult() != null ? pathOut.getCachedCommandResult()
                    : commandResult;
            completeCommandResult(pathOut, finalResult);
        }
    }

    /**
     * 处理EACH_OK_AND_ERROR策略
     * 返回每个OK或ERROR结果（流式结果）
     */
    private void handleEachOkAndErrorPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        log.debug("PathManager: 流式返回结果: commandId={}, isSuccess={}, isFinal={}",
                commandResult.getCommandId(), commandResult.isSuccess(), commandResult.isFinal());

        // 如果是最终结果，完成Future
        if (commandResult.isFinal()) {
            log.debug("PathManager: EACH_OK_AND_ERROR最终结果. CommandId: {}", commandResult.getCommandId());
            completeCommandResult(pathOut, commandResult);
        }
    }

    /**
     * 完成命令结果的Future并进行回溯
     * 将CommandResult的commandId恢复为parentCommandId，目的地设置为原始命令的sourceLocation
     */
    private void completeCommandResult(PathOut pathOut, CommandResult commandResult) throws CloneNotSupportedException {
        pathOut.setHasReceivedFinalCommandResult(true);

        // 完成CompletableFuture
        if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
            pathOut.getResultFuture().complete(commandResult.getResult()); // 完成时传入实际结果值
            log.debug("PathManager: 命令结果Future已完成: commandId={}",
                    commandResult.getCommandId());
        }

        // 进行结果回溯：创建新的CommandResult消息并重新提交到Engine队列
        if (pathOut.getParentCommandId() != 0) { // 使用0作为无效parentCommandId的判断
            CommandResult backtrackResult = commandResult.clone();

            // 恢复commandId为parentCommandId
            long parentCmdId = pathOut.getParentCommandId(); // 明确声明为long
            backtrackResult.setCommandId(parentCmdId); // 直接传递long类型

            // 设置目的地为原始命令的sourceLocation
            backtrackResult.setDestinationLocations(List.of(pathOut.getSourceLocation()));
            backtrackResult.setSourceLocation(pathOut.getDestinationLocation());

            // 重新提交到Engine的消息队列继续回溯
            messageSubmitter.submitMessage(backtrackResult);
            log.debug("PathManager: 命令结果已回溯: originalCommandId={}, parentCommandId={}",
                    commandResult.getCommandId(), pathOut.getParentCommandId());
        } else if (commandResult.getProperties() != null
                && commandResult.getProperties().containsKey(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID)) { // 如果是根命令，且有ClientLocation，路由到ClientConnectionExtension
            String clientChannelId = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID,
                    String.class);
            String clientLocationUri = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI,
                    String.class);
            String clientAppUri = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, String.class);
            String clientGraphId = commandResult.getProperty(MessageConstants.PROPERTY_CLIENT_GRAPH_ID, String.class);

            Location clientLoc = null;
            if (clientLocationUri != null && clientAppUri != null && clientGraphId != null) {
                clientLoc = Location.builder()
                        .appUri(clientAppUri)
                        .graphId(clientGraphId)
                        .extensionName(ClientConnectionExtension.NAME)
                        .build();
            }

            if (clientLoc != null) {
                commandResult.setDestinationLocations(List.of(clientLoc));
            } else { // 如果没有clientLoc，但有channelId，说明是直接返回到channel，路由到ClientConnectionExtension
                commandResult.setDestinationLocations(List.of(Location.builder()
                        .appUri("system-app")
                        .graphId("system-graph")
                        .extensionName(ClientConnectionExtension.NAME)
                        .build()));
            }

            messageSubmitter.submitMessage(commandResult);
            log.debug("PathManager: 根命令结果路由到ClientConnectionExtension. ChannelId: {}, Result: {}",
                    clientChannelId, commandResult);
        }

        // 无论如何，最终都要移除PathOut
        removeOutPath(pathOut.getCommandId());
    }

    /**
     * 处理连接断开事件，清理与该Channel相关的PathOut和DataInPath
     *
     * @param channelId 断开连接的Channel ID
     */
    public void handleChannelDisconnected(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("PathManager: 尝试处理空的或无效的Channel ID断开事件");
            return;
        }

        log.info("PathManager: 处理Channel断开连接事件: channelId={}", channelId);

        // 清理PathOut
        ConcurrentSkipListSet<Long> commandIdsToCleanup = channelToCommandIdsMap.remove(channelId);
        if (commandIdsToCleanup != null && !commandIdsToCleanup.isEmpty()) {
            log.debug("PathManager: 发现 {} 个与Channel {} 相关的命令需要清理", commandIdsToCleanup.size(), channelId);
            for (long commandId : commandIdsToCleanup) {
                Optional<PathOut> pathOutOpt = getOutPath(commandId);
                if (pathOutOpt.isPresent()) {
                    PathOut pathOut = pathOutOpt.get();
                    // 完成Future，表明连接已断开，命令无法返回结果
                    if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                        pathOut.getResultFuture().completeExceptionally(new ChannelDisconnectedException(
                                "Channel " + channelId + " disconnected. CommandResult cannot be returned."));
                    }
                    removeOutPath(commandId); // 内部会处理从pathOuts中移除，不需要重复
                    log.debug("PathManager: 清理与断开连接Channel相关的PathOut: commandId={}", commandId);
                } else {
                    log.warn("PathManager: 在断开连接清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
                }
            }
        } else {
            log.debug("PathManager: 没有发现与Channel {} 相关的命令需要清理", channelId);
        }

        // 清理DataInPath (如果有DataInPath与Channel关联)
        // 假设DataInPath也会通过channelId进行清理，但目前DataInPath没有channelId映射
        // 如果DataInPath需要通过channelId清理，需要添加channelId到DataInPath，并在DataInPath创建时维护映射
        // 目前DataInPath只用于内部关联，生命周期与Command类似，通过DataPathId来管理
        // 暂时不做处理，如果未来有需要再实现DataInPath的Channel清理逻辑
        // Stream<Map.Entry<UUID, DataInPath>> dataInPathsToCleanup =
        // dataInPaths.entrySet().stream()
        // .filter(entry -> channelId.equals(entry.getValue().channelId()))
        // .collect(Collectors.toList());
        // dataInPathsToCleanup.forEach(entry -> removeInPath(entry.getKey()));
    }

    /**
     * 清理与指定图实例相关的所有PathOut实例。
     * 当图实例停止时调用。
     *
     * @param graphId 要清理的图实例ID
     */
    public void cleanupPathsForGraph(String graphId) {
        if (graphId == null || graphId.isEmpty()) {
            log.warn("PathManager: 尝试清理空的或无效的Graph ID相关的路径");
            return;
        }

        log.info("PathManager: 清理与Graph {} 相关的PathOut实例", graphId);

        // 收集需要移除的Command ID
        List<Long> commandIdsToCleanup = pathOuts.entrySet().stream()
                .filter(entry -> {
                    PathOut pathOut = entry.getValue();
                    // 检查sourceLocation或destinationLocation的graphId是否匹配
                    return (pathOut.getSourceLocation() != null
                            && graphId.equals(pathOut.getSourceLocation().graphId())) ||
                            (pathOut.getDestinationLocation() != null
                                    && graphId.equals(pathOut.getDestinationLocation().graphId()));
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (commandIdsToCleanup.isEmpty()) {
            log.debug("PathManager: 没有发现与Graph {} 相关的PathOut需要清理", graphId);
            return;
        }

        log.debug("PathManager: 发现 {} 个与Graph {} 相关的PathOut需要清理", commandIdsToCleanup.size(), graphId);

        for (long commandId : commandIdsToCleanup) {
            Optional<PathOut> pathOutOpt = getOutPath(commandId);
            if (pathOutOpt.isPresent()) {
                PathOut pathOut = pathOutOpt.get();
                // 完成Future，表明图已停止，命令可能无法返回结果
                if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                    pathOut.getResultFuture().completeExceptionally(new GraphStoppedException(
                            "Graph " + graphId + " stopped. CommandResult cannot be returned."));
                }
                removeOutPath(commandId); // 内部会处理从pathOuts和channelToCommandIdsMap中移除
                log.debug("PathManager: 清理与停止图相关的PathOut: commandId={}", commandId);
            } else {
                log.warn("PathManager: 在图停止清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
            }
        }
    }
}