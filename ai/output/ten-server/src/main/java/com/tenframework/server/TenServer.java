package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import lombok.extern.slf4j.Slf4j;

import com.tenframework.server.handler.HttpCommandInboundHandler;
import com.tenframework.server.NettyHttpServer;

import java.net.BindException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TenServer {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final int initialTcpPort;
    private final int initialHttpPort;
    private final Engine engine;
    private NettyMessageServer nettyMessageServer;
    private NettyHttpServer nettyHttpServer;

    private int currentTcpPort;
    private int currentHttpPort;

    public TenServer(int tcpPort, int httpPort, Engine engine) {
        this.initialTcpPort = tcpPort;
        this.initialHttpPort = httpPort;
        this.engine = engine;
        this.currentTcpPort = tcpPort;
        this.currentHttpPort = httpPort;
    }

    public CompletableFuture<Void> start() {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                CompletableFuture<Void> startAttemptFuture = tryStart();
                // 如果成功，返回future并跳出重试循环
                return startAttemptFuture;
            } catch (CompletionException e) {
                if (e.getCause() instanceof BindException) {
                    log.warn("Port already in use on attempt {}/{}. Retrying with new ports...", attempt + 1,
                            MAX_RETRY_ATTEMPTS);
                    // 重新查找可用端口
                    currentTcpPort = findAvailablePort();
                    currentHttpPort = findAvailablePort();
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS); // 短暂延迟
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Server startup interrupted during retry.", interruptedException));
                    }
                } else {
                    // 非BindException，直接失败
                    return CompletableFuture.failedFuture(e);
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        // 达到最大重试次数仍未成功
        return CompletableFuture.failedFuture(new RuntimeException(
                "Failed to start TenServer after " + MAX_RETRY_ATTEMPTS + " attempts due to port binding issues."));
    }

    private CompletableFuture<Void> tryStart() throws Exception {
        log.info("TenServer starting on TCP port {} and HTTP port {}", currentTcpPort, currentHttpPort);

        nettyMessageServer = new NettyMessageServer(currentTcpPort, currentHttpPort, engine);
        nettyHttpServer = new NettyHttpServer(currentHttpPort, engine);

        CompletableFuture<Void> messageServerStartFuture = nettyMessageServer.start();
        CompletableFuture<Void> httpServerStartFuture = nettyHttpServer.start();

        return CompletableFuture.allOf(messageServerStartFuture, httpServerStartFuture)
                .whenComplete((v, cause) -> {
                    if (cause == null) {
                        // 成功启动后，更新实际绑定的端口
                        this.currentTcpPort = nettyMessageServer.getBoundPort();
                        this.currentHttpPort = nettyHttpServer.getBoundPort();
                        log.info("TenServer successfully started on TCP port {} and HTTP port {}", this.currentTcpPort,
                                this.currentHttpPort);
                    }
                })
                .exceptionally(e -> {
                    log.error("TenServer failed to start one or more components.", e);
                    throw new java.util.concurrent.CompletionException(e); // 重新抛出，以便上游可以捕获
                });
    }

    public CompletableFuture<Void> shutdown() {
        log.info("TenServer shutting down.");

        CompletableFuture<Void> messageServerShutdownFuture = CompletableFuture.completedFuture(null);
        if (nettyMessageServer != null) {
            messageServerShutdownFuture = nettyMessageServer.shutdown();
        }

        CompletableFuture<Void> httpServerShutdownFuture = CompletableFuture.completedFuture(null);
        if (nettyHttpServer != null) {
            httpServerShutdownFuture = nettyHttpServer.shutdown();
        }

        return CompletableFuture.allOf(messageServerShutdownFuture, httpServerShutdownFuture)
                .exceptionally(e -> {
                    log.error("TenServer shutdown encountered errors.", e);
                    throw new java.util.concurrent.CompletionException(e); // 重新抛出，以便上游可以捕获
                });
    }

    // 新增方法，用于获取实际绑定的TCP端口
    public int getTcpPort() {
        return currentTcpPort;
    }

    // 新增方法，用于获取实际绑定的HTTP端口
    public int getHttpPort() {
        return currentHttpPort;
    }

    // 新增方法，用于查找可用端口
    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法找到可用端口: " + e.getMessage(), e);
        }
    }
}