package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import com.tenframework.server.message.MessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.tenframework.server.handler.WebSocketMessageFrameHandler; // 修正导入

@Slf4j
public class NettyMessageServer {

    private final int port;
    private final Engine engine;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private CompletableFuture<Void> startFuture = new CompletableFuture<>();
    private CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

    public NettyMessageServer(int port, Engine engine) {
        this.port = port;
        this.engine = engine;
    }

    public CompletableFuture<Void> getStartFuture() {
        return startFuture;
    }

    public CompletableFuture<Void> getShutdownFuture() {
        return shutdownFuture;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(); // (1) 用于接受传入连接的线程组
        workerGroup = new NioEventLoopGroup(); // (2) 用于处理已接受连接的I/O操作的线程组

        try {
            ServerBootstrap b = new ServerBootstrap(); // (3) 一个辅助类，用于设置服务器
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (4) 使用NioServerSocketChannel作为服务器的通道类型
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (5)
                                                                            // ChannelInitializer用于为新接受的通道设置ChannelPipeline
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec()); // HTTP 消息编解码器
                            ch.pipeline().addLast(new HttpObjectAggregator(65536)); // HTTP 消息聚合器
                            // WebSocket 协议处理器，指定 WebSocket 路径。这里将处理握手请求。
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/websocket"));

                            // 在 WebSocket 握手成功后，上述处理器会从 pipeline 中移除 HTTP 相关的处理器
                            // 并在其位置添加 WebSocketFrameDecoder 和 WebSocketFrameEncoder。
                            // 接下来，我们将添加处理 WebSocket 帧的自定义处理器。
                            ch.pipeline().addLast(new MessageEncoder()); // 消息编码器：Message -> WebSocket Binary Frame
                            ch.pipeline().addLast(new MessageDecoder()); // 消息解码器：WebSocket Binary Frame -> Message
                            ch.pipeline().addLast(new WebSocketMessageFrameHandler(engine)); // 自定义 WebSocket 业务处理器
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // (6) 设置服务器套接字的选项，如积压队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (7) 设置已接受通道的选项，如启用TCP KeepAlive

            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(port).sync(); // (8) 绑定端口并等待绑定操作完成

            log.info("NettyMessageServer started and listening on port {}", port);
            startFuture.complete(null); // 服务器启动成功，完成startFuture

            f.channel().closeFuture().addListener(future -> {
                shutdown();
                shutdownFuture.complete(null); // 服务器关闭，完成shutdownFuture
            });
        } catch (Exception e) {
            startFuture.completeExceptionally(e); // 启动失败，completeExceptionally
            throw e;
        } // finally 块保持不变
    }

    public void shutdown() {
        log.info("Shutting down NettyMessageServer on port {}", port);
        CompletableFuture<Void> bossShutdownFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> workerShutdownFuture = CompletableFuture.completedFuture(null);

        if (bossGroup != null) {
            bossShutdownFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    bossGroup.shutdownGracefully().sync();
                    log.info("BossGroup shutdown gracefully.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("BossGroup shutdown interrupted.", e);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    log.error("BossGroup shutdown failed.", e);
                    throw new RuntimeException(e);
                }
                return null;
            });
        }

        if (workerGroup != null) {
            workerShutdownFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    workerGroup.shutdownGracefully().sync();
                    log.info("WorkerGroup shutdown gracefully.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("WorkerGroup shutdown interrupted.", e);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    log.error("WorkerGroup shutdown failed.", e);
                    throw new RuntimeException(e);
                }
                return null;
            });
        }

        CompletableFuture.allOf(bossShutdownFuture, workerShutdownFuture)
                .thenRun(() -> {
                    log.info("NettyMessageServer on port {} shut down completely.", port);
                    shutdownFuture.complete(null); // 所有组都关闭后，完成shutdownFuture
                })
                .exceptionally(e -> {
                    log.error("NettyMessageServer shutdown encountered errors on port {}: {}", port, e.getMessage());
                    shutdownFuture.completeExceptionally(e); // 如果有任何异常，完成shutdownFutureWithException
                    return null;
                });
    }
}