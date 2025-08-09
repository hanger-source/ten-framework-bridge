package com.tenframework.core.engine;

import com.tenframework.core.extension.ExtensionContext;
import com.tenframework.core.extension.Extension; // 引入 Extension
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.CommandMessage;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.path.PathManager;
// import com.tenframework.core.route.RouteManager; // 移除 RouteManager 导入

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ExtensionMessageDispatcher} 的默认实现，负责将消息分发到Engine内部的Extension。
 * 现在它通过 ExtensionContext 来管理和分发消息。
 */
@Slf4j
public class DefaultExtensionMessageDispatcher implements ExtensionMessageDispatcher {

    private final ExtensionContext extensionContext; // 引用 ExtensionContext
    private final PathManager pathManager; // 通过 ExtensionContext 获取
    // private final RouteManager routeManager; // 移除 RouteManager 字段
    private final ConcurrentMap<Long, CompletableFuture<Object>> commandFutures; // 从Engine传入

    public DefaultExtensionMessageDispatcher(ExtensionContext extensionContext,
            ConcurrentMap<Long, CompletableFuture<Object>> commandFutures) {
        this.extensionContext = extensionContext;
        this.pathManager = extensionContext.getPathManager(); // 从 ExtensionContext 获取
        // this.routeManager = extensionContext.getRouteManager(); // 移除 RouteManager
        // 初始化
        this.commandFutures = commandFutures;
    }

    @Override
    public void dispatchMessage(Message message) {
        // 1. 获取当前 Engine 的 ID (从 ExtensionContext 获取)
        String engineId = extensionContext.getEngine().getEngineId();

        // 2. 解析消息的实际目的地列表，委托给 PathManager (现在 PathManager 负责路由)
        List<Location> targetLocations = pathManager.resolveDestinations(message);

        // 如果消息自身包含目的地，也添加到目标列表中（通常用于直接指定 Extension 的情况）
        if (message.getDestLocs() != null) {
            targetLocations.addAll(message.getDestLocs());
        }

        if (targetLocations.isEmpty()) {
            log.warn("消息没有解析到任何目标Extension，或消息没有目的地: engineId={}, messageType={}, messageId={}",
                    engineId, message.getType(), message.getId());
            // 对于命令消息，如果无法分发，需要完成其 Future
            if (message instanceof CommandMessage command) {
                CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(new IllegalStateException("命令消息没有解析到任何目标Extension"));
                }
            }
            return;
        }

        // 为每个目标Extension分发消息
        for (Location targetLocation : targetLocations) {
            // 确保目的地是当前 Engine 所属的图，并获取 Extension
            if (!targetLocation.getGraphId().equals(extensionContext.getEngine().getGraphId())) {
                log.warn("DefaultExtensionMessageDispatcher: 消息目的地 {} 不属于当前 Engine {}，消息 {} 被丢弃。",
                        targetLocation.getGraphId(), engineId, message.getId());
                continue;
            }

            // 委托给 ExtensionContext 进行实际的分发
            // ExtensionContext 会负责查找 Extension 并调用其 onMessage 方法
            Message finalMessageToSend = message; // 默认使用原始消息
            if (targetLocations.size() > 1) {
                // 如果消息需要分发到多个目的地，则克隆消息以避免并发修改问题
                try {
                    finalMessageToSend = (Message) message.clone();
                } catch (CloneNotSupportedException e) {
                    log.error("克隆消息失败，无法分发到所有目标: messageType={}, messageId={}, targetLocation={}",
                            message.getType(), message.getId(), targetLocation, e);
                    // 对于命令消息，如果克隆失败，需要完成其 Future
                    if (message instanceof CommandMessage command) {
                        CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                        if (future != null && !future.isDone()) {
                            future.completeExceptionally(e);
                        }
                    }
                    continue; // 跳过当前目的地
                }
            }
            // 设置当前消息的目标 Location 为单例列表，简化 Extension 内部处理
            finalMessageToSend.setDestinationLocations(Collections.singletonList(targetLocation));

            try {
                extensionContext.dispatchMessageToExtension(finalMessageToSend);
                log.debug("DefaultExtensionMessageDispatcher: 消息 {} 已分发到 Extension {}.{}",
                        message.getId(), targetLocation.getGraphId(), targetLocation.getNodeId());

                // 如果是命令消息，且分发成功，但不是最终目的地（例如，传递给下一个 Extension），则不完成 Future
                // Future 的完成应该在 CommandResult 返回时处理
            } catch (Exception e) {
                log.error("DefaultExtensionMessageDispatcher: 分发消息到 Extension 失败. MessageId: {}, Target: {}.{}: {}",
                        message.getId(), targetLocation.getGraphId(), targetLocation.getNodeId(), e.getMessage(), e);
                // 如果是命令消息，分发失败，完成 Future
                if (message instanceof CommandMessage command) {
                    CompletableFuture<Object> future = commandFutures.remove(Long.parseLong(command.getId()));
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(e);
                    }
                }
            }
        }
    }
}