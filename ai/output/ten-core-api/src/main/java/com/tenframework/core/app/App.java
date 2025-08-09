package com.tenframework.core.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.PredefinedGraphEntry;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.path.PathTable;
import com.tenframework.core.remote.Remote;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.util.MessageUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * App 类作为 Ten 框架的顶层容器和协调器。
 * 它管理 Engine 实例，处理传入的连接，并路由消息。
 * 对应 C 语言中的 ten_app_t 结构体。
 */
@Slf4j
public class App implements MessageReceiver, CommandReceiver, Runnable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Getter
    private String appUri;
    private Runloop appRunloop;
    private final Map<String, Engine> engines; // 管理所有活跃的 Engine 实例，key 为 graphId
    private final List<Connection> orphanConnections; // 尚未绑定到 Engine 的连接
    private final PathTable pathTable; // App 级别的 PathTable
    private final Map<String, Remote> remotes; // 外部远程连接 (跨 App/Engine 通信)

    private final boolean hasOwnRunloopPerEngine; // 每个 Engine 是否有自己的 Runloop

    private GraphConfig appConfig; // 新增：App 的整体配置，对应 property.json
    // 新增：预定义图的映射，方便通过名称查找
    private Map<String, PredefinedGraphEntry> predefinedGraphsByName;
    // 新增：Extension 注册机制
    private final Map<String, Class<? extends Extension>> availableExtensions;

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
        this.engines = new ConcurrentHashMap<>();
        this.orphanConnections = Collections.synchronizedList(new java.util.ArrayList<>());
        this.pathTable = new PathTable(); // App 级别 PathTable
        this.remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射
        this.availableExtensions = new ConcurrentHashMap<>(); // 初始化 Extension 注册表

        // 加载配置
        if (configFilePath != null && !configFilePath.isEmpty()) {
            loadConfig(configFilePath);
        } else {
            this.appConfig = new GraphConfig(); // 创建一个空的默认配置
            log.warn("App: 未提供配置文件路径，使用默认空配置。");
        }

        this.appRunloop = new Runloop("AppRunloop-" + appUri);

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
        this.engines = new ConcurrentHashMap<>();
        this.orphanConnections = Collections.synchronizedList(new java.util.ArrayList<>());
        this.pathTable = new PathTable(); // App 级别 PathTable
        this.remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射
        this.availableExtensions = new ConcurrentHashMap<>(); // 初始化 Extension 注册表

        this.appConfig = appConfig != null ? appConfig : new GraphConfig(); // 使用传入的配置或默认空配置
        this.appRunloop = new Runloop("AppRunloop-" + appUri);

        log.info("App {} created with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    // 私有方法：加载配置文件
    private void loadConfig(String configFilePath) {
        try {
            String jsonContent = Files.readString(Paths.get(configFilePath));
            this.appConfig = OBJECT_MAPPER.readValue(jsonContent, GraphConfig.class);
            log.info("App: 从 {} 加载配置成功。", configFilePath);
        } catch (IOException e) {
            log.error("App: 加载配置文件 {} 失败: {}", configFilePath, e.getMessage());
            this.appConfig = new GraphConfig(); // 加载失败时使用空配置
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
                                MessageType.CMD_START_GRAPH,
                                srcLoc,
                                Collections.singletonList(destLoc),
                                "Auto-start predefined graph",
                                entry.getGraphDefinition().getJsonContent(), // 使用预定义图的JSON内容
                                false // 假定自动启动的图不是长运行模式，或者根据实际配置
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
        appRunloop.stop();
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
                message.getSourceLocation());
        appRunloop.postTask(() -> {
            if (message.getType() == MessageType.CMD_START_GRAPH) {
                StartGraphCommand startCmd = (StartGraphCommand) message;
                handleStartGraphCommand(startCmd, connection);
            } else if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                // 对于所有其他消息，尝试路由到目标 Engine 或 Remote
                routeMessageToDestination(message, connection);
            } else {
                log.warn("App: 消息 {} (Type: {}) 没有目的地，无法处理。", message.getId(), message.getType());
            }
        });
    }

    /**
     * 处理传入的命令消息。
     *
     * @param command 传入的命令消息。
     * @return 命令处理结果的 CompletableFuture。
     */
    @Override
    public Object handleCommand(Command command) {
        // App 级别的命令处理，例如处理 App 本身的配置命令
        log.info("App: 收到命令: {}", command.getName());
        appRunloop.postTask(() -> routeMessageToDestination(command, null)); // 命令也通过路由机制处理
        return CommandResult.success(command.getId(), Map.of("app_response", "Command received by App."));
    }

    /**
     * 处理 StartGraphCommand。
     *
     * @param command    StartGraphCommand。
     * @param connection 发送此命令的连接，可能为 null。
     */
    private void handleStartGraphCommand(StartGraphCommand command, Connection connection) {
        String targetGraphId = command.getGraphId(); // 从命令中获取目标 graphId

        // 优先从预定义图中查找 GraphDefinition
        GraphDefinition graphDefinition = null;
        if (targetGraphId != null && predefinedGraphsByName.containsKey(targetGraphId)) {
            graphDefinition = predefinedGraphsByName.get(targetGraphId).getGraphDefinition();
            log.info("App: 从预定义图中找到 GraphDefinition (graphId: {})。", targetGraphId);
        } else if (command.getGraphJsonDefinition() != null) {
            // 如果命令中包含 JSON 定义，则解析它
            graphDefinition = new GraphDefinition(appUri, command.getGraphJsonDefinition());
            log.info("App: 从 StartGraphCommand 中解析 GraphDefinition (graphId: {})。", graphDefinition.getGraphId());
        }

        if (graphDefinition == null) {
            log.error("App: 无法获取 GraphDefinition，无法启动 Engine。StartGraphCommand ID: {}", command.getId());
            // 返回错误结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(), "Failed to get GraphDefinition.");
                connection.sendOutboundMessage(errorResult);
            }
            return;
        }

        String actualGraphId = graphDefinition.getGraphId(); // 确保使用解析后的 graphId

        Engine engine = engines.get(actualGraphId);
        if (engine == null) {
            log.info("App: 创建新的 Engine 实例，Graph ID: {}", actualGraphId);
            engine = new Engine(this, actualGraphId, graphDefinition, hasOwnRunloopPerEngine, availableExtensions);
            engines.put(actualGraphId, engine);
            engine.start(); // 启动 Engine 及其 Runloop
            log.info("App: Engine {} 已启动。", actualGraphId);
        } else {
            log.info("App: Engine {} 已存在，重用现有实例。", actualGraphId);
        }

        // 迁移 Connection 到 Engine
        if (connection != null) {
            if (orphanConnections.remove(connection)) {
                log.info("App: 孤立连接 {} (Channel ID: {}) 已从孤立列表中移除。", connection.getRemoteAddress(),
                        connection.getChannel().id().asShortText());
            }
            log.info("App: 正在将连接 {} 迁移到 Engine {}。", connection.getRemoteAddress(), actualGraphId);
            connection.migrate(engine.getEngineRunloop(), new Location().setAppUri(appUri).setGraphId(actualGraphId)
                    .setNodeId(MessageConstants.NODE_ID_CLIENT_CONNECTION)); // 迁移到 Engine 的 Runloop
            // 在连接迁移成功后，Engine 会处理连接的后续消息
            log.info("App: 连接 {} 已成功迁移到 Engine {}.", connection.getRemoteAddress(), actualGraphId);

            // 返回成功的 CommandResult 给发起方
            CommandResult successResult = CommandResult.success(command.getId(),
                    Map.of("graph_id", actualGraphId, "message", "Engine started and connection migrated."));
            connection.sendOutboundMessage(successResult);
            log.info("App: 发送 StartGraphCommand 成功结果给连接 {}。", connection.getRemoteAddress());
        } else {
            // 如果没有连接 (例如是内部自动启动的图)
            log.info("App: StartGraphCommand {} 处理完成，Engine {} 已启动。", command.getId(), actualGraphId);
        }
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
                            targetEngine.handleInboundMessage(message);
                        } else {
                            log.warn("App: 目标 Engine {} 不存在，消息 {} 无法路由。", destLoc.getGraphId(), message.getId());
                        }
                    } else {
                        // 目标是 App 本身，但没有指定 Engine，或者 App 本身需要处理
                        log.debug("App: 消息 {} 目标 App 本身。", message.getId());
                        // TODO: App 自身处理消息的逻辑 (如果需要)
                    }
                } else {
                    // 目标是外部 App/Remote
                    String remoteId = destLoc.getAppUri(); // 使用 App URI 作为 Remote ID
                    Remote targetRemote = remotes.get(remoteId);
                    if (targetRemote == null) {
                        // TODO: 根据 remoteId (App URI) 创建或获取 Remote 实例
                        // 这可能需要一个 RemoteFactory，或从 App 配置中读取 Remote 定义
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
                        targetRemote.sendOutboundMessage(message);
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
                        targetEngine.handleInboundMessage(message); // 路由到对应的 Engine
                    } else {
                        log.warn("App: 目标 Engine {} 不存在，无法路由消息 {}。", destLoc.getGraphId(), message.getId());
                        // TODO: 处理路由失败，例如返回错误消息
                    }
                } else {
                    // 目标是 App 本身，没有指定 Engine
                    log.debug("App: 消息 {} 目标是 App 本身。", message.getId());
                    // 如果 App 需要处理特定消息，可以在这里添加逻辑
                }
            } else { // 目标是其他 App/远程
                String remoteAppUri = destLoc.getAppUri();
                Remote remote = remotes.get(remoteAppUri);
                if (remote == null) {
                    // 尝试创建 Remote，如果需要
                    log.warn("App: 目标远程 App URI {} 不存在 Remote 实例，消息 {} 无法路由。", remoteAppUri, message.getId());
                    // TODO: 创建 Remote 实例并存储
                } else {
                    remote.sendOutboundMessage(message); // 通过 Remote 发送
                }
            }
        }
    }
}