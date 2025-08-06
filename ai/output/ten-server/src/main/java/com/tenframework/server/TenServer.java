package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class TenServer {

    private final int tcpPort;
    private final int httpPort;
    private final Engine engine;
    private NettyMessageServer nettyMessageServer;
    private NettyHttpServer nettyHttpServer;

    public TenServer(int tcpPort, int httpPort, Engine engine) {
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
        this.engine = engine;
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> serverStartFuture = new CompletableFuture<>();
        try {
            nettyMessageServer = new NettyMessageServer(tcpPort, engine);
            nettyHttpServer = new NettyHttpServer(httpPort, engine);

            CompletableFuture<Void> tcpServerStart = nettyMessageServer.getStartFuture();
            CompletableFuture<Void> httpServerStart = nettyHttpServer.getStartFuture();

            nettyMessageServer.start();
            nettyHttpServer.start();

            CompletableFuture.allOf(tcpServerStart, httpServerStart)
                    .thenRun(() -> {
                        log.info("All Netty servers started successfully.");
                        serverStartFuture.complete(null);
                    })
                    .exceptionally(e -> {
                        log.error("Failed to start one or more Netty servers: {}", e.getMessage());
                        serverStartFuture.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error starting TenServer: {}", e.getMessage());
            serverStartFuture.completeExceptionally(e);
        }
        return serverStartFuture;
    }

    public CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> serverShutdownFuture = new CompletableFuture<>();
        if (nettyMessageServer == null && nettyHttpServer == null) {
            log.warn("TenServer is not running, no servers to shut down.");
            serverShutdownFuture.complete(null);
            return serverShutdownFuture;
        }

        CompletableFuture<Void> tcpServerShutdown = CompletableFuture.completedFuture(null);
        if (nettyMessageServer != null) {
            tcpServerShutdown = nettyMessageServer.getShutdownFuture();
            nettyMessageServer.shutdown();
        }

        CompletableFuture<Void> httpServerShutdown = CompletableFuture.completedFuture(null);
        if (nettyHttpServer != null) {
            httpServerShutdown = nettyHttpServer.getShutdownFuture();
            nettyHttpServer.shutdown();
        }

        CompletableFuture.allOf(tcpServerShutdown, httpServerShutdown)
                .thenRun(() -> {
                    log.info("All Netty servers shut down completely.");
                    serverShutdownFuture.complete(null);
                })
                .exceptionally(e -> {
                    log.error("TenServer shutdown encountered errors: {}", e.getMessage());
                    serverShutdownFuture.completeExceptionally(e);
                    return null;
                });
        return serverShutdownFuture;
    }
}