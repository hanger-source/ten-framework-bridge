package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import lombok.extern.slf4j.Slf4j;

// import com.tenframework.server.handler.HttpCommandInboundHandler; // 已移动到 NettyMessageServer
// import com.tenframework.server.NettyHttpServer; // 不再使用

import java.net.BindException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TenServer {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 500;

    private static final int DEFAULT_PORT = 8080; // 统一端口

    private final int initialPort; // 统一端口
    private final Engine engine;
    private NettyMessageServer nettyMessageServer;
    // private NettyHttpServer nettyHttpServer; // 不再需要

    private int currentPort; // 统一端口

    public TenServer(int port, Engine engine) { // 修改构造函数参数
        this.initialPort = port;
        this.engine = engine;
        this.currentPort = port;
    }

    public CompletableFuture<Void> start() {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                CompletableFuture<Void> startAttemptFuture = tryStart();
                return startAttemptFuture;
            } catch (CompletionException e) {
                if (e.getCause() instanceof BindException) {
                    log.warn("Port already in use on attempt {}/{}. Retrying with new port...", attempt + 1,
                            MAX_RETRY_ATTEMPTS);
                    currentPort = findAvailablePort(); // 重新查找可用端口
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Server startup interrupted during retry.", interruptedException));
                    }
                } else {
                    return CompletableFuture.failedFuture(e);
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        return CompletableFuture.failedFuture(new RuntimeException(
                "Failed to start TenServer after " + MAX_RETRY_ATTEMPTS + " attempts due to port binding issues."));
    }

    private CompletableFuture<Void> tryStart() throws Exception {
        log.info("TenServer starting on port {}", currentPort); // 日志更新

        nettyMessageServer = new NettyMessageServer(currentPort, engine); // 传入统一端口
        // nettyHttpServer = null; // 移除

        return nettyMessageServer.start()
                .whenComplete((v, cause) -> {
                    if (cause == null) {
                        this.currentPort = nettyMessageServer.getBoundPort();
                        log.info("TenServer successfully started on port {}", this.currentPort); // 日志更新
                    }
                })
                .exceptionally(e -> {
                    log.error("TenServer failed to start.", e);
                    throw new java.util.concurrent.CompletionException(e);
                });
    }

    public CompletableFuture<Void> shutdown() {
        log.info("TenServer shutting down.");

        CompletableFuture<Void> messageServerShutdownFuture = CompletableFuture.completedFuture(null);
        if (nettyMessageServer != null) {
            messageServerShutdownFuture = nettyMessageServer.shutdown();
        }

        return messageServerShutdownFuture
                .exceptionally(e -> {
                    log.error("TenServer shutdown encountered errors.", e);
                    throw new java.util.concurrent.CompletionException(e);
                });
    }

    public int getPort() { // 统一获取端口的方法
        return currentPort;
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法找到可用端口: " + e.getMessage(), e);
        }
    }
}