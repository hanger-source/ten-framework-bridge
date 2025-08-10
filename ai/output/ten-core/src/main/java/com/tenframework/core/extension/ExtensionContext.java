package com.tenframework.core.extension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.app.App;
import com.tenframework.core.engine.CommandSubmitter;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.extension.submitter.ExtensionCommandSubmitter;
import com.tenframework.core.extension.submitter.ExtensionMessageSubmitter;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.MessageConversionContext;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.path.PathTable;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.tenenv.TenEnvProxy;
import com.tenframework.core.util.MessageConverter;
import com.tenframework.core.util.ReflectionUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;

/**
 * 管理 Engine 中 Extension 的生命周期和交互。
 * 这是 Engine 与其加载的 Extension 之间交互的主要接口。
 */
@Slf4j
public class ExtensionContext implements Agent, ExtensionCommandSubmitter, ExtensionMessageSubmitter {

    @Getter
    private final Engine engine; // 引擎引用
    private final App app; // 应用引用
    @Getter
    private final PathTable pathTable;
    private final MessageSubmitter engineMessageSubmitter;
    private final CommandSubmitter engineCommandSubmitter;

    // 存储所有已加载的 Extension 实例，key 为 extensionId
    private final ConcurrentHashMap<String, Extension> extensions;

    // 新增：存储每个 Extension 对应的 TenEnvProxy 实例
    private final ConcurrentHashMap<String, TenEnvProxy<ExtensionEnvImpl>> extensionProxies;

    // 存储 Extension 的 Method 映射，优化反射调用性能
    private final ConcurrentHashMap<Class<? extends Extension>, Map<String, Method>> extensionMethodCache
        = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ExtensionInfo> extensionInfos; // Added to store ExtensionInfo

    @Getter
    private final String roleName;

    public ExtensionContext(Engine engine, App app, PathTable pathTable, MessageSubmitter engineMessageSubmitter,
        CommandSubmitter engineCommandSubmitter) {
        this.engine = Objects.requireNonNull(engine, "Engine must not be null.");
        this.app = Objects.requireNonNull(app, "App must not be null.");
        this.pathTable = Objects.requireNonNull(pathTable, "PathTable must not be null.");
        this.engineMessageSubmitter = Objects.requireNonNull(engineMessageSubmitter,
            "Engine message submitter must not be null.");
        this.engineCommandSubmitter = Objects.requireNonNull(engineCommandSubmitter,
            "Engine command submitter must not be null.");

        extensions = new ConcurrentHashMap<>();
        extensionProxies = new ConcurrentHashMap<>();
        extensionInfos = new ConcurrentHashMap<>(); // Initialize extensionInfos
        roleName = "ExtensionContext-%s".formatted(engine.getGraphId());

        log.info("ExtensionContext created for Engine: {}", engine.getGraphId());
    }

