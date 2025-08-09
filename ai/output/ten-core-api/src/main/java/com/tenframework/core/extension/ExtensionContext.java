package com.tenframework.core.extension;

import com.tenframework.core.app.App;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.path.PathTable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    private final AsyncExtensionEnv asyncExtensionEnv; // Extension 异步环境接口

    private String extensionName; // 当前 Extension 的名称
    private String graphId; // 当前 Extension 所属的 Graph ID
    private String appUri; // 当前 Extension 所属的 App URI

    public ExtensionContext(Engine engine, App app, PathTable pathTable, AsyncExtensionEnv asyncExtensionEnv) {
        this.engine = engine;
        this.app = app;
        this.pathTable = pathTable;
        this.asyncExtensionEnv = asyncExtensionEnv;

        this.extensionName = asyncExtensionEnv.getExtensionName();
        this.graphId = asyncExtensionEnv.getGraphId();
        this.appUri = asyncExtensionEnv.getAppUri();

        log.info("ExtensionContext created for Extension: {}, Graph: {}, App: {}", extensionName, graphId, appUri);
    }

    /**
     * 清理 ExtensionContext，例如在 Extension 停止时。
     */
    public void cleanup() {
        log.info("ExtensionContext for Extension {} cleaned up.", extensionName);
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
        String destExtensionName = message.getDestLocations() != null && !message.getDestLocations().isEmpty()
                ? message.getDestLocations().get(0).getNodeId() // 假设第一个 destLoc 的 nodeId 是目标 ExtensionName
                : null; // 或者根据业务逻辑决定默认值或抛出异常

        if (destExtensionName == null || !destExtensionName.equals(extensionName)) {
            log.warn("ExtensionContext {}: 消息 {} (Type: {}) 目的地不匹配或没有指定 Extension Name，无法分发。",
                    extensionName, msgId, msgType);
            return; // 消息不是发给这个 Extension 的
        }

        log.debug("ExtensionContext {}: 准备向 Extension 实例分发消息: ID={}, Type={}",
                extensionName, msgId, msgType);

        // 获取 Extension 实例
        Optional<Extension> extensionOptional = app.getRegisteredExtensionInstance(extensionName, graphId);
        if (extensionOptional.isEmpty()) {
            log.error("ExtensionContext {}: 未找到 Extension 实例 {}，无法分发消息 {} (Type: {})。",
                    extensionName, extensionName, msgId, msgType);
            return;
        }
        Extension extension = extensionOptional.get();

        // 根据消息类型分发
        switch (msgType) {
            case CMD:
                // 通用命令消息，由 Extension 的 onCommand 方法处理
                extension.onCommand((Command) message, asyncExtensionEnv);
                break;
            case CMD_RESULT:
                extension.onCommandResult((CommandResult) message, asyncExtensionEnv);
                break;
            case DATA:
                extension.onData((DataMessage) message, asyncExtensionEnv);
                break;
            case AUDIO_FRAME:
                extension.onAudioFrame((AudioFrameMessage) message, asyncExtensionEnv);
                break;
            case VIDEO_FRAME:
                extension.onVideoFrame((VideoFrameMessage) message, asyncExtensionEnv);
                break;
            // 其他具体命令类型（如 CMD_START_GRAPH, CMD_TIMER 等）已在 Engine 层面处理，不应分发到 Extension
            case CMD_CLOSE_APP:
            case CMD_START_GRAPH:
            case CMD_STOP_GRAPH:
            case CMD_TIMER:
            case CMD_TIMEOUT:
                log.warn("ExtensionContext {}: 收到不应由 Extension 直接处理的命令 {} (Type: {})，已忽略。",
                        extensionName, msgId, msgType);
                // 对于这些命令，可能需要返回一个不支持的结果，或者直接忽略
                if (message instanceof Command) {
                    Command command = (Command) message;
                    asyncExtensionEnv.sendResult(
                            CommandResult.fail(command.getId(), "Unsupported App/Engine level command for Extension."));
                }
                break;
            default:
                log.warn("ExtensionContext {}: 收到未知消息类型 {} (ID: {})，已忽略。",
                        extensionName, msgType, msgId);
                // 对于未知消息类型，如果它是一个 Command，也发送一个不支持的结果
                if (message instanceof Command) {
                    Command command = (Command) message;
                    asyncExtensionEnv
                            .sendResult(CommandResult.fail(command.getId(), "Unknown command type for Extension."));
                }
                break;
        }
    }
}