package com.tenframework.server;

import java.util.concurrent.CompletableFuture;

import com.tenframework.core.engine.Engine;
import com.tenframework.server.handler.ByteBufToWebSocketFrameEncoder;
import com.tenframework.server.handler.HttpCommandInboundHandler; // 新增导入
import com.tenframework.server.handler.HttpCommandResultOutboundHandler; // 新增导入
import com.tenframework.server.handler.HttpRequestLogger;
import com.tenframework.server.handler.MessageInboundHandler;
import com.tenframework.server.handler.WebSocketFrameToByteBufDecoder;
import com.tenframework.server.handler.WebSocketMessageFrameHandler;
import com.tenframework.server.message.MessageEncoder;
import com.tenframework.server.message.WebSocketMessageDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMessageServer {

    private final int port; // 修改为统一的端口
    private final Engine engine;
    private final CompletableFuture<Void> startFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private int boundPort; // 新增字段，用于存储实际绑定的端口

    public NettyMessageServer(int port, Engine engine) { // 修改构造函数参数
        this.port = port;
        this.engine = engine;
    }

    public CompletableFuture<Void> start() throws Exception {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast("httpRequestLogger", new HttpRequestLogger()); // Add HTTP request
                                                                                                 // logger
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/websocket"));
                            ch.pipeline().addLast(new HttpCommandResultOutboundHandler());
                            ch.pipeline().addLast(new HttpCommandInboundHandler(engine));
                            ch.pipeline().addLast(new WebSocketFrameToByteBufDecoder());
                            ch.pipeline().addLast(new WebSocketMessageDecoder());
                            ch.pipeline().addLast("messageInboundHandler", new MessageInboundHandler(engine)); // Add
                                                                                                               // Message
                                                                                                               // inbound
                                                                                                               // handler
                            ch.pipeline().addLast(new MessageEncoder());
                            ch.pipeline().addLast(new ByteBufToWebSocketFrameEncoder());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 绑定端口
            ChannelFuture future = b.bind(port).sync(); // 使用统一端口
            boundPort = ((java.net.InetSocketAddress) future.channel().localAddress()).getPort(); // 获取实际绑定的端口

            log.info("NettyMessageServer started and listening on port {}", boundPort);

            startFuture.complete(null);

            return startFuture.exceptionally(e -> {
                log.error("NettyMessageServer failed during startup.", e);
                return null;
            });

        } catch (Exception e) {
            startFuture.completeExceptionally(e);
            throw e;
        }
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutting down NettyMessageServer on port {}", port);
        CompletableFuture<Void> bossShutdownFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> workerShutdownFuture = CompletableFuture.completedFuture(null);

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

        CompletableFuture.allOf(bossShutdownFuture, workerShutdownFuture)
                .thenRun(() -> {
                    log.info("NettyMessageServer on port {} shut down completely.", port);
                    shutdownFuture.complete(null);
                })
                .exceptionally(e -> {
                    log.error("NettyMessageServer shutdown encountered errors on port {}: {}", port,
                            e.getMessage());
                    shutdownFuture.completeExceptionally(e);
                    return null;
                });

        return shutdownFuture;
    }

    public int getPort() {
        return port;
    }

    public int getBoundPort() {
        return boundPort;
    }
}