package com.tenframework.server;

import com.tenframework.core.engine.Engine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerMain {

    private static final int DEFAULT_PORT = 9090;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Ten Framework Server...");

        // 1. 初始化 Engine
        Engine engine = new Engine("server-engine");
        engine.start();
        log.info("Engine started: engineId={}", engine.getEngineId());

        // 2. 启动 TenServer
        TenServer tenServer = new TenServer(DEFAULT_PORT, engine);
        tenServer.start().join(); // 阻塞等待服务器启动完成，保持服务持续运行
        log.info("TenServer successfully started on port {}", tenServer.getPort());

        // 服务将持续运行，直到进程被外部终止
        // 可以在此处添加Shutdown Hook以优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down TenServer and Engine gracefully...");
            tenServer.shutdown().join();
            engine.stop();
            log.info("TenServer and Engine shut down completely.");
        }));
    }
}