    /**
     * 加载并初始化一个 Extension 实例。
     *
     * @param extensionId   Extension 的唯一 ID。
     * @param extensionType Extension 的类型。
     * @param config        Extension 的配置。
     * @param engineRunloop Engine 的 Runloop，用于 Extension 的生命周期事件和消息处理
     * @param extInfo       Extension 的详细信息，包含消息转换配置。
     * @return 加载的 Extension 实例。
     */
    public Extension loadExtension(String extensionId, String extensionType, GraphConfig config,
        Runloop engineRunloop, ExtensionInfo extInfo) { // Added ExtensionInfo parameter
        if (extensions.containsKey(extensionId)) {
            log.warn("Extension with ID {} already loaded.", extensionId);
            return extensions.get(extensionId);
        }

        try {
            // 1. 创建 Extension 实例
            Class<? extends Extension> extensionClass = findExtensionClass(extensionType);
            Extension extension = ReflectionUtils.newInstance(extensionClass);

            // 这里简化为直接传入 Engine 的 Runloop，后续可根据 C/Python 对齐情况调整
            // 创建 ExtensionEnvImpl 实例
            ExtensionEnvImpl extensionEnv = new ExtensionEnvImpl(extensionId, extension, config, app.getAppUri(),
                engine.getGraphId(), this, this, engineRunloop);
            TenEnvProxy<ExtensionEnvImpl> proxy = new TenEnvProxy<>(engineRunloop, extensionEnv,
                "Extension-%s".formatted(extensionId)); // Corrected generic type
            extensionProxies.put(extensionId, proxy);
            extensionInfos.put(extensionId, extInfo); // Store ExtensionInfo
            log.info("TenEnvProxy created for Extension {}.", extensionId);

            // 3. 初始化 Extension (传入 TenEnv)
            proxy.targetRunloop().postTask(() -> {
                try {
                    extension.init(extensionId, config, extensionEnv); // 传入 ExtensionEnvImpl
                    log.info("Extension {} (Type: {}) loaded for Engine {}.", extensionId, extensionType,
                        engine.getGraphId());

                    // 4. 调用 Extension 的生命周期回调（传入 TenEnvProxy）
                    proxy.postTask(() -> {
                        try {
                            extension.onConfigure(extensionEnv);
                            extension.onInit(extensionEnv);
                            extension.onStart(extensionEnv);
                        } catch (Exception e) {
                            log.error("Error during Extension {} lifecycle callbacks (onConfigure/onInit/onStart): {}",
                                extensionId, e.getMessage(), e);
                        }
                    });

                    // 5. 将 Extension 加入管理列表
                    extensions.put(extensionId, extension);
                    log.info("Extension {} (Type: {}) loaded for Engine {}.", extensionId, extensionType,
                        engine.getGraphId());
                } catch (Exception e) {
                    log.error("Error initializing extension {}: {}", extensionId, e.getMessage(), e);
                }
            });

            return extension;
        } catch (Exception e) {
            log.error("Failed to load or initialize extension {} (Type: {}) for Engine {}: {}",
                extensionId, extensionType, engine.getGraphId(), e.getMessage(), e);
            throw new RuntimeException("Extension loading failed: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 卸载并销毁一个 Extension 实例。
     *
     * @param extensionId Extension 的唯一 ID。
     */
    public void unloadExtension(String extensionId) {
        Extension extension = extensions.remove(extensionId);
        if (extension != null) {
            extensionInfos.remove(extensionId); // Remove ExtensionInfo
            try {
                // 调用 Extension 的 destroy 方法，传入对应的 TenEnvProxy
                TenEnvProxy<ExtensionEnvImpl> proxy = extensionProxies.remove(extensionId);
                if (proxy != null) {
                    ExtensionEnvImpl extensionEnv = proxy.targetEnv();
                    // 1. 调用 Extension 的停止和去初始化回调
                    // 这些任务也应通过 proxy 的 runloop 提交，以保持线程安全和一致性
                    proxy.postTask(() -> {
                        try {
                            extension.onStop(extensionEnv);
                            extension.onDeinit(extensionEnv);
                        } catch (Exception e) {
                            log.error("Error during Extension {} lifecycle callbacks (onStop/onDeinit): {}",
                                extensionId, e.getMessage(), e);
                        }
                    });
                    // 2. 调用 destroy 方法（可以在 onDeinit 之后同步调用，或者也作为 task 提交）
                    // 这里选择在 postTask 外部同步调用，或者依赖 onDeinit 内部的清理
                    extension.destroy(extensionEnv); // 传入 extensionEnv
                    // Note: 考虑代理生命周期协调：C++ 端会在所有 proxy 释放后通知 attached_target。
                    // Java 端目前由 GC 自动处理，并依赖显式 close()。未来可考虑更精细的生命周期通知机制。
                    log.info("TenEnvProxy for Extension {} removed.", extensionId);
                } else {
                    log.warn("No TenEnvProxy found for Extension {}. Direct destroy call.", extensionId);
                    extension.destroy(null); // 如果没有代理，传入 null 或进行适当处理
                }
                log.info("Extension {} unloaded.", extensionId);
            } catch (Exception e) {
                log.error("Error destroying extension {}: {}", extensionId, e.getMessage(), e);
            }
        } else {
            log.warn("Extension with ID {} not found.", extensionId);
        }
    }

    /**
     * 卸载所有已加载的 Extension 实例。
     */
    public void unloadAllExtensions() {
        log.info("ExtensionContext: Unloading all extensions for Engine {}.", engine.getGraphId());
        // 创建一个副本以避免并发修改异常
        List<String> extensionIds = new ArrayList<>(extensions.keySet());
        for (String extensionId : extensionIds) {
            unloadExtension(extensionId);
        }
        log.info("ExtensionContext: All extensions unloaded for Engine {}.", engine.getGraphId());
    }

    /**
     * 根据 Extension 类型名称查找对应的 Class。
     *
     * @param extensionType Extension 的类型名称
     * @return 对应的 Class 对象。
     * @throws ClassNotFoundException 如果找不到对应的 Class。
     */
    private Class<? extends Extension> findExtensionClass(String extensionType) throws ClassNotFoundException {
        // 假设 Extension 类都在 classpath 中
        // Note: 更复杂的 Extension 发现机制，例如通过插件系统，是未来可考虑的增强。
        Class<?> clazz = Class.forName(extensionType);
        if (Extension.class.isAssignableFrom(clazz)) {
            return (Class<? extends Extension>)clazz;
        } else {
            throw new IllegalArgumentException("Class %s is not an Extension.".formatted(extensionType));
        }
    }

    /**
     * 将消息提交给 Extension 进行处理。
     * 这是 Engine 消息分发到 Extension 的入口点，消息将在 Extension 专属线程上处理。
     *
     * @param message           待分发的消息。
     * @param targetExtensionId 目标 Extension 的 ID。
     */
    public void dispatchMessageToExtension(Message message, String targetExtensionId) {
        if (targetExtensionId == null || targetExtensionId.isEmpty()) {
            log.warn("ExtensionContext: 目标 Extension ID 为空或 null，无法分发消息 {}。跳过分发。", message.getId());
            return;
        }

        Extension extension = extensions.get(targetExtensionId);
        if (extension == null) {
            log.error("ExtensionContext: 未找到 Extension 实例 {}，无法分发消息 {} (Type: {}).",
                targetExtensionId, message.getId(), message.getType());
            // 如果是命令，需要返回一个失败的 CommandResult
            if (message instanceof Command command) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(command.getId(),
                        "Extension instance not found: %s".formatted(targetExtensionId)),
                    "Engine");
            }
            return;
        }

        TenEnvProxy<ExtensionEnvImpl> proxy = extensionProxies.get(targetExtensionId);
        if (proxy == null) {
            log.error("ExtensionContext: TenEnvProxy 未初始化，无法分发消息 {} (Type: {}) 给 Extension {}.",
                message.getId(), message.getType(), targetExtensionId);
            if (message instanceof Command command) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(command.getId(),
                        "TenEnvProxy not initialized for Extension: %s".formatted(targetExtensionId)),
                    "Engine");
            }
            return;
        }

