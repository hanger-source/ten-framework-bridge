package com.tenframework.core.extension;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.Message;
import com.tenframework.core.path.PathManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExtensionContext 封装了 Extension 的运行时环境和生命周期管理。
 * 它为 Extension 提供与 Engine 交互的能力，并管理 Extension 实例的加载和消息分发。
 * 对齐C语言中的ten_extension_context_t结构体。
 */
@Slf4j
public class ExtensionContext { // 不再实现 AsyncExtensionEnv 接口

    private final Engine engine; // 引用所属的 Engine
    private final GraphDefinition graphDefinition; // 引用 Engine 正在运行的 Graph 定义
    private final PathManager pathManager; // 引用 PathManager
    private final AsyncExtensionEnv asyncExtensionEnv; // 新增：引用 AsyncExtensionEnv 实例

    // 管理所有活跃的 Extension 实例，key 为 Extension 的 nodeId/instanceName
    private final Map<String, Extension> activeExtensions;

    public ExtensionContext(Engine engine, GraphDefinition graphDefinition, PathManager pathManager,
            AsyncExtensionEnv asyncExtensionEnv) { // 构造函数接收 AsyncExtensionEnv
        this.engine = engine;
        this.graphDefinition = graphDefinition;
        this.pathManager = pathManager;
        this.asyncExtensionEnv = asyncExtensionEnv;
        this.activeExtensions = new ConcurrentHashMap<>();
        log.info("ExtensionContext created for Engine: {}", engine.getEngineId());
    }

    /**
     * 加载并初始化 Extension 实例。
     * 根据 GraphDefinition 中的 ExtensionInfo 来创建 Extension。
     * 这个方法应在 Engine 初始化运行时环境时被调用。
     *
     * @param extensionInfo 要加载的 Extension 的配置信息
     * @return 加载并初始化后的 Extension 实例
     */
    public Extension loadExtension(ExtensionInfo extensionInfo) {
        // TODO: 根据 extensionInfo.getExtensionAddonName() 动态加载 Extension 类
        // 这是一个简化的占位符实现，实际需要一个 ExtensionFactory 或类加载器
        try {
            // 假设我们有一个简单的 Extension 构造函数，需要 ExtensionContext 和 ExtensionInfo
            // ClientConnectionExtension 是一个示例，实际应该根据 addonName 动态判断
            if ("client_connection".equals(extensionInfo.getExtensionAddonName())) {
                ClientConnectionExtension extension = new ClientConnectionExtension(this.asyncExtensionEnv,
                        extensionInfo); // 传递 asyncExtensionEnv
                activeExtensions.put(extensionInfo.getLoc().getNodeId(), extension); // 使用 nodeId 作为 key
                log.info("ExtensionContext: Extension {} (type: {}) 已加载和初始化。",
                        extensionInfo.getLoc().getNodeId(), extensionInfo.getExtensionAddonName());
                return extension;
            } else {
                log.warn("ExtensionContext: 未知的 Extension Addon Name: {}. 无法加载。",
                        extensionInfo.getExtensionAddonName());
                return null;
            }
        } catch (Exception e) {
            log.error("ExtensionContext: 加载或初始化 Extension 失败 (Addon: {}): {}",
                    extensionInfo.getExtensionAddonName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据 nodeId 获取活跃的 Extension 实例。
     *
     * @param nodeId Extension 的唯一标识符 (通常是 Location.nodeId)
     * @return 对应的 Extension 实例，如果不存在则返回 null
     */
    public Extension getExtension(String nodeId) {
        return activeExtensions.get(nodeId);
    }

    /**
     * 将消息分发到目标 Extension。
     * 这个方法通常由 ExtensionMessageDispatcher 调用。
     *
     * @param message 待分发的消息
     */
    public void dispatchMessageToExtension(Message message) {
        // 根据消息的目的地 (destLocs) 找到对应的 Extension 并分发消息
        if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
            for (Location destLoc : message.getDestLocs()) {
                // 确保是当前 Engine 内部的 Extension
                if (destLoc.getGraphId() != null && destLoc.getGraphId().equals(engine.getGraphId())) {
                    Extension targetExtension = getExtension(destLoc.getNodeId());
                    if (targetExtension != null) {
                        log.debug("ExtensionContext: 消息 {} 分发到 Extension {}.{}", message.getId(),
                                destLoc.getGraphId(), destLoc.getNodeId());
                        targetExtension.onMessage(message); // 调用 Extension 的消息处理方法
                    } else {
                        log.warn("ExtensionContext: 无法找到目标 Extension: {}.{}，消息 {} 被丢弃。",
                                destLoc.getGraphId(), destLoc.getNodeId(), message.getId());
                    }
                } else {
                    log.warn("ExtensionContext: 消息目的地 {} 不属于当前 Engine {}，消息 {} 被丢弃。",
                            destLoc.getGraphId(), engine.getEngineId(), message.getId());
                }
            }
        } else {
            log.warn("ExtensionContext: 消息 {} 没有目的地，无法分发。", message.getId());
        }
    }

    /**
     * 清理所有活跃 Extension 的资源。
     * 在 Engine 关闭时被调用。
     */
    public void cleanup() {
        log.info("ExtensionContext: 正在清理所有 Extension 资源...");
        for (Extension extension : activeExtensions.values()) {
            try {
                extension.cleanup(); // 调用 Extension 的清理方法
            } catch (Exception e) {
                log.error("ExtensionContext: 清理 Extension {} 资源失败: {}", extension.getLoc().getNodeId(), e.getMessage(),
                        e);
            }
        }
        activeExtensions.clear();

        // 关闭 AsyncExtensionEnv 的资源，例如虚拟线程池
        if (asyncExtensionEnv instanceof EngineAsyncExtensionEnv) {
            ((EngineAsyncExtensionEnv) asyncExtensionEnv).close();
        }
        log.info("ExtensionContext: 所有 Extension 资源清理完成。");
    }

    // 提供给外部访问核心组件的 getter
    public Engine getEngine() {
        return engine;
    }

    public GraphDefinition getGraphDefinition() {
        return graphDefinition;
    }

    public PathManager getPathManager() {
        return pathManager;
    }

    public AsyncExtensionEnv getAsyncExtensionEnv() { // 新增 getter
        return asyncExtensionEnv;
    }

    // TODO: 实现 ten_env_t 中其他提供给 Extension 的功能
    // 例如：post_msg_to_self, post_msg_to_engine, post_cmd_to_engine, post_timeout
}