package com.tenframework.agent;

import com.tenframework.core.engine.Engine;
import com.tenframework.server.TenServer; // 导入TenServer
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.Location;
import com.tenframework.core.message.Message;

import io.netty.buffer.Unpooled;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;

public class Main {
        public static void main(String[] args) throws Exception {
                System.out.println("Hello, ten-framework bridge!");

                // 1. 初始化 Engine
                Engine engine = new Engine("main-engine");
                engine.start();
                System.out.println("Engine started.");

                // 2. 注册 SimpleEchoExtension
                SimpleEchoExtension echoExtension = new SimpleEchoExtension();
                // 提供 Extension 的配置属性，例如图ID
                Map<String, Object> extensionProperties = Map.of(
                                "graph_id", "test-graph",
                                "echo_prefix", "Custom Echo: " // 示例配置
                );
                engine.registerExtension("echo-extension", echoExtension, extensionProperties, "main-app"); // 添加appUri
                System.out.println("SimpleEchoExtension registered.");

                // 3. 启动 TenServer
                int tcpPort = 9090;
                int httpPort = 9091;

                TenServer tenServer = new TenServer(tcpPort, httpPort, engine);
                tenServer.start().join(); // 阻塞等待服务器启动完成
                System.out.println("TenServer started on TCP port " + tcpPort + " and HTTP port " + httpPort);

                // --- 模拟客户端交互 ---

                // 4. 通过 HTTP 接口发送 start_graph 命令
                System.out.println("\n--- Sending start_graph command via HTTP ---");
                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode startGraphCommandJson = objectMapper.createObjectNode();
                startGraphCommandJson.put("cmd_id", UUID.randomUUID().toString());
                startGraphCommandJson.put("name", "start_graph");
                ObjectNode startGraphArgs = objectMapper.createObjectNode();
                startGraphArgs.put("graph_id", "test-graph");
                ObjectNode nodes = objectMapper.createObjectNode();
                nodes.put("echo-extension", objectMapper.createObjectNode()
                                .put("addon", "SimpleEchoExtension")
                                .put("app", "test-app"));
                startGraphArgs.set("nodes", nodes);
                startGraphArgs.set("connections", objectMapper.createArrayNode()); // 暂时没有复杂的连接
                startGraphCommandJson.set("args", startGraphArgs);

                // 模拟 HTTP POST 请求
                Command startGraphCommand = new Command(
                                UUID.randomUUID().toString(), // 将 UUID 转换为 String
                                null, // parentCommandId (仍然是 String)
                                startGraphCommandJson.get("name").asText(),
                                objectMapper.convertValue(startGraphArgs, Map.class),
                                new Location("main-app", null, "http-client"), // sourceLocation
                                Collections.singletonList(new Location("main-app", "test-graph", "engine")), // destinationLocations
                                new HashMap<>(), // properties
                                System.currentTimeMillis() // timestamp
                );
                System.out.println("Submitting start_graph command to Engine: " + startGraphCommandJson.toString());
                engine.submitMessage(startGraphCommand);
                // 假设 start_graph 是同步处理或在Engine内部完成，这里等待一小段时间
                TimeUnit.SECONDS.sleep(1);

                // 5. 通过 TCP/MsgPack 接口发送一个 Data 消息给 SimpleEchoExtension
                System.out.println("\n--- Sending Data message via TCP/MsgPack (simulated) ---");
                Location dataSource = new Location("main-app", "test-graph", "tcp-client"); // appUri改为main-app
                Location dataDest = new Location("main-app", "test-graph", "echo-extension"); // appUri改为main-app
                Data testData = new Data(
                                "test-data-message", // name
                                Unpooled.wrappedBuffer("Hello from TCP Client!".getBytes()), // data (ByteBuf)
                                false, // isEof
                                "text/plain", // contentType
                                "UTF-8", // encoding
                                dataSource, // sourceLocation
                                Collections.singletonList(dataDest), // destinationLocations
                                new HashMap<>(), // properties
                                System.currentTimeMillis() // timestamp
                );

                // 模拟 TCP 客户端发送消息
                System.out.println("Submitting Data message to Engine: " + testData.getProperties().get("text"));
                engine.submitMessage(testData);
                TimeUnit.SECONDS.sleep(1); // 等待回显消息处理

                // 6. 通过 HTTP 接口发送 stop_graph 命令
                System.out.println("\n--- Sending stop_graph command via HTTP ---");
                ObjectNode stopGraphCommandJson = objectMapper.createObjectNode();
                stopGraphCommandJson.put("cmd_id", UUID.randomUUID().toString());
                stopGraphCommandJson.put("name", "stop_graph");
                ObjectNode stopGraphArgs = objectMapper.createObjectNode().put("graph_id", "test-graph");
                stopGraphCommandJson.set("args", stopGraphArgs);

                Command stopGraphCommand = new Command(
                                UUID.randomUUID().toString(), // 将 UUID 转换为 String
                                null,
                                stopGraphCommandJson.get("name").asText(),
                                objectMapper.convertValue(stopGraphArgs, Map.class),
                                new Location("main-app", null, "http-client"), // appUri改为main-app
                                Collections.singletonList(new Location("main-app", "test-graph", "engine")),
                                new HashMap<>(), // properties
                                System.currentTimeMillis() // timestamp
                );
                System.out.println("Submitting stop_graph command to Engine: " + stopGraphCommandJson.toString());
                engine.submitMessage(stopGraphCommand);
                TimeUnit.SECONDS.sleep(1);

                // 7. 关闭服务
                System.out.println("\n--- Shutting down services ---");
                tenServer.shutdown().join(); // 阻塞等待服务器关闭完成
                engine.stop();
                System.out.println("All services stopped.");
        }
}