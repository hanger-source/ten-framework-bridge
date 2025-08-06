package com.tenframework.core.server;

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
import com.tenframework.core.server.WebSocketMessageFrameHandler;

@Slf4j
public class NettyHttpServer {

    private final int port;
    private final Engine engine; // Engine 实例
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private CompletableFuture<Void> startFuture = new CompletableFuture<>();
    private CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

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

    public void start() throws Exception {
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

            ChannelFuture f = b.bind(port).sync();
            log.info("NettyHttpServer started and listening on port {}", port);
            startFuture.complete(null); // 服务器启动成功，完成startFuture

            f.channel().closeFuture().addListener(future -> {
                shutdown();
                shutdownFuture.complete(null); // 服务器关闭，完成shutdownFuture
            });
        } catch (Exception e) {
            startFuture.completeExceptionally(e); // 启动失败，completeExceptionally
            throw e;
        } finally {
            // The original code had a finally block here, but the new code removes the
            // sync() call.
            // The original code also had a shutdown() call here.
            // The new code's start() method now handles the shutdown.
            // So, the original finally block is effectively removed by the new start()
            // logic.
        }
    }

    public void shutdown() {
        log.info("Shutting down NettyHttpServer on port {}", port);
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
                    log.info("NettyHttpServer on port {} shut down completely.", port);
                    shutdownFuture.complete(null); // 所有组都关闭后，完成shutdownFuture
                })
                .exceptionally(e -> {
                    log.error("NettyHttpServer shutdown encountered errors on port {}: {}", port, e.getMessage());
                    shutdownFuture.completeExceptionally(e); // 如果有任何异常，完成shutdownFutureWithException
                    return null;
                });
    }
}