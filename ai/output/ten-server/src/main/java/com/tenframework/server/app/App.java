package com.tenframework.server.app;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.server.connection.Connection;
import com.tenframework.server.remote.Remote;
import com.tenframework.server.message.MessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * `ten-framework` 应用的入口点和宿主。
 * 负责初始化 Engine，启动网络服务（监听端口），并管理所有 Connection 和 Remote。
 * 对齐C/Python中的ten_app_t结构体。
 */
@Slf4j
public class App {

    private final Engine engine; // 关联的 Engine 实例
    private final ServerBootstrap serverBootstrap; // Netty 服务器启动器
    private final EventLoopGroup bossGroup; // 用于处理传入连接的事件循环组
    private final EventLoopGroup workerGroup; // 用于处理已接受连接的事件循环组

    private final Map<String, Connection> activeConnections; // 管理所有活跃的连接
    private final Map<Location, Remote> remotes; // 管理所有远程 Engine 实例

    private final int port;
    private final String websocketPath; // WebSocket路径，例如 "/websocket"

    public App(Engine engine, int port, String websocketPath) {
        this.engine = engine;
        this.port = port;
        this.websocketPath = websocketPath;

        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.serverBootstrap = new ServerBootstrap();

        this.activeConnections = new ConcurrentHashMap<>();
        this.remotes = new ConcurrentHashMap<>();

        configureServerBootstrap();
        log.info("App: 初始化完成，监听端口: {}", port);
    }

    private void configureServerBootstrap() {
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(65536)); // 聚合HTTP消息
                        p.addLast(new WebSocketServerProtocolHandler(websocketPath, null, true)); // WebSocket协议处理器

                        // 添加自定义的消息编解码器和连接处理器
                        p.addLast(new MessageDecoder()); // 将ByteBuf解码为Message
                        p.addLast(new MessageEncoder()); // 将Message编码为ByteBuf
                        p.addLast(new ConnectionHandler(App.this, engine)); // 处理连接和消息分发
                    }
                });
    }

    /**
     * 启动 Netty 服务器，并开始监听端口。
     * 
     * @throws InterruptedException 如果线程被中断
     */
    public void start() throws InterruptedException {
        log.info("App: 正在启动服务器，监听端口: {}...", port);
        ChannelFuture future = serverBootstrap.bind(port).sync();
        // Engine可能需要在App启动后启动，或者由Spring等框架管理其生命周期
        // engine.start(); // 假设Engine有start方法
        log.info("App: 服务器已启动，监听端口: {}", port);
        future.channel().closeFuture().sync(); // 阻塞直到服务器关闭
    }

    /**
     * 关闭 Netty 服务器，停止 Engine，并关闭所有活跃连接。
     */
    public void stop() {
        log.info("App: 正在关闭... ");
        // engine.stop(); // 假设Engine有stop方法

        // 关闭所有活跃连接
        activeConnections.values().forEach(Connection::close);
        remotes.values().forEach(Remote::shutdown);

        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        log.info("App: 已关闭。");
    }

    /**
     * 当 Netty 接收到新的客户端连接时，创建并注册一个新的 Connection 实例。
     * 由 ConnectionHandler 调用。
     * 
     * @param channel 新连接的Channel
     * @return 新创建的Connection实例
     */
    public Connection onNewConnection(Channel channel) {
        Connection connection = new Connection(channel);
        activeConnections.put(connection.getConnectionId(), connection);
        log.info("App: 新增活跃连接: {}", connection.getConnectionId());
        return connection;
    }

    /**
     * 当一个 Connection 关闭时，由 ConnectionHandler 调用，移除该连接。
     * 
     * @param connectionId 关闭的连接ID
     */
    public void onConnectionClosed(String connectionId) {
        Connection removed = activeConnections.remove(connectionId);
        if (removed != null) {
            log.info("App: 连接 {} 已关闭，从活跃列表中移除。剩余活跃连接数: {}", connectionId, activeConnections.size());
        }
    }

    /**
     * 从 Connection 接收到消息后，将消息提交给 Engine 的输入队列。
     * 由 ConnectionHandler 调用。
     * 
     * @param message 接收到的消息
     */
    public void handleInboundMessage(Message message) {
        // 消息最终会提交给Engine处理，这里可以做一些预处理或日志记录
        engine.submitMessage(message);
        log.debug("App: 接收到入站消息，已提交给Engine: type={}, id={}", message.getType(), message.getId());
    }

    /**
     * Engine 内部产生需要发送到外部的消息时，通过此方法路由到对应的 Connection 或 Remote。
     * 
     * @param message 要发送的出站消息
     */
    public void sendOutboundMessage(Message message) {
        // 根据消息的目的地Location决定如何发送
        // 1. 发送到特定的活跃Connection (客户端响应)
        // 2. 发送到Remote (跨Engine通信)

        if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
            for (Location destLoc : message.getDestLocs()) {
                // 尝试通过活跃Connection发送
                // 这里需要一种机制来根据destLoc找到对应的connectionId，或者直接通过Channel发送
                // 简单起见，如果destLoc与某个活跃连接的remoteLocation匹配，则发送
                activeConnections.values().stream()
                        .filter(conn -> destLoc.equals(conn.getRemoteLocation()))
                        .findFirst()
                        .ifPresent(conn -> conn.sendMessage(message));

                // 尝试通过Remote发送（如果目的地是远程Engine）
                if (remotes.containsKey(destLoc)) {
                    remotes.get(destLoc).sendMessage(message);
                }
            }
        } else {
            log.warn("App: 出站消息没有目的地，无法发送: type={}, id={}", message.getType(), message.getId());
        }
    }

    // 辅助方法，用于添加和获取Remote实例
    public Remote getOrCreateRemote(Location remoteLocation, Engine localEngine) {
        return remotes.computeIfAbsent(remoteLocation, loc -> new Remote(loc, localEngine));
    }

    public static void main(String[] args) throws InterruptedException {
        // 示例用法：
        // 首先需要一个Engine实例
        // Engine engine = new Engine("app-main"); // 假设Engine构造函数需要app_uri
        // App app = new App(engine, 8080, "/ws");
        // app.start();
    }
}