package com.tenframework.core.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import com.tenframework.core.server.ChannelDisconnectedException;

/**
 * 引擎核心类，负责管理扩展和消息路由。
 */
public class Engine {

    /**
     * 维护channelId到相关联的Command ID集合的映射，用于断开连接时清理PathOut
     */
    private final ConcurrentMap<String, ConcurrentSkipListSet<UUID>> channelToCommandIdsMap;

    /**
     * 队列容量，默认64K条消息
     */
    private final int queueCapacity;

    /**
     * 扩展注册表，存储已注册的扩展。
     */
    private final ConcurrentHashMap<String, Extension> extensionRegistry;

    /**
     * 扩展性能指标，存储每个扩展的性能数据。
     */
    private final ConcurrentHashMap<String, ExtensionMetrics> extensionMetrics;

    /**
     * 构造函数，初始化引擎。
     *
     * @param queueCapacity 队列容量
     */
    public Engine(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        this.channelToCommandIdsMap = new ConcurrentHashMap<>();
        this.extensionRegistry = new ConcurrentHashMap<>();
        this.extensionMetrics = new ConcurrentHashMap<>();
    }

    /**
     * 注册扩展。
     *
     * @param extension 扩展实例
     * @return 注册成功返回true，否则返回false
     */
    public boolean registerExtension(Extension extension) {
        String extensionName = extension.getName();
        if (extensionRegistry.containsKey(extensionName)) {
            log.warn("Extension名称已存在: engineId={}, extensionName={}", engineId, extensionName);
            return false;
        }

        // 创建ExtensionContext
        // 传入 extension 实例以便 EngineExtensionContext 可以获取其活跃任务数
        EngineExtensionContext context = new EngineExtensionContext(extensionName, extension.getAppUri(), this,
                properties, extension);

        // 创建Extension性能指标
        // 之前使用了 Lombok 的 builder() 方法，但 ExtensionMetrics 已被重构
        ExtensionMetrics metrics = new ExtensionMetrics();
        extensionMetrics.put(extensionName, metrics);

        extensionRegistry.put(extensionName, extension);
        log.info("Extension注册成功: engineId={}, extensionName={}", engineId, extensionName);
        return true;
    }

    /**
     * 注销扩展。
     *
     * @param extensionName 扩展名称
     */
    public void unregisterExtension(String extensionName) {
        extensionRegistry.remove(extensionName);
        extensionMetrics.remove(extensionName);
        log.info("Extension注销成功: engineId={}, extensionName={}", engineId, extensionName);
    }

    /**
     * 获取扩展。
     *
     * @param extensionName 扩展名称
     * @return 扩展实例，如果不存在则返回null
     */
    public Extension getExtension(String extensionName) {
        return extensionRegistry.get(extensionName);
    }

    /**
     * 获取所有已注册的扩展。
     *
     * @return 扩展实例列表
     */
    public Collection<Extension> getExtensions() {
        return extensionRegistry.values();
    }

    /**
     * 处理消息。
     *
     * @param message 消息
     */
    public void processMessage(Message message) {
        // 根据消息类型路由到不同的扩展
        String extensionName = routeMessage(message);
        if (extensionName != null) {
            Extension extension = extensionRegistry.get(extensionName);
            if (extension != null) {
                try {
                    extension.processMessage(message);
                } catch (Exception e) {
                    log.error("处理消息失败: engineId={}, extensionName={}, messageId={}", engineId, extensionName,
                            message.getId(), e);
                    // 可以根据需要进行重试或记录错误
                }
            } else {
                log.warn("未找到扩展: engineId={}, extensionName={}", engineId, extensionName);
            }
        } else {
            log.warn("无法路由消息: engineId={}, messageId={}", engineId, message.getId());
        }
    }

    /**
     * 路由消息到合适的扩展。
     *
     * @param message 消息
     * @return 路由到的扩展名称，如果无法路由则返回null
     */
    private String routeMessage(Message message) {
        // 简单的路由逻辑：根据消息类型或内容匹配扩展
        // 实际应用中可能需要更复杂的规则，例如正则表达式、机器学习模型等
        String messageType = message.getType();
        for (Map.Entry<String, Extension> entry : extensionRegistry.entrySet()) {
            String extensionName = entry.getKey();
            Extension extension = entry.getValue();
            if (extension.canProcessMessage(message)) {
                return extensionName;
            }
        }
        return null;
    }

    /**
     * 处理连接断开。
     *
     * @param channelId 通道ID
     */
    public void handleChannelDisconnected(String channelId) {
        ConcurrentSkipListSet<UUID> commandIds = channelToCommandIdsMap.remove(channelId);
        if (commandIds != null) {
            commandIds.forEach(commandId -> {
                // 根据commandId找到对应的Message，并从队列中移除
                // 实际应用中需要一个MessageManager来管理Message的生命周期
                // 这里只是示例，实际需要更复杂的逻辑
                log.info("清理断开连接的通道消息: engineId={}, channelId={}, commandId={}", engineId, channelId, commandId);
            });
        }
    }

    /**
     * 获取引擎ID。
     *
     * @return 引擎ID
     */
    public String getEngineId() {
        return engineId;
    }

    /**
     * 获取扩展性能指标。
     *
     * @param extensionName 扩展名称
     * @return 扩展性能指标，如果不存在则返回null
     */
    public ExtensionMetrics getExtensionMetrics(String extensionName) {
        return extensionMetrics.get(extensionName);
    }

    /**
     * 获取所有扩展性能指标。
     *
     * @return 扩展性能指标列表
     */
    public Collection<ExtensionMetrics> getExtensionMetrics() {
        return extensionMetrics.values();
    }
}