        // --- 消息转换逻辑开始 ---
        Message processedMessage = message;
        ExtensionInfo targetExtInfo = extensionInfos.get(targetExtensionId);
        if (targetExtInfo != null && targetExtInfo.getMsgConversionContexts() != null) {
            for (MessageConversionContext context : targetExtInfo.getMsgConversionContexts()) {
                Message converted = MessageConverter.convertMessage(processedMessage, context);
                if (converted != processedMessage) { // 如果消息被转换了
                    processedMessage = converted;
                    log.debug("ExtensionContext: 消息 {} 被转换。", processedMessage.getId());
                }
            }
        }
        // --- 消息转换逻辑结束 ---

        // 将消息提交到 Extension 专属的 Runloop 线程上执行相应的 onXXX 回调
        final Message finalProcessedMessage = processedMessage;
        ExtensionEnvImpl extensionEnv = proxy.targetEnv(); // 获取实际的 ExtensionEnvImpl 实例
        if (extensionEnv == null) {
            log.error("ExtensionContext: 无法获取 ExtensionEnvImpl 实例，无法分发消息 {}。",
                finalProcessedMessage.getId());
            if (finalProcessedMessage instanceof Command command) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(command.getId(), "ExtensionEnvImpl not available."),
                    "Engine");
            }
            return;
        }

        extensionEnv.getAttachedRunloop().postTask(() -> {
            try {
                // 在 Extension 自己的 Runloop 线程中根据消息类型调用对应的 onXXX 回调
                switch (finalProcessedMessage.getType()) {
                    case CMD:
                        extension.onCmd(extensionEnv, (Command)finalProcessedMessage);
                        break;
                    case CMD_RESULT:
                        extension.onCmdResult(extensionEnv, (CommandResult)finalProcessedMessage);
                        break;
                    case DATA:
                        extension.onDataMessage(extensionEnv, (DataMessage)finalProcessedMessage);
                        break;
                    case AUDIO_FRAME:
                        extension.onAudioFrame(extensionEnv, (AudioFrameMessage)finalProcessedMessage);
                        break;
                    case VIDEO_FRAME:
                        extension.onVideoFrame(extensionEnv, (VideoFrameMessage)finalProcessedMessage);
                        break;
                    case CMD_CLOSE_APP:
                    case CMD_START_GRAPH:
                    case CMD_STOP_GRAPH:
                    case CMD_TIMER:
                    case CMD_TIMEOUT:
                        log.warn("ExtensionContext: 收到不应由 Extension 直接处理的命令 {} (Type: {})，已忽略。",
                            finalProcessedMessage.getId(), finalProcessedMessage.getType());
                        if (finalProcessedMessage instanceof Command command) {
                            this.routeCommandResultFromExtension(
                                CommandResult.fail(command.getId(),
                                    "Unsupported App/Engine level command for Extension."),
                                "Engine");
                        }
                        break;
                    default:
                        log.warn("ExtensionContext: 收到未知消息类型 {} (ID: {})，已忽略。",
                            finalProcessedMessage.getType(), finalProcessedMessage.getId());
                        if (finalProcessedMessage instanceof Command command) {
                            this.routeCommandResultFromExtension(
                                CommandResult.fail(command.getId(), "Unknown command type for Extension."),
                                "Engine");
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("ExtensionContext: Extension {} 处理消息 {} 时发生异常: {}",
                    targetExtensionId, finalProcessedMessage.getId(), e.getMessage(), e);
                if (finalProcessedMessage instanceof Command command) {
                    this.routeCommandResultFromExtension(
                        CommandResult.fail(command.getId(),
                            "Error processing message in Extension: %s".formatted(e.getMessage())),
                        "Engine");
                }
            }
        });
    }

    /**
     * 将命令提交给 Extension 进行处理。
     * 这是 Engine 消息分发到 Extension 的入口点，消息将在 Extension 专属线程上处理。
     * 注意：这里假设命令是点对点发送给特定的 Extension，而不是广播。
     *
     * @param command           要处理的命令。
     * @param targetExtensionId 目标 Extension 的 ID。
     */
    public void dispatchCommandToExtension(Command command, String targetExtensionId) {
        if (targetExtensionId == null || targetExtensionId.isEmpty()) {
            log.warn("ExtensionContext: 目标 Extension ID 为空或 null，无法分发命令 {}。跳过分发。", command.getId());
            return;
        }

        Extension extension = extensions.get(targetExtensionId);
        if (extension == null) {
            log.error("ExtensionContext: 未找到 Extension 实例 {}，无法分发命令 {} (Type: {}).",
                targetExtensionId, command.getId(), command.getType());
            // 如果命令需要返回结果，这里需要发出一个失败的 CommandResult
            if (command.getOriginalCommandId() != null) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(command.getId(),
                        "Extension instance not found: %s".formatted(targetExtensionId)),
                    "Engine"); // Engine 作为发送方
            }
            return;
        }

        TenEnvProxy<ExtensionEnvImpl> proxy = extensionProxies.get(targetExtensionId);
        if (proxy == null) {
            log.error("ExtensionContext: TenEnvProxy 未初始化，无法分发命令 {} (Type: {}) 给 Extension {}.",
                command.getId(), command.getType(), targetExtensionId);
            if (command.getOriginalCommandId() != null) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(command.getId(),
                        "TenEnvProxy not initialized for Extension: %s".formatted(targetExtensionId)),
                    "Engine"); // Engine 作为发送方
            }
            return;
        }

        // --- 消息转换逻辑开始 (对于入站命令) ---

        Message processedCommand = command;
        ExtensionInfo targetExtInfo = extensionInfos.get(targetExtensionId);
        if (targetExtInfo != null && targetExtInfo.getMsgConversionContexts() != null) {
            for (MessageConversionContext context : targetExtInfo.getMsgConversionContexts()) {
                Message converted = MessageConverter.convertMessage(processedCommand, context);
                if (converted != processedCommand) { // 如果消息被转换了
                    processedCommand = converted;
                    log.debug("ExtensionContext: 入站命令 {} 被转换。", processedCommand.getId());
                }
            }
        }
        // --- 消息转换逻辑结束 ---

        // 将命令提交到 Extension 专属的 Runloop 线程上执行 onCmd 回调
        final Command finalProcessedCommand = (Command)processedCommand;
        // C++ 端 ten_extension_thread_handle_in_msg_sync 会根据 extension_thread->state
        // 来决定是入队还是直接处理
        // 我们的 Java Runloop.postTask 自动处理了队列和线程调度
        ExtensionEnvImpl extensionEnv = proxy.targetEnv(); // 获取实际的 ExtensionEnvImpl 实例
        if (extensionEnv == null) {
            log.error("ExtensionContext: 无法获取 ExtensionEnvImpl 实例，无法分发命令 {}。",
                finalProcessedCommand.getId());
            if (finalProcessedCommand.getOriginalCommandId() != null) {
                this.routeCommandResultFromExtension(
                    CommandResult.fail(finalProcessedCommand.getId(), "ExtensionEnvImpl not available."),
                    "Engine");
            }
            return;
        }

        extensionEnv.getAttachedRunloop().postTask(() -> {
            try {
                // 在 Extension 自己的 Runloop 线程中调用 onCmd
                extension.onCmd(extensionEnv, finalProcessedCommand);
            } catch (Exception e) {
                log.error("ExtensionContext: Extension {} 处理命令 {} 时发生异常: {}",
                    targetExtensionId, finalProcessedCommand.getId(), e.getMessage(), e);
                if (finalProcessedCommand.getOriginalCommandId() != null) {
                    this.routeCommandResultFromExtension(
                        CommandResult.fail(finalProcessedCommand.getId(),
                            "Error processing command in Extension: %s".formatted(e.getMessage())),
                        "Engine");
                }
            }
        });
    }

    @Override
    public int doWork() {
        int workDone = 0;
        // ExtensionContext 本身不直接处理任务队列，其主要职责是管理 Extension
        // Extension 自身的 Agent 或其 Runloop 会处理消息
        // Note: 如果 ExtensionContext 需要周期性任务，可在此处添加。
        return workDone;
    }

    @Override
    public String roleName() {
        return roleName;
    }

    // 获取所有 Extension 的 ID 列表
    public List<String> getExtensionIds() {
        return Collections.unmodifiableList(extensions.keySet().stream().toList());
    }

    // 获取 Extension 实例
    public Extension getExtension(String extensionId) {
        return extensions.get(extensionId);
    }

    // 新增：获取 Extension 的 TenEnvProxy 实例
    public TenEnvProxy<ExtensionEnvImpl> getTenEnvProxyForExtension(String extensionId) {
        return extensionProxies.get(extensionId);
    }

    // 实现 ExtensionCommandSubmitter 接口方法
    @Override
    public CompletableFuture<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName) {
        // 命令从 Extension 提交，委托给 Engine 的 commandSubmitter
        log.debug("ExtensionContext: Extension {} 提交命令 {} 到 Engine。", sourceExtensionName, command.getId());
        // 修改 srcLoc 以反映真实的来源 Extension
        command.getSrcLoc().setExtensionName(sourceExtensionName);
        return engineCommandSubmitter.submitCommand(command);
    }

    // 实现 ExtensionMessageSubmitter 接口方法
    @Override
    public void submitMessageFromExtension(Message message, String sourceExtensionName) {
        // 消息从 Extension 提交，委托给 Engine 的 messageSubmitter
        log.debug("ExtensionContext: Extension {} 提交消息 {} 到 Engine。", sourceExtensionName, message.getId());
        // 修改 srcLoc 以反映真实的来源 Extension
        message.getSrcLoc().setExtensionName(sourceExtensionName);
        engineMessageSubmitter.submitMessage(message);
    }

    // 实现 ExtensionCommandSubmitter 接口中新增的方法
    @Override
    public void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName) {
        log.debug("ExtensionContext: Extension {} 路由命令结果 {} 到 Engine。", sourceExtensionName,
            commandResult.getId());
        // 委托给 Engine 处理，Engine 知道如何路由结果
        // 这里需要 Engine 提供一个方法来路由来自 Extension 的命令结果
        engine.routeCommandResultFromExtension(commandResult, sourceExtensionName);
    }
}