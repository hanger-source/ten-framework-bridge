package com.tenframework.core.path;

import com.tenframework.core.graph.ConnectionConfig;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
// import java.util.regex.Pattern; // 暂不需要，如果规则评估器复杂再引入
// import java.util.stream.Collectors; // 暂不需要

/**
 * 路径表，用于管理命令的生命周期和结果回溯。
 * 同时封装了图的路由逻辑。
 * 对应C语言中的ten_path_table_t结构。
 */
@Slf4j
public class PathTable {

    private final Long2ObjectHashMap<PathIn> inPaths;
    private final Long2ObjectHashMap<PathOut> outPaths;

    private final GraphDefinition graphDefinition;

    public PathTable(GraphDefinition graphDefinition) {
        this.inPaths = new Long2ObjectHashMap<>();
        this.outPaths = new Long2ObjectHashMap<>();
        this.graphDefinition = graphDefinition;
        log.info("PathTable initialized for graph: {}", graphDefinition.getGraphId());
    }

    public void addInPath(PathIn pathIn) {
        if (pathIn == null || pathIn.getCommandId() == 0) {
            log.warn("尝试添加空的或无效的PathIn到路径表");
            return;
        }
        long key = pathIn.getCommandId();
        inPaths.put(key, pathIn);
        log.debug("PathIn已添加: commandId={}", pathIn.getCommandId());
    }

    public void addOutPath(PathOut pathOut) {
        if (pathOut == null || pathOut.getCommandId() == 0) {
            log.warn("尝试添加空的或无效的PathOut到路径表");
            return;
        }
        long key = pathOut.getCommandId();
        outPaths.put(key, pathOut);
        log.debug("PathOut已添加: commandId={}", pathOut.getCommandId());
    }

    public Optional<PathIn> getInPath(long commandId) {
        return Optional.ofNullable(inPaths.get(commandId));
    }

    public Optional<PathOut> getOutPath(long commandId) {
        return Optional.ofNullable(outPaths.get(commandId));
    }

    public Optional<PathIn> removeInPath(long commandId) {
        Optional<PathIn> removed = Optional.ofNullable(inPaths.remove(commandId));
        removed.ifPresent(p -> log.debug("PathIn已移除: commandId={}", p.getCommandId()));
        return removed;
    }

    public Optional<PathOut> removeOutPath(long commandId) {
        Optional<PathOut> removed = Optional.ofNullable(outPaths.remove(commandId));
        removed.ifPresent(p -> log.debug("PathOut已移除: commandId={}", p.getCommandId()));
        return removed;
    }

    public void clear() {
        inPaths.clear();
        outPaths.clear();
        log.info("路径表已清空");
    }

    public int getInPathCount() {
        return inPaths.size();
    }

    public int getOutPathCount() {
        return outPaths.size();
    }

    public PathOut createOutPath(long commandId, long parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, Location returnLocation) { // 使用 Location returnLocation
        PathOut pathOut = new PathOut(commandId, parentCommandId, commandName, sourceLocation,
                destinationLocation, resultFuture, returnPolicy, returnLocation);
        addOutPath(pathOut);
        return pathOut;
    }

    /**
     * 处理连接断开事件，清理与该Channel相关的PathOut
     *
     * @param channelId 断开连接的Channel ID
     */
    public void handleChannelDisconnected(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            log.warn("尝试处理空的或无效的Channel ID断开事件");
            return;
        }

        outPaths.entrySet().removeIf(entry -> {
            PathOut pathOut = entry.getValue();
            // 使用 getReturnLocation() 的 appUri 或其他标识来匹配 channelId (简化处理，实际需要更复杂的映射)
            if (pathOut.getReturnLocation() != null && channelId.equals(pathOut.getReturnLocation().getAppUri())) { // 简化为匹配
                                                                                                                    // appUri
                log.debug("PathTable: 清理与 Channel {} 关联的 PathOut: commandId={}", channelId, pathOut.getCommandId());
                if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                    pathOut.getResultFuture().completeExceptionally(new RuntimeException("Client disconnected"));
                }
                return true; // 移除此条目
            }
            return false;
        });

        log.info("PathTable: 已处理 Channel {} 断开事件，清理完成。", channelId);
    }

    /**
     * 解析消息的目的地，根据图的连接配置和消息内容进行路由。
     * 对应C语言中的ten_path_table_resolve_destinations。
     *
     * @param message 待路由的消息
     * @return 消息可能到达的所有目标 Location 列表
     */
    public List<Location> resolveDestinations(Message message) {
        // 1. 优先检查消息是否已设置明确的目的地 (动态路由)
        if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
            log.debug("PathTable: Message has explicit destinations. Returning: {}", message.getDestLocs());
            return message.getDestLocs();
        }

        // 2. 如果没有明确目的地，则根据源位置和图配置进行静态路由
        if (message.getSrcLoc() == null || message.getSrcLoc().getGraphId() == null) {
            log.warn("PathTable: Message source location or graph ID is null. Cannot resolve destinations: {}",
                    message.getId());
            return Collections.emptyList();
        }

        String sourceGraphId = message.getSrcLoc().getGraphId();
        // 确保消息的源图ID与当前 PathTable 所属的图ID匹配
        if (!sourceGraphId.equals(graphDefinition.getGraphId())) {
            log.warn("PathTable: Message's source graph ID {} does not match current graph ID {}. Message: {}",
                    sourceGraphId, graphDefinition.getGraphId(), message.getId());
            return Collections.emptyList();
        }

        List<Location> destinations = new ArrayList<>();
        if (graphDefinition.getConnections() == null || graphDefinition.getConnections().isEmpty()) {
            log.warn("PathTable: GraphDefinition 或 Connections 为空，无法解析目的地。");
            return destinations;
        }

        for (ConnectionConfig connConfig : graphDefinition.getConnections()) {
            if (evaluateRoutingRule(message, connConfig.getRoutingRule())) {
                destinations.add(connConfig.getDestination());
            }
        }
        return destinations;
    }

    /**
     * 评估路由规则是否匹配消息。
     * 对应C语言中的ten_graph_instance_evaluate_routing_rule（现在由 PathTable 负责）。
     *
     * @param message 待评估的消息
     * @param rule    路由规则字符串 (例如: "msg.type == 'CMD_START_GRAPH'")
     * @return 如果规则匹配消息内容，则返回true，否则返回false
     */
    private boolean evaluateRoutingRule(Message message, String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return true; // 没有规则意味着总是匹配
        }

        String[] parts = rule.split("==");
        if (parts.length != 2) {
            log.warn("PathTable: 无效的路由规则格式: {}", rule);
            return false;
        }

        String left = parts[0].trim();
        String right = parts[1].trim().replace("'", "").replace("\"", ""); // 移除引号

        try {
            if (left.equals("msg.type")) {
                return message.getType().name().equals(right);
            } else if (left.startsWith("msg.properties.")) {
                String propKey = left.substring("msg.properties.".length());
                if (message.getProperties() != null) {
                    Object propValue = message.getProperties().get(propKey);
                    return propValue != null && propValue.toString().equals(right);
                }
                return false;
            } else if (left.startsWith("msg.src_loc.graph_id")) {
                return message.getSrcLoc() != null && message.getSrcLoc().getGraphId() != null
                        && message.getSrcLoc().getGraphId().equals(right);
            } else if (left.startsWith("msg.src_loc.node_id")) {
                return message.getSrcLoc() != null && message.getSrcLoc().getNodeId() != null
                        && message.getSrcLoc().getNodeId().equals(right);
            } else if (left.startsWith("msg.id")) {
                return message.getId() != null && message.getId().equals(right);
            }
        } catch (Exception e) {
            log.error("PathTable: 评估路由规则 {} 发生异常: {}", rule, e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * 评估条件是否匹配消息。
     * 对应C语言中的ten_graph_instance_evaluate_condition。
     * 这个方法与 evaluateRoutingRule 类似，但更通用，用于评估 Extension 内部的条件逻辑。
     *
     * @param message   待评估的消息
     * @param condition 条件字符串
     * @return 如果条件匹配消息内容，则返回true，否则返回false
     */
    public boolean evaluateCondition(Message message, String condition) { // 公开方法给 ExtensionContext 调用
        // 目前与 evaluateRoutingRule 逻辑相同，未来可以扩展以支持更复杂的条件表达式
        return evaluateRoutingRule(message, condition);
    }

    /**
     * 处理命令结果的消息返回策略。
     * 对应C语言中的ten_path_table_process_cmd_result_return_policy。
     *
     * @param pathOut       PathOut 实例
     * @param commandResult 接收到的命令结果
     * @throws CloneNotSupportedException 如果 CommandResult 无法克隆
     */
    public void handleResultReturnPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        ResultReturnPolicy policy = pathOut.getReturnPolicy();
        switch (policy) {
            case SINGLE_RESULT:
                if (pathOut.hasReceivedFinalCommandResult()) {
                    log.warn("PathOut {} 已收到最终结果，忽略重复的单个结果。", pathOut.getCommandId());
                    return;
                }
                pathOut.setCachedCommandResult(commandResult);
                pathOut.setHasReceivedFinalCommandResult(true);
                break;
            case STREAM_ALWAYS_RETURN:
                pathOut.setCachedCommandResult(commandResult);
                break;
            case STREAM_RETURN_FINAL_ONLY:
                if (commandResult.isFinal()) {
                    pathOut.setCachedCommandResult(commandResult);
                    pathOut.setHasReceivedFinalCommandResult(true);
                } else {
                    log.debug("PathOut {}: 策略为STREAM_RETURN_FINAL_ONLY，忽略中间结果。", pathOut.getCommandId());
                }
                break;
            default:
                log.warn("未知的结果返回策略: {}. PathOut: {}", policy, pathOut.getCommandId());
                break;
        }
    }

    /**
     * 清理与指定图实例相关的所有PathOut实例。
     * 当图实例停止时调用。
     *
     * @param graphId 要清理的图实例ID
     */
    public void cleanupPathsForGraph(String graphId) {
        if (graphId == null || graphId.isEmpty()) {
            log.warn("PathTable: 尝试清理空的或无效的Graph ID相关的路径");
            return;
        }

        log.info("PathTable: 清理与Graph {} 相关的PathOut实例", graphId);

        List<Long> commandIdsToCleanup = new ArrayList<>();
        for (Map.Entry<Long, PathOut> entry : outPaths.entrySet()) {
            PathOut pathOut = entry.getValue();
            if ((pathOut.getSourceLocation() != null && graphId.equals(pathOut.getSourceLocation().getGraphId())) ||
                    (pathOut.getDestinationLocation() != null
                            && graphId.equals(pathOut.getDestinationLocation().getGraphId()))) {
                commandIdsToCleanup.add(entry.getKey());
            }
        }

        if (commandIdsToCleanup.isEmpty()) {
            log.debug("PathTable: 没有发现与Graph {} 相关的PathOut需要清理", graphId);
            return;
        }

        log.debug("PathTable: 发现 {} 个与Graph {} 相关的PathOut需要清理", commandIdsToCleanup.size(), graphId);

        for (long commandId : commandIdsToCleanup) {
            Optional<PathOut> pathOutOpt = getOutPath(commandId);
            if (pathOutOpt.isPresent()) {
                PathOut pathOut = pathOutOpt.get();
                if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                    pathOut.getResultFuture()
                            .completeExceptionally(new RuntimeException("Graph " + graphId + " stopped."));
                }
                removeOutPath(commandId);
                log.debug("PathTable: 清理与停止图相关的PathOut: commandId={}", commandId);
            } else {
                log.warn("PathTable: 在图停止清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
            }
        }
    }
}