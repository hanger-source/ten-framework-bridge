package com.tenframework.core.app;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.remote.Remote; // 引入 Remote，用于 remotes Map
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * `ten-framework` 应用的入口点和宿主。
 * 负责初始化 Engine，并管理所有 Connection 和 Engine 实例。
 * 对齐C/Python中的ten_app_t结构体。
 */
@Slf4j
public class App {

    private final String appUri;
    private final List<Engine> engines;
    private final boolean oneEventLoopPerEngine;

    private final Map<String, Connection> activeConnections;
    private final Map<String, Remote> remotes; // 管理所有远程 Engine 实例，使用 String 作为 key (Remote ID/URI)

    public App(String appUri, boolean oneEventLoopPerEngine) {
        this.appUri = appUri;
        this.oneEventLoopPerEngine = oneEventLoopPerEngine;
        this.engines = new CopyOnWriteArrayList<>();
        this.activeConnections = new ConcurrentHashMap<>();
        this.remotes = new ConcurrentHashMap<>(); // 初始化 remotes
        log.info("App: 初始化完成，URI: {}", appUri);
    }

    /**
     * 启动 App，并启动所有 Engine 实例。
     */
    public void start() {
        log.info("App: 正在启动，URI: {}...", appUri);
        for (Engine engine : engines) {
            engine.start();
        }
        log.info("App: 已启动，URI: {}", appUri);
    }

    /**
     * 关闭 App，停止所有 Engine，并关闭所有活跃连接。
     */
    public void stop() {
        log.info("App: 正在关闭... ");
        for (Engine engine : engines) {
            engine.stop();
        }
        activeConnections.values().forEach(Connection::close);
        remotes.values().forEach(Remote::shutdown); // 远程清理
        log.info("App: 已关闭。");
    }

    /**
     * 注册一个新的 Engine 实例到 App。
     *
     * @param engine 要注册的 Engine 实例
     */
    public void addEngine(Engine engine) {
        this.engines.add(engine);
        log.info("App {}: 新增 Engine: {}", appUri, engine.getEngineId());
    }

