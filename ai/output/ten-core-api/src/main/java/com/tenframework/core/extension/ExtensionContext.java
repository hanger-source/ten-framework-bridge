package com.tenframework.core.extension;

import com.tenframework.core.app.App;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.path.PathManager;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

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
        activeExtensions = new ConcurrentHashMap<>();
        log.info("ExtensionContext created for Engine: {}", engine.getEngineId());
    }

    /**
     * 加载并初始化 Extension 实例。
     * 根据 ExtensionInfo 中的 addonName 来动态加载 Extension 类。
     * 这个方法应在 Engine 初始化运行时环境时被调用。
     *
     * @param extensionInfo 要加载的 Extension 的配置信息
     * @return 加载并初始化后的 Extension 实例
     */
    public Extension loadExtension(ExtensionInfo extensionInfo) {
        String addonName = extensionInfo.getExtensionAddonName();
        String extensionName = extensionInfo.getExtensionName(); // 这里的 extensionName 应该就是 nodeId

        Optional<Class<? extends Extension>> extensionClassOptional = engine.getApp().getRegisteredExtension(addonName);

        if (extensionClassOptional.isEmpty()) {
            log.error("ExtensionContext: 无法找到注册的 Extension Addon: {}. 无法加载 Extension: {}.", addonName, extensionName);
            return null;
        }

        Class<? extends Extension> extensionClass = extensionClassOptional.get();
        Extension extension = null;
        try {
            // 尝试使用带 ExtensionInfo 和 Engine 的构造函数 (如果存在)
            try {
                Constructor<? extends Extension> constructor = extensionClass.getConstructor(ExtensionInfo.class,
                        Engine.class);
                extension = constructor.newInstance(extensionInfo, engine);
            } catch (NoSuchMethodException e) {
                // 如果没有找到，尝试使用带 ExtensionInfo 的构造函数
                try {
                    Constructor<? extends Extension> constructor = extensionClass.getConstructor(ExtensionInfo.class);
                    extension = constructor.newInstance(extensionInfo);
                } catch (NoSuchMethodException e2) {
                    // 如果还没有找到，尝试使用无参数构造函数
                    extension = extensionClass.getConstructor().newInstance();
                }
            }

            // 统一设置 extensionName 和 appUri (通过 asyncExtensionEnv 获取)
            // 如果 Extension 是 BaseExtension 的子类，这些可能已经在其构造函数中处理
            if (extension instanceof BaseExtension) {
                ((BaseExtension) extension).setExtensionName(extensionName); // 设置 Extension 的名称
            }
            // TODO: 这里需要确保 Extension 能够获取到 AppUri 和 GraphId
            // AsyncExtensionEnv 应该能够提供这些信息，或者在 Extension 的 onConfigure 阶段设置

            // 调用生命周期方法
            extension.onConfigure(asyncExtensionEnv);
            extension.onInit(asyncExtensionEnv);
            extension.onStart(asyncExtensionEnv);

            activeExtensions.put(extensionName, extension);
            log.info("ExtensionContext: Extension {} (Addon: {}) 已成功加载和初始化。", extensionName, addonName);
            return extension;
        } catch (Exception e) {
            log.error("ExtensionContext: 加载或初始化 Extension {} (Addon: {}) 失败: {}", extensionName, addonName,
                    e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据 nodeId 卸载 Extension 实例并清理其资源。
     *
     * @param nodeId Extension 的唯一标识符。
     */
    public void unloadExtension(String nodeId) {
        Extension extension = activeExtensions.remove(nodeId);
        if (extension != null) {
            try {
                extension.onStop(asyncExtensionEnv);
                extension.onDeinit(asyncExtensionEnv);
                log.info("ExtensionContext: Extension {} 已卸载并清理。", nodeId);
            } catch (Exception e) {
                log.error("ExtensionContext: 卸载或清理 Extension {} 失败: {}", nodeId, e.getMessage(), e);
            }
        } else {
            log.warn("ExtensionContext: 尝试卸载不存在的 Extension: {}", nodeId);
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
                        log.debug("ExtensionContext: 消息 {} (Type: {}) 分发到 Extension {}.{}", message.getId(),
                                message.getType(),
                                destLoc.getGraphId(), destLoc.getNodeId());
                        // 根据消息类型调用 Extension 接口中细化的方法
                        switch (message.getType()) {
                            case CMD_START_GRAPH:
                            case CMD_STOP_GRAPH:
                            case CMD_ADD_EXTENSION_TO_GRAPH:
                            case CMD_REMOVE_EXTENSION_FROM_GRAPH:
                            case CMD_TIMER:
                            case CMD_TIMEOUT:
                            case CMD_CLOSE_APP:
                                targetExtension.onCommand((Command) message, asyncExtensionEnv);
                                break;
                            case DATA_MESSAGE: // Changed from DATA
                                targetExtension.onData((DataMessage) message, asyncExtensionEnv);
                                break;
                            case AUDIO_FRAME_MESSAGE: // Changed from AUDIO_FRAME
                                targetExtension.onAudioFrame((AudioFrameMessage) message, asyncExtensionEnv);
                                break;
                            case VIDEO_FRAME_MESSAGE: // Changed from VIDEO_FRAME
                                targetExtension.onVideoFrame((VideoFrameMessage) message, asyncExtensionEnv);
                                break;
                            case CMD_RESULT:
                                targetExtension.onCommandResult((CommandResult) message, asyncExtensionEnv);
                                break;
                            case INVALID:
                            default:
                                log.warn("ExtensionContext: 未知或无效消息类型 {} 无法分发到 Extension {}，消息 {} 被丢弃。",
                                        message.getType(), destLoc.getNodeId(), message.getId());
                                break;
                        }
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
                extension.onStop(asyncExtensionEnv);
                extension.onDeinit(asyncExtensionEnv); // 调用 Extension 的去初始化方法
            } catch (Exception e) {
                log.error("ExtensionContext: 清理 Extension {} 资源失败: {}", extension.getAppUri(), e.getMessage(),
                        e);
            }
        }
        activeExtensions.clear();

        // 关闭 AsyncExtensionEnv 的资源，例如虚拟线程池
        if (asyncExtensionEnv instanceof EngineAsyncExtensionEnv) {
            ((EngineAsyncExtensionEnv) asyncExtensionEnv).close();
        }
        log.info("ExtensionContext: 所有 Extension 资源清理完成。", engine.getEngineId());
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
}