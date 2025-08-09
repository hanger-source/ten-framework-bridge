package com.tenframework.core.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.command.app.AppCommandHandler;
import com.tenframework.core.command.app.CloseAppCommandHandler;
import com.tenframework.core.command.app.StartGraphCommandHandler;
import com.tenframework.core.command.app.StopGraphCommandHandler;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.PredefinedGraphEntry;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.remote.Remote;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.util.MessageUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * App 类作为 Ten 框架的顶层容器和协调器。
 * 它管理 Engine 实例，处理传入的连接，并路由消息。
 * 对应 C 语言中的 ten_app_t 结构体。
 */
@Slf4j
@Getter
public class App implements MessageReceiver, Runnable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, Engine> engines; // 管理所有活跃的 Engine 实例，key 为 graphId
    private final List<Connection> orphanConnections; // 尚未绑定到 Engine 的连接
    // private final PathTable pathTable; // App 级别 PathTable - 移除 App 级别 PathTable
    private final Map<String, Remote> remotes; // 外部远程连接 (跨 App/Engine 通信)
    private final boolean hasOwnRunloopPerEngine; // 每个 Engine 是否有自己的 Runloop
    // 新增：Extension 注册机制
    private final Map<String, Class<? extends Extension>> availableExtensions;
    private String appUri;
    private Runloop appRunloop;
    private GraphConfig appConfig; // 新增：App 的整体配置，对应 property.json
    // 新增：预定义图的映射，方便通过名称查找
    private Map<String, PredefinedGraphEntry> predefinedGraphsByName;

    // 新增：App 级别的命令处理器映射
    private final Map<MessageType, AppCommandHandler> appCommandHandlers;

    /**
     * App 的构造函数。
     *
     * @param appUri                 应用程序的 URI。
     * @param hasOwnRunloopPerEngine 是否为每个 Engine 创建独立的 Runloop。
     * @param configFilePath         配置文件路径 (例如 property.json)，可以为 null。
     */
    public App(String appUri, boolean hasOwnRunloopPerEngine, String configFilePath) {
        this.appUri = appUri;
        this.hasOwnRunloopPerEngine = hasOwnRunloopPerEngine;
        engines = new ConcurrentHashMap<>();
        orphanConnections = Collections.synchronizedList(new java.util.ArrayList<>());
        // this.pathTable = new PathTable(); // App 级别 PathTable - 移除 App 级别 PathTable
        remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射
        availableExtensions = new ConcurrentHashMap<>(); // 初始化 Extension 注册表

        // 加载配置
        if (configFilePath != null && !configFilePath.isEmpty()) {
            loadConfig(configFilePath);
        } else {
            appConfig = new GraphConfig(); // 创建一个空的默认配置
            log.warn("App: 未提供配置文件路径，使用默认空配置。");
        }

        appRunloop = new Runloop("AppRunloop-" + appUri);

        appCommandHandlers = new HashMap<>(); // 初始化 App 命令处理器映射
        registerAppCommandHandlers(); // 注册 App 级别的命令处理器

        log.info("App {} created with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    /**
     * App 的构造函数。
     *
     * @param appUri                 应用程序的 URI。
     * @param hasOwnRunloopPerEngine 是否为每个 Engine 创建独立的 Runloop。
     * @param appConfig              App 的配置对象。
     */
    public App(String appUri, boolean hasOwnRunloopPerEngine, GraphConfig appConfig) {
        this.appUri = appUri;
        this.hasOwnRunloopPerEngine = hasOwnRunloopPerEngine;
        engines = new ConcurrentHashMap<>();
        orphanConnections = Collections.synchronizedList(new java.util.ArrayList<>());
        // this.pathTable = new PathTable(); // App 级别 PathTable - 移除 App 级别 PathTable
        remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射
        availableExtensions = new ConcurrentHashMap<>(); // 初始化 Extension 注册表

        this.appConfig = appConfig != null ? appConfig : new GraphConfig(); // 使用传入的配置或默认空配置
        appRunloop = new Runloop("AppRunloop-" + appUri);

        appCommandHandlers = new HashMap<>(); // 初始化 App 命令处理器映射
        registerAppCommandHandlers(); // 注册 App 级别的命令处理器

        log.info("App {} created with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    private void registerAppCommandHandlers() {
        // 注册所有 App 级别的命令处理器
        appCommandHandlers.put(MessageType.CMD_START_GRAPH, new StartGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_STOP_GRAPH, new StopGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_CLOSE_APP, new CloseAppCommandHandler());
    }

    // 私有方法：加载配置文件
    private void loadConfig(String configFilePath) {
        try {
            String jsonContent = Files.readString(Paths.get(configFilePath));
            appConfig = OBJECT_MAPPER.readValue(jsonContent, GraphConfig.class);
            log.info("App: 从 {} 加载配置成功。", configFilePath);
        } catch (IOException e) {
            log.error("App: 加载配置文件 {} 失败: {}", configFilePath, e.getMessage());
            appConfig = new GraphConfig(); // 加载失败时使用空配置
        }
    }

    /**
     * 注册一个 Extension 类，使其可被 Engine 动态加载。
     *
     * @param extensionName  Extension 的名称。
     * @param extensionClass Extension 的 Class 对象。
     */
    public void registerExtension(String extensionName, Class<? extends Extension> extensionClass) {
        availableExtensions.put(extensionName, extensionClass);
        log.info("App: 注册 Extension: {} -> {}", extensionName, extensionClass.getName());
    }

    /**
     * 获取已注册的 Extension Class。
     *
     * @param extensionName Extension 的名称。
     * @return 对应的 Extension Class，如果不存在则返回 Optional.empty()。
     */
    public Optional<Class<? extends Extension>> getRegisteredExtension(String extensionName) {
        return Optional.ofNullable(availableExtensions.get(extensionName));
    }

    /**
     * 启动 App。
     */
    public void start() {
        log.info("App: 启动中...");
        appRunloop.start(); // 启动 App 的 Runloop

        // 从配置中加载预定义图
        predefinedGraphsByName = appConfig.getPredefinedGraphs() != null ? appConfig.getPredefinedGraphs().stream()
                .collect(Collectors.toMap(PredefinedGraphEntry::getName, entry -> entry)) : new ConcurrentHashMap<>();

        // 自动启动配置中标记为 auto_start 的预定义图
        if (appConfig.getPredefinedGraphs() != null) {
            appConfig.getPredefinedGraphs().stream()
                    .filter(PredefinedGraphEntry::isAutoStart)
                    .forEach(entry -> {
                        log.info("App: 自动启动预定义图: {}", entry.getName());
                        // 模拟发送 StartGraphCommand 来启动预定义图
                        Location srcLoc = new Location().setAppUri(appUri);
                        Location destLoc = new Location().setAppUri(appUri)
                                .setGraphId(entry.getGraphDefinition().getGraphId());
                        StartGraphCommand startCmd = new StartGraphCommand(
                                MessageUtils.generateUniqueId(),
                                srcLoc,
                                Collections.singletonList(destLoc),
                                "Auto-start predefined graph", // message
                                entry.getGraphDefinition().getJsonContent(), // graphJsonDefinition
                                false // longRunningMode
                        );
                        // 提交命令到 App Runloop，然后由 App 的 handleInboundMessage 处理
                        appRunloop.postTask(() -> handleInboundMessage(startCmd, null));
                    });
        }
        log.info("App: 已启动。");
    }

    /**
     * 停止 App。
     */
    public void stop() {
        log.info("App: 停止中...");
        // 停止所有 Engine
        engines.values().forEach(Engine::stop);
        engines.clear();

        // 关闭所有远程连接
        remotes.values().forEach(Remote::shutdown);
        remotes.clear();

        // 清理孤立连接
        orphanConnections.clear();

        // 停止 App 的 Runloop
        appRunloop.shutdown();
        log.info("App: 已停止。");
    }

    @Override
    public void run() {
        // App 的主运行逻辑，如果 App 有自己的 Runloop，它会在这里处理事件
        // 目前，App 的 Runloop 已经由 Runloop.start() 管理，此处方法主要用于接口实现
        log.info("App run() method called, AppRunloop is active.");
    }

    /**
     * 处理传入的消息。
     *
     * @param message    传入的消息。
     * @param connection 消息来源的连接，可能为 null（例如来自内部命令）。
     */
    @Override
    public void handleInboundMessage(Message message, Connection connection) {
        log.debug("App: 接收到入站消息: ID={}, Type={}, Src={}", message.getId(), message.getType(),
                message.getSrcLoc()); // 修正方法名
        appRunloop.postTask(() -> {
            if (message instanceof Command) {
                Command command = (Command) message;
                AppCommandHandler handler = appCommandHandlers.get(command.getType());
                if (handler != null) {
                    try {
                        handler.handle(this, command, connection);
                    } catch (Exception e) {
                        log.error("App {}: 命令处理器处理命令 {} 失败: {}", appUri, command.getId(), e.getMessage(), e);
                        if (connection != null) {
                            CommandResult errorResult = CommandResult.fail(command.getId(),
                                    "App command handling failed: " + e.getMessage());
                            connection.sendOutboundMessage(errorResult);
                        }
                    }
                } else {
                    log.warn("App {}: 未知 App 级别命令类型或没有注册处理器: {}", appUri, command.getType());
                    if (connection != null) {
                        CommandResult errorResult = CommandResult.fail(command.getId(),
                                "Unknown App command type or no handler registered: " + command.getType());
                        connection.sendOutboundMessage(errorResult);
                    }
                }
            } else { // 对于非命令消息，尝试路由到目标 Engine 或 Remote
                if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                    routeMessageToDestination(message, connection);
                } else {
                    log.warn("App: 消息 {} (Type: {}) 没有目的地，无法处理。", message.getId(), message.getType());
                }
            }
        });
    }

    /**
     * 当有新的连接建立时被调用。
     *
     * @param connection 新的连接实例。
     */
    public void onNewConnection(Connection connection) {
        log.info("App: 接收到新连接: {}", connection.getRemoteAddress());
        // 将新连接添加到孤立连接列表，等待 StartGraphCommand 来绑定到 Engine
        orphanConnections.add(connection);
        // 设置连接的消息接收者为 App 本身，直到它被迁移到某个 Engine
        connection.setMessageReceiver(this);
    }

    /**
     * 将消息路由到指定的目标 Location。
     * 这可以是 Engine 内部的 Extension，也可以是另一个 Remote (例如另一个 App)。
     *
     * @param message          待路由的消息。
     * @param sourceConnection 消息的来源连接，如果来自内部则为 null。
     */
    public void sendMessageToLocation(Message message, Connection sourceConnection) {
        appRunloop.postTask(() -> {
            if (message.getDestLocs() == null || message.getDestLocs().isEmpty()) {
                log.warn("App: 消息 {} 没有目的地 Location，无法路由。", message.getId());
                return;
            }

            for (Location destLoc : message.getDestLocs()) {
                if (destLoc.getAppUri() != null && destLoc.getAppUri().equals(appUri)) {
                    // 目标是当前 App 内部的 Engine
                    if (destLoc.getGraphId() != null) {
                        Engine targetEngine = engines.get(destLoc.getGraphId());
                        if (targetEngine != null) {
                            log.debug("App: 路由消息 {} 到 Engine {}。", message.getId(), destLoc.getGraphId());
                            targetEngine.processMessage(message); // Engine 的 processMessage 已经处理了命令分发
                        } else {
                            log.warn("App: 目标 Engine {} 不存在，消息 {} 无法路由。", destLoc.getGraphId(), message.getId());
                        }
                    }
                } else {
                    // 目标是外部 App/Remote
                    String remoteId = destLoc.getAppUri(); // 使用 App URI 作为 Remote ID
                    Remote targetRemote = remotes.get(remoteId);
                    if (targetRemote == null) {
                        log.warn("App: 目标 Remote {} (App URI) 不存在，消息 {} 无法路由。需要实现 Remote 的创建。", remoteId,
                                message.getId());
                        // 暂时发送回源连接，表示无法路由
                        if (sourceConnection != null) {
                            CommandResult errorResult = CommandResult.fail(message.getId(),
                                    "Remote " + remoteId + " not found.");
                            sourceConnection.sendOutboundMessage(errorResult);
                        }
                    } else {
                        log.debug("App: 路由消息 {} 到 Remote {}。", message.getId(), remoteId);
                        targetRemote.sendMessage(message);
                    }
                }
            }
        });
    }

    /**
     * 路由消息到其目的地。
     *
     * @param message          待路由的消息。
     * @param sourceConnection 消息的来源连接，如果来自内部则为 null。
     */
    private void routeMessageToDestination(Message message, Connection sourceConnection) {
        List<Location> destinations = message.getDestLocs();
        if (destinations == null || destinations.isEmpty()) {
            log.warn("App: 消息 {} 没有目的地，无法路由。", message.getId());
            return;
        }

        for (Location destLoc : destinations) {
            if (appUri.equals(destLoc.getAppUri())) { // 目标是本 App 内部
                if (destLoc.getGraphId() != null) {
                    Engine targetEngine = engines.get(destLoc.getGraphId());
                    if (targetEngine != null) {
                        targetEngine.processMessage(message); // 路由到对应的 Engine
                    } else {
                        log.warn("App: 目标 Engine {} 不存在，无法路由消息 {}。", destLoc.getGraphId(), message.getId());
                    }
                }
            } else { // 目标是其他 App/远程
                String remoteAppUri = destLoc.getAppUri();
                Remote remote = remotes.get(remoteAppUri);
                if (remote == null) {
                    log.warn("App: 目标远程 App URI {} 不存在 Remote 实例，消息 {} 无法路由。", remoteAppUri, message.getId());
                }
            }
        }
    }
}