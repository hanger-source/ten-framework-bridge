package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.tenframework.server.handler.WebSocketMessageFrameHandler;
import com.tenframework.server.handler.HttpCommandResultOutboundHandler;
import com.tenframework.server.handler.HttpCommandInboundHandler;

@Slf4j
public class NettyHttpServer {

    private final int port;
    private final Engine engine; // Engine 实例
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private CompletableFuture<Void> startFuture = new CompletableFuture<>();
    private CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

    private int boundPort; // 新增字段，用于存储实际绑定的端口

    public NettyHttpServer(int port, Engine engine) {
        this.port = port;
        this.engine = engine;
    }

    public CompletableFuture<Void> getStartFuture() {
        return startFuture;
    }

    public CompletableFuture<Void> getShutdownFuture() {
        return shutdownFuture;
    }

    public CompletableFuture<Void> start() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpRequestDecoder());
                            ch.pipeline().addLast(new HttpCommandResultOutboundHandler()); // 添加HTTP命令结果编码器
                            ch.pipeline().addLast(new HttpResponseEncoder());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));

                            // WebSocket 握手和协议处理
                            // /ws 是WebSocket路径，如果请求是WebSocket升级，此Handler将升级协议
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws", null, true));

                            // 自定义WebSocket消息帧处理器，处理WebSocket帧和TEN消息的转换
                            // 注意：此Handler在WebSocket握手成功后才开始处理WebSocket帧
                            ch.pipeline().addLast(new WebSocketMessageFrameHandler(engine));

                            // 原始的HTTP命令处理器，只处理非WebSocket升级的HTTP请求
                            // 在WebSocket握手成功后，HttpCommandInboundHandler和之前的HTTP编码器/解码器会被移除
                            ch.pipeline().addLast(new HttpCommandInboundHandler(engine));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // bind方法是非阻塞的，返回一个ChannelFuture
            ChannelFuture f = b.bind(port);
            f.addListener(future -> {
                if (future.isSuccess()) {
                    this.boundPort = ((java.net.InetSocketAddress) ((ChannelFuture) future).channel().localAddress())
                            .getPort(); // 获取实际绑定的端口
                    log.info("NettyHttpServer started and listening on port {}", boundPort);
                    startFuture.complete(null);
                } else {
                    log.error("NettyHttpServer failed to start on port {}", port, future.cause());
                    startFuture.completeExceptionally(future.cause());
                }
            });

            return startFuture;

        } catch (Exception e) {
            startFuture.completeExceptionally(e); // 启动失败，completeExceptionally
            // 不需要重新抛出，因为我们通过CompletableFuture处理异步异常
            return startFuture;
        }
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutting down NettyHttpServer on port {}", port);
        CompletableFuture<Void> bossShutdownFuture = new CompletableFuture<>();
        CompletableFuture<Void> workerShutdownFuture = new CompletableFuture<>();

        if (bossGroup != null) {
            bossGroup.shutdownGracefully().addListener(f -> {
                if (f.isSuccess()) {
                    bossShutdownFuture.complete(null);
                    log.info("BossGroup shutdown gracefully.");
                } else {
                    bossShutdownFuture.completeExceptionally(f.cause());
                    log.error("BossGroup shutdown failed.", f.cause());
                }
            });
        } else {
            bossShutdownFuture.complete(null);
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully().addListener(f -> {
                if (f.isSuccess()) {
                    workerShutdownFuture.complete(null);
                    log.info("WorkerGroup shutdown gracefully.");
                } else {
                    workerShutdownFuture.completeExceptionally(f.cause());
                    log.error("WorkerGroup shutdown failed.", f.cause());
                }
            });
        } else {
            workerShutdownFuture.complete(null);
        }

        // 使用CompletableFuture.allOf等待所有关闭操作完成
        CompletableFuture.allOf(bossShutdownFuture, workerShutdownFuture)
                .thenRun(() -> {
                    log.info("NettyHttpServer on port {} shut down completely.", port);
                    shutdownFuture.complete(null);
                })
                .exceptionally(e -> {
                    log.error("NettyHttpServer shutdown encountered errors on port {}: {}", port, e.getMessage());
                    shutdownFuture.completeExceptionally(e);
                    return null;
                });

        return shutdownFuture;
    }

    // 新增方法，用于获取实际绑定的端口
    public int getBoundPort() {
        return boundPort;
    }
}