package com.tenframework.server;

import java.util.concurrent.CompletableFuture;

import com.tenframework.core.engine.Engine;
import com.tenframework.server.handler.ByteBufToWebSocketFrameEncoder;
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

    private final int tcpPort; // 修改为tcpPort
    private final int httpPort; // HTTP端口
    private final Engine engine;
    private final CompletableFuture<Void> startFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private int boundPort; // 新增字段，用于存储实际绑定的端口

    public NettyMessageServer(int tcpPort, int httpPort, Engine engine) { // 修改构造函数参数
        this.tcpPort = tcpPort; // 赋值给tcpPort
        this.httpPort = httpPort; // 赋值给httpPort
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
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/websocket"));
                            ch.pipeline().addLast(new WebSocketFrameToByteBufDecoder()); // 将 WebSocketFrame 转换为 ByteBuf
                            ch.pipeline().addLast(new WebSocketMessageDecoder()); // 消息解码器：ByteBuf -> Message (无长度前缀)
                            ch.pipeline().addLast(new MessageEncoder()); // 消息编码器：Message -> ByteBuf
                            ch.pipeline().addLast(new ByteBufToWebSocketFrameEncoder()); // ByteBuf -> WebSocketFrame
                            ch.pipeline().addLast(new WebSocketMessageFrameHandler(engine));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 绑定TCP端口
            ChannelFuture tcpFuture = b.bind(tcpPort).sync(); // 使用tcpPort
            boundPort = ((java.net.InetSocketAddress) tcpFuture.channel().localAddress()).getPort(); // 获取实际绑定的端口

            log.info("NettyMessageServer started and listening on TCP port {}", boundPort); // 日志更新

            // 启动HTTP服务器

            // 在这里完成startFuture，表示NettyMessageServer自身已启动
            startFuture.complete(null);

            // 返回一个组合的Future，让外部调用者等待所有服务器组件启动
            return startFuture.exceptionally(e -> {
                log.error("NettyMessageServer or HttpServer failed during startup.", e);
                return null; // 异常已记录，向上层抛出
            });

        } catch (Exception e) {
            startFuture.completeExceptionally(e);
            throw e;
        }
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutting down NettyMessageServer on TCP port {}", tcpPort);
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
                    log.info("NettyMessageServer on TCP port {} shut down completely.", tcpPort);
                    shutdownFuture.complete(null);
                })
                .exceptionally(e -> {
                    log.error("NettyMessageServer shutdown encountered errors on TCP port {}: {}", tcpPort,
                            e.getMessage());
                    shutdownFuture.completeExceptionally(e);
                    return null;
                });

        return shutdownFuture;
    }

    public int getPort() {
        return tcpPort; // 提供获取tcpPort的方法
    }

    // 新增方法，用于获取实际绑定的端口
    public int getBoundPort() {
        return boundPort;
    }
}