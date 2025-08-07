package com.tenframework.core.route;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.graph.ConnectionConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * 路由管理器，负责消息在Engine内部的图实例之间的路由。
 */
@Slf4j
public class RouteManager {

    private final ConcurrentMap<String, GraphInstance> graphInstances;
    // Removed: private final MessageSubmitter messageSubmitter;

    public RouteManager(ConcurrentMap<String, GraphInstance> graphInstances) {
        this.graphInstances = graphInstances;
        // Removed: this.messageSubmitter = messageSubmitter;
    }

    /**
     * 根据消息的来源和类型，解析出其在图中的所有潜在目标Extension位置。
     * 这个方法不执行消息分发，只负责路由决策。
     *
     * @param message 要解析的消息
     * @return 消息将路由到的目标Location列表。如果没有有效目标，则返回空列表。
     */
    public List<Location> resolveMessageDestinations(Message message) {
        if (message.getSourceLocation() == null || message.getSourceLocation().graphId() == null) {
            log.warn("RouteManager: Message source location or graph ID is null. Cannot resolve destinations: {}",
                    message.getName());
            return Collections.emptyList();
        }

        String sourceGraphId = message.getSourceLocation().graphId();
        GraphInstance currentGraphInstance = graphInstances.get(sourceGraphId);
        if (currentGraphInstance == null) {
            log.warn("RouteManager: Source graph instance not found for message. GraphId: {}, Message: {}",
                    sourceGraphId, message.getName());
            return Collections.emptyList();
        }

        // 使用GraphInstance现有的resolveDestinations方法
        // resolveDestinations方法已经实现了基于连接配置的路由逻辑
        List<String> targetExtensionNames = currentGraphInstance.resolveDestinations(message);

        // 将Extension名称转换为Location对象
        return targetExtensionNames.stream()
                .map(extensionName -> Location.builder()
                        .appUri(currentGraphInstance.getAppUri())
                        .graphId(currentGraphInstance.getGraphId())
                        .extensionName(extensionName)
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}