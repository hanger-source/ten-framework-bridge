package com.tenframework.server.remote;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.server.connection.Connection;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 远程 Engine 实例的逻辑封装。
 * 负责管理与远程 Engine 的连接，并将本地 Engine 产生的发往远程的消息转发出去。
 * 对齐C/Python中的ten_remote_t结构体。
 */
@Data
@Slf4j
public class Remote {

    private final Location remoteEngineLocation; // 远程 Engine 的 Location
    private final Engine localEngine; // 本地 Engine 的引用
    private final List<Connection> activeConnections; // 管理与此 Remote 关联的所有活跃 Connection 实例
    private final EventLoopGroup workerGroup; // Netty worker group for client connections

    public Remote(Location remoteEngineLocation, Engine localEngine) {
        this.remoteEngineLocation = remoteEngineLocation;
        this.localEngine = localEngine;
        this.activeConnections = new CopyOnWriteArrayList<>(); // 线程安全列表
        this.workerGroup = new NioEventLoopGroup();
        log.info("Remote {}: 创建，目标Engine: {}", remoteEngineLocation.toDebugString(),
                remoteEngineLocation.toDebugString());
    }

    /**
     * 建立到远程 Engine 的新 Connection。
     * 这里以WebSocket为例，后续可扩展支持TCP等。
     * 
     * @param host 远程主机
     * @param port 远程端口
     * @return ChannelFuture，用于异步处理连接结果
     */
    public ChannelFuture createConnection(String host, int port, String path) {
        URI websocketURI = URI.create(String.format("ws://%s:%d%s", host, port, path));
        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                websocketURI, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(8192));
                        // 这里需要添加WebSocketClientHandler以及MessageDecoder/Encoder
                        // 为了避免循环依赖，暂时不直接引入MessageEncoder/Decoder
                        // TODO: 引入MessageEncoder/Decoder和RemoteConnectionHandler
                        // p.addLast(new MessageDecoder());
                        // p.addLast(new MessageEncoder());
                        // p.addLast(new RemoteConnectionHandler(Remote.this, localEngine));
                    }
                });

        log.info("Remote {}: 尝试建立WebSocket连接到 {}:{}", remoteEngineLocation.toDebugString(), host, port);
        ChannelFuture future = b.connect(host, port);
        future.addListener(f -> {
            if (f.isSuccess()) {
                log.info("Remote {}: WebSocket连接建立成功到 {}", remoteEngineLocation.toDebugString(), host);
                // TODO: 创建并添加Connection实例到activeConnections
                // Connection newConnection = new Connection(f.channel());
                // newConnection.bindToEngine(localEngine);
                // activeConnections.add(newConnection);
                // handshaker.handshake(f.channel()).sync(); // 执行WebSocket握手
            } else {
                log.error("Remote {}: WebSocket连接建立失败到 {}:{}, 错误: {}", remoteEngineLocation.toDebugString(), host, port,
                        f.cause().getMessage());
            }
        });
        return future;
    }

    /**
     * 将消息通过可用的 Connection 发送到远程 Engine。
     * 根据 Message 的 destLoc 找到合适的 Connection。
     * 
     * @param message 要发送的消息
     */
    public void sendMessage(Message message) {
        // 简单实现：尝试通过第一个活跃连接发送，复杂路由逻辑可在此处扩展
        if (!activeConnections.isEmpty()) {
            Connection conn = activeConnections.get(0); // 假设通过第一个连接发送
            conn.sendMessage(message);
        } else {
            log.warn("Remote {}: 没有活跃连接可发送消息: type={}, id={}", remoteEngineLocation.toDebugString(), message.getType(),
                    message.getId());
        }
    }

    /**
     * 当一个 Connection 关闭时，Remote 更新其连接列表。
     * 
     * @param connection 已关闭的 Connection 实例
     */
    public void onConnectionClosed(Connection connection) {
        activeConnections.remove(connection);
        log.info("Remote {}: 连接 {} 已关闭，从活跃列表中移除。剩余活跃连接数: {}",
                remoteEngineLocation.toDebugString(), connection.getConnectionId(), activeConnections.size());
    }

    /**
     * 关闭此 Remote 及其所有活跃连接和Netty资源。
     */
    public void shutdown() {
        log.info("Remote {}: 正在关闭所有连接和Netty资源...", remoteEngineLocation.toDebugString());
        for (Connection conn : activeConnections) {
            conn.close();
        }
        workerGroup.shutdownGracefully();
        log.info("Remote {}: 已关闭。", remoteEngineLocation.toDebugString());
    }
}