    /**
     * 根据 graphId 获取 Engine 实例。
     * 在实际应用中，可能需要更复杂的查找逻辑，例如根据 graphId 映射到 Engine。
     * 目前简化为遍历查找。
     *
     * @param graphId Engine 对应的 graphId
     * @return 找到的 Engine 实例，如果不存在则返回 null
     */
    public Engine getEngineByGraphId(String graphId) {
        return engines.stream()
                .filter(e -> e.getEngineId().equals(graphId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 创建一个新的 Engine 实例。
     *
     * @param graphId 新 Engine 的 graphId
     * @return 新创建的 Engine 实例
     */
    public Engine createEngine(String graphId) {
        Engine newEngine = new Engine(graphId, 65536, oneEventLoopPerEngine);
        addEngine(newEngine);
        log.info("App {}: 创建新的 Engine: {}", appUri, newEngine.getEngineId());
        return newEngine;
    }

    /**
     * 当 Netty 接收到新的客户端连接时，创建并注册一个新的 Connection 实例。
     * 由 ServerHandler 调用。
     *
     * @param connection 新连接的Connection实例
     */
    public void onNewConnection(Connection connection) {
        activeConnections.put(connection.getConnectionId(), connection);
        log.info("App: 新增活跃连接: {}", connection.getConnectionId());
    }

    /**
     * 当一个 Connection 关闭时，由 ServerHandler 调用，移除该连接。
     *
     * @param connectionId 关闭的连接ID
     */
    public void onConnectionClosed(String connectionId) {
        Connection removed = activeConnections.remove(connectionId);
        if (removed != null) {
            log.info("App: 连接 {} 已关闭，从活跃列表中移除。剩余活跃连接数: {}", connectionId, activeConnections.size());
            // TODO: 通知所有Remote，检查其是否包含此Connection并进行清理
        }
    }

    /**
     * 从 Connection 接收到消息后，将消息提交给 Engine 的输入队列。
     * 此方法现在承担 `ten_app_do_connection_migration_or_push_to_engine_queue` 的部分职责。
     * 它可以根据消息和连接状态，决定是进行连接迁移还是直接将消息推送到 Engine 队列。
     *
     * @param message    接收到的消息
     * @param connection 消息来源的 Connection 实例
     */
    public void handleInboundMessage(Message message, Connection connection) {
        if (message.getType() == MessageType.CMD_START_GRAPH && connection.getEngine() == null) {
            String graphId = null;
            if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                graphId = message.getDestLocs().get(0).getGraphId();
            } else { // 如果没有目的地，生成一个随机 UUID 作为 graphId
                graphId = UUID.randomUUID().toString();
                log.warn("StartGraphCommand 没有指定目的地，生成随机 graphId: {}", graphId);
            }

            Engine targetEngine = getEngineByGraphId(graphId);

            if (targetEngine == null) {
                targetEngine = createEngine(graphId);
            }

            connection.bindToEngine(targetEngine);
            // 触发 Connection 迁移到 Engine 的 Runloop
            // 这里需要修改 Connection.migrate 接口以接受 Runloop 而不是 ExecutorService
            connection.migrate(targetEngine.getLoop()); // **将 Runloop 实例传递给 migrate**

            targetEngine.submitCommand((StartGraphCommand) message);
            log.info("App {}: 接收到 StartGraphCommand，Connection {} 已绑定到 Engine {} 并触发迁移。",
                    appUri, connection.getConnectionId(), targetEngine.getEngineId());

        } else if (connection.getEngine() != null) {
            connection.getEngine().submitMessage(message);
            log.debug("App {}: 接收到入站消息，已提交给Engine {}: type={}, id={}",
                    appUri, connection.getEngine().getEngineId(), message.getType(), message.getId());
        } else {
            log.warn("App {}: 接收到入站消息但 Connection 未绑定 Engine，且非 StartGraphCommand，消息将被丢弃: type={}, id={}",
                    appUri, message.getType(), message.getId());
        }
    }

    /**
     * Engine 内部产生需要发送到外部的消息时，通过此方法路由到对应的 Connection 或 Remote。
     *
     * @param message 要发送的出站消息
     */
    public void sendOutboundMessage(Message message) {
        if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
            for (Location destLoc : message.getDestLocs()) {
                sendMessageToLocation(message, destLoc); // 委托给新方法
            }
        } else {
            log.warn("App {}: 出站消息没有目的地，无法发送: type={}, id={}", appUri, message.getType(), message.getId());
        }
    }

    /**
     * 将消息发送到指定的位置 (Location)，可以是 Connection 或 Remote。
     *
     * @param message        待发送的消息
     * @param targetLocation 目标位置
     */
    private void sendMessageToLocation(Message message, Location targetLocation) {
        if (targetLocation == null) {
            log.warn("App {}: 尝试向空目标位置发送消息: type={}, id={}", appUri, message.getType(), message.getId());
            return;
        }

        // 尝试发送给活跃的 Connection
        // 这里需要更复杂的映射机制，例如，根据 Location 的 appUri, graphId, nodeId 来找到对应的 Connection
        // 目前简化为匹配 remoteLocation
        boolean sent = false;
        for (Connection conn : activeConnections.values()) {
            if (conn.getRemoteLocation() != null && targetLocation.equals(conn.getRemoteLocation())) {
                conn.sendMessage(message);
                log.debug("App {}: 消息已发送到 Connection {}: type={}, id={}, targetLoc={}",
                        appUri, conn.getConnectionId(), message.getType(), message.getId(), targetLocation);
                sent = true;
                break;
            }
        }

        // 如果没有发送给 Connection，则尝试发送给 Remote
        if (!sent) {
            Remote targetRemote = remotes.get(targetLocation.getAppUri()); // 假设 Remote 的 key 是 appUri
            if (targetRemote != null && targetRemote.isActive()) { // 假设 Remote 有 isActive 方法
                targetRemote.sendMessage(message);
                log.debug("App {}: 消息已发送到 Remote {}: type={}, id={}, targetLoc={}",
                        appUri, targetRemote.getRemoteId(), message.getType(), message.getId(), targetLocation);
                sent = true;
            } else {
                log.warn("App {}: 无法找到目标 Remote 或其不活跃，无法发送消息: targetLoc={}, type={}, id={}",
                        appUri, targetLocation, message.getType(), message.getId());
            }
        }

        if (!sent) {
            log.warn("App {}: 消息无法发送到任何目标: type={}, id={}, targetLoc={}",
                    appUri, message.getType(), message.getId(), targetLocation);
        }
    }
}