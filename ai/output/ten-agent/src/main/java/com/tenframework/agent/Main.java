package com.tenframework.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tenframework.core.message.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.server.TenServer;
import io.netty.buffer.Unpooled;

public class Main {
        public static void main(String[] args) throws Exception {
                System.out.println("Hello, ten-framework bridge!");

                // 1. 初始化 Engine
                Engine engine = new Engine("main-engine");
                engine.start();
                System.out.println("Engine started.");

                // 3. 启动 TenServer
                int port = 8080; // 使用统一端口

                TenServer tenServer = new TenServer(port, engine); // 只传入一个端口
                tenServer.start().join(); // 阻塞等待服务器启动完成
                System.out.println("TenServer started on port " + port); // 更新日志
        }
}