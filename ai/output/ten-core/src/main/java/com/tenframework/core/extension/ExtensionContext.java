package com.tenframework.core.extension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.app.App;
import com.tenframework.core.engine.CommandSubmitter;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.path.PathTable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * `ExtensionContext` 封装了 `Extension` 的运行时环境。
 * 它提供了 `Extension` 与 `Engine` 交互的接口，包括消息发送、命令提交和属性操作。
 * 每个 Extension 实例都有一个对应的 ExtensionContext。
 * <p>
 * 类似于 C 语言中的 `ten_extension_context_t`。
 */
@Slf4j
@Getter
public class ExtensionContext {

    private final Engine engine; // 引用所属的 Engine 实例
    private final App app; // 引用所属的 App 实例
    private final PathTable pathTable; // 消息路由表
    private final MessageSubmitter messageSubmitter; // 用于向 Engine 提交消息
    private final CommandSubmitter commandSubmitter; // 用于向 Engine 提交命令
    private final Map<String, Extension> loadedExtensions; // 存储已加载和配置的 Extension 实例
    private String extensionName; // 当前 Extension 的名称
    private String graphId; // 当前 Extension 所属的 Graph ID
    private String appUri; // 当前 Extension 所属的 App URI
    private AsyncExtensionEnv currentExtensionEnv; // 当前 Extension 专属的 AsyncExtensionEnv 实例

    public ExtensionContext(Engine engine, App app, PathTable pathTable, MessageSubmitter messageSubmitter,
            CommandSubmitter commandSubmitter) {
        this.engine = engine;
        this.app = app;
        this.pathTable = pathTable;
        this.messageSubmitter = messageSubmitter;
        this.commandSubmitter = commandSubmitter;
        loadedExtensions = new ConcurrentHashMap<>(); // 初始化 Map

        log.info("ExtensionContext created for Engine: {}, App: {}", engine.getEngineId(), app.getAppUri());
    }

    /**
     * 为一个特定的 Extension 创建并设置其专属的 AsyncExtensionEnv。
     *
     * @param extensionName     Extension 的名称。
     * @param extensionType     Extension 的类型。
     * @param initialProperties Extension 的初始属性。
     * @return 创建的 AsyncExtensionEnv 实例。
     */
    public AsyncExtensionEnv createExtensionEnv(String extensionName, String extensionType,
            Map<String, Object> initialProperties) {
        this.extensionName = extensionName; // 设置当前上下文的 Extension 名称
        appUri = app.getAppUri();
        graphId = engine.getEngineId(); // EngineId 即 GraphId

        currentExtensionEnv = new EngineAsyncExtensionEnv(
                extensionName,
                extensionType,
            appUri,
            graphId,
                messageSubmitter,
                commandSubmitter,
                initialProperties);

        try {
            Optional<Class<? extends Extension>> extensionClassOptional = app.getRegisteredExtension(extensionName);
            if (extensionClassOptional.isPresent()) {
                Class<? extends Extension> extensionClass = extensionClassOptional.get();
                Extension extensionInstance = extensionClass.getDeclaredConstructor().newInstance();
                extensionInstance.onConfigure(currentExtensionEnv); // 配置 Extension
                loadedExtensions.put(extensionName, extensionInstance); // 存储实例
                log.info("ExtensionContext {}: 为 Extension '{}' (Type: {}) 创建了 AsyncExtensionEnv 并实例化配置成功。",
                        extensionName, extensionName, extensionType);
            } else {
                log.warn("ExtensionContext {}: 未注册的 Extension: {} (Type: {})，无法实例化和配置。", extensionName, extensionName,
                        extensionType);
            }
        } catch (Exception e) {
            log.error("ExtensionContext {}: 实例化或配置 Extension '{}' (Type: {}) 失败: {}",
                    extensionName, extensionName, extensionType, e.getMessage(), e);
        }

        return currentExtensionEnv;
    }

    /**
     * 清理 ExtensionContext，例如在 Extension 停止时。
     */
    public void cleanup() {
        log.info("ExtensionContext for Extension {} cleaned up.", extensionName);
        if (currentExtensionEnv != null) {
            currentExtensionEnv.close(); // 关闭 Extension 的专属环境
            currentExtensionEnv = null;
        }
        // 清理所有加载的 Extension 实例
        loadedExtensions.values().forEach(ext -> {
            try {
                // 在这里调用 ext.onDeinit()，确保传入正确的 AsyncExtensionEnv
                // 这里 ext.getAppUri() 用于判断是否有效，因为 env.getGraphId() 可能会在清理时为空
                ext.onDeinit(ext.getAppUri() != null ? currentExtensionEnv : null);
            } catch (Exception e) {
                log.error("Extension {} deinitialization failed: {}", ext.getClass(), e.getMessage(), e);
            }
        });
        loadedExtensions.clear();
    }

    /**
     * 根据消息的目的地路由消息到相应的 Extension。
     * 这是 Engine 消息分发到 Extension 的入口点。
     *
     * @param message 待分发的消息。
     */
    public void dispatchMessageToExtension(Message message) {
        String msgId = message.getId();
        MessageType msgType = message.getType();
        String destExtensionName = message.getDestLocs() != null && !message.getDestLocs().isEmpty()
                ? message.getDestLocs().get(0).getNodeId() // 假设第一个 destLoc 的 nodeId 是目标 ExtensionName
                : null;

        if (destExtensionName == null || !destExtensionName.equals(extensionName)) { // 确保是发给当前 ExtensionContext 所属的
                                                                                     // Extension
            log.warn("ExtensionContext {}: 消息 {} (Type: {}) 目的地不匹配或没有指定 Extension Name，无法分发。",
                    extensionName, msgId, msgType);
            return;
        }

        log.debug("ExtensionContext {}: 准备向 Extension 实例分发消息: ID={}, Type={}",
                extensionName, msgId, msgType);

        // 从已加载的 Extension 实例中获取
        Extension extension = loadedExtensions.get(extensionName);
        if (extension == null) {
            log.error("ExtensionContext {}: 未找到 Extension 实例 {}，无法分发消息 {} (Type: {})。",
                    extensionName, extensionName, msgId, msgType);
            return;
        }

        // 确保 AsyncExtensionEnv 存在，并将其传递给 Extension 方法
        if (currentExtensionEnv == null) {
            log.error("ExtensionContext {}: AsyncExtensionEnv 未初始化，无法分发消息 {} (Type: {})。",
                    extensionName, msgId, msgType);
            // 尝试重新创建？或者抛出异常？这里简单返回
            return;
        }

        // 根据消息类型分发
        switch (msgType) {
            case CMD:
                extension.onCommand((Command) message, currentExtensionEnv);
                break;
            case CMD_RESULT:
                extension.onCommandResult((CommandResult) message, currentExtensionEnv);
                break;
            case DATA:
                extension.onData((DataMessage) message, currentExtensionEnv);
                break;
            case AUDIO_FRAME:
                extension.onAudioFrame((AudioFrameMessage) message, currentExtensionEnv);
                break;
            case VIDEO_FRAME:
                extension.onVideoFrame((VideoFrameMessage) message, currentExtensionEnv);
                break;
            case CMD_CLOSE_APP:
            case CMD_START_GRAPH:
            case CMD_STOP_GRAPH:
            case CMD_TIMER:
            case CMD_TIMEOUT:
                log.warn("ExtensionContext {}: 收到不应由 Extension 直接处理的命令 {} (Type: {})，已忽略。",
                        extensionName, msgId, msgType);
                if (message instanceof Command) {
                    Command command = (Command) message;
                    currentExtensionEnv.sendResult(
                            CommandResult.fail(command.getId(), "Unsupported App/Engine level command for Extension."));
                }
                break;
            default:
                log.warn("ExtensionContext {}: 收到未知消息类型 {} (ID: {})，已忽略。",
                        extensionName, msgType, msgId);
                if (message instanceof Command) {
                    Command command = (Command) message;
                    currentExtensionEnv
                            .sendResult(CommandResult.fail(command.getId(), "Unknown command type for Extension."));
                }
                break;
        }
    }
}