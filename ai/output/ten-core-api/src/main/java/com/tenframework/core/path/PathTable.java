package com.tenframework.core.path;

import com.tenframework.core.message.CommandResult;
import com.tenframework.core.Location;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 路径表，用于管理命令的生命周期和结果回溯。
 * 对应C语言中的ten_path_table_t结构。
 */
@Slf4j
public class PathTable {

    /**
     * 记录命令流入Engine的路径（key: commandId.mostSignificantBits, value: PathIn）
     * 使用Agrona Long2ObjectHashMap优化性能，避免UUID对象开销
     */
    private final Long2ObjectHashMap<PathIn> inPaths;

    /**
     * 记录命令从Engine发出到外部的路径（key: commandId.mostSignificantBits, value: PathOut）
     * 使用Agrona Long2ObjectHashMap优化性能，避免UUID对象开销
     */
    private final Long2ObjectHashMap<PathOut> outPaths;

    public PathTable() {
        this.inPaths = new Long2ObjectHashMap<>();
        this.outPaths = new Long2ObjectHashMap<>();
    }

    /**
     * 将UUID转换为long键值，使用mostSignificantBits作为主键
     * 这样可以避免UUID对象的开销，提高HashMap查找性能
     */
    private long uuidToLong(UUID uuid) {
        return uuid.getMostSignificantBits();
    }

    /**
     * 添加一个输入路径（当Engine接收到命令时）
     *
     * @param pathIn 输入路径对象
     */
    public void addInPath(PathIn pathIn) {
        if (pathIn == null || pathIn.getCommandId() == null) {
            log.warn("尝试添加空的或无效的PathIn到路径表");
            return;
        }
        long key = uuidToLong(pathIn.getCommandId());
        inPaths.put(key, pathIn);
        log.debug("PathIn已添加: commandId={}", pathIn.getCommandId());
    }

    /**
     * 添加一个输出路径（当Engine发出命令时）
     *
     * @param pathOut 输出路径对象
     */
    public void addOutPath(PathOut pathOut) {
        if (pathOut == null || pathOut.getCommandId() == null) {
            log.warn("尝试添加空的或无效的PathOut到路径表");
            return;
        }
        long key = uuidToLong(pathOut.getCommandId());
        outPaths.put(key, pathOut);
        log.debug("PathOut已添加: commandId={}", pathOut.getCommandId());
    }

    /**
     * 根据命令ID获取输入路径
     *
     * @param commandId 命令ID
     * @return 对应的PathIn，如果不存在则返回Optional.empty()
     */
    public Optional<PathIn> getInPath(UUID commandId) {
        long key = uuidToLong(commandId);
        return Optional.ofNullable(inPaths.get(key));
    }

    /**
     * 根据命令ID获取输出路径
     *
     * @param commandId 命令ID
     * @return 对应的PathOut，如果不存在则返回Optional.empty()
     */
    public Optional<PathOut> getOutPath(UUID commandId) {
        long key = uuidToLong(commandId);
        return Optional.ofNullable(outPaths.get(key));
    }

    /**
     * 移除一个输入路径（通常在命令处理完成或超时后）
     *
     * @param commandId 要移除的命令ID
     * @return 被移除的PathIn，如果不存在则返回Optional.empty()
     */
    public Optional<PathIn> removeInPath(UUID commandId) {
        long key = uuidToLong(commandId);
        Optional<PathIn> removed = Optional.ofNullable(inPaths.remove(key));
        removed.ifPresent(p -> log.debug("PathIn已移除: commandId={}", p.getCommandId()));
        return removed;
    }

    /**
     * 移除一个输出路径（通常在命令结果回溯完成或超时后）
     *
     * @param commandId 要移除的命令ID
     * @return 被移除的PathOut，如果不存在则返回Optional.empty()
     */
    public Optional<PathOut> removeOutPath(UUID commandId) {
        long key = uuidToLong(commandId);
        Optional<PathOut> removed = Optional.ofNullable(outPaths.remove(key));
        removed.ifPresent(p -> log.debug("PathOut已移除: commandId={}", p.getCommandId()));
        return removed;
    }

    /**
     * 清空所有路径，通常在Engine停止时调用
     */
    public void clear() {
        inPaths.clear();
        outPaths.clear();
        log.info("路径表已清空");
    }

    /**
     * 获取当前输入路径的数量
     */
    public int getInPathCount() {
        return inPaths.size();
    }

    /**
     * 获取当前输出路径的数量
     */
    public int getOutPathCount() {
        return outPaths.size();
    }

    /**
     * 创建并添加一个输出路径（当Engine发出命令时）
     *
     * @param commandId 命令ID
     * @param parentCommandId 父命令ID
     * @param commandName 命令名称
     * @param sourceLocation 来源位置
     * @param destinationLocation 目标位置
     * @param resultFuture 结果Future
     * @param returnPolicy 结果返回策略
     * @param channelId 可选的Channel ID，用于将结果回传给特定客户端
     * @return 新创建的PathOut对象
     */
    public PathOut createOutPath(UUID commandId, UUID parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<CommandResult> resultFuture,
            ResultReturnPolicy returnPolicy, String channelId) {
        PathOut pathOut = new PathOut(commandId, parentCommandId, commandName, sourceLocation,
                destinationLocation, resultFuture, returnPolicy, channelId);
        addOutPath(pathOut);
        return pathOut;
    }
}