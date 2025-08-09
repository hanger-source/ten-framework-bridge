package com.tenframework.core.demo;

import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.protocol.websocket.WebSocketProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DemoApp {

    public static void main(String[] args) throws InterruptedException {
        log.info("DemoApp: 启动演示应用...");

        // 1. 初始化 App 实例
        String appUri = "ten://localhost/demo_app";
        App app = new App(appUri, true); // true 表示每个 Engine 有自己的 Runloop
        app.start();

        // 2. 初始化协议处理器 (WebSocketProtocol)
        WebSocketProtocol protocol = new WebSocketProtocol();

        // 3. 模拟一个客户端连接
        MockChannel mockChannel = new MockChannel();
        SimpleConnection clientConnection = new SimpleConnection(mockChannel, protocol);

        // 模拟 Connection 被 App 接收
        app.onNewConnection(clientConnection);

        // 4. 准备一个简化的 Graph JSON 定义
        // 包含一个简单的 EchoExtension (假设它会回传数据，这里简化为只打印)
        String graphJson = """
                {
                  "graph_id": "test_graph_001",
                  "graph_name": "DemoGraph",
                  "extension_groups_info": [],
                  "extensions_info": [
                    {
                      "loc": {
                        "app_uri": "%s",
                        "graph_id": "test_graph_001",
                        "node_id": "echo_ext"
                      },
                      "extension_addon_name": "echo_extension",
                      "extension_name": "echo_ext"
                    }
                  ],
                  "connections": [
                    {
                      "path_id": "echo_to_echo_path",
                      "source": { "app_uri": "%s", "graph_id": "test_graph_001", "node_id": "echo_ext" },
                      "destination": { "app_uri": "%s", "graph_id": "test_graph_001", "node_id": "echo_ext" },
                      "message_type": "DATA_MESSAGE"
                    }
                  ]
                }
                """.formatted(appUri, appUri, appUri);

        // 5. 模拟客户端发送 StartGraphCommand
        Location destLoc = new Location().setAppUri(appUri).setGraphId("test_graph_001");
        StartGraphCommand startCmd = new StartGraphCommand(
                UUID.randomUUID().toString(),
                MessageType.CMD_START_GRAPH,
                new Location().setAppUri(appUri), // srcLoc 可以简化
                Collections.singletonList(destLoc),
                "启动图命令",
                graphJson,
                false // 非长运行模式
        );
        startCmd.setId(String.valueOf(UUID.randomUUID().getLeastSignificantBits())); // 设置CommandId

        log.info("DemoApp: 模拟客户端发送 StartGraphCommand...");
        // 将 StartGraphCommand 模拟为来自 Connection 的消息
        // 通常这里是 protocol.serialize(startCmd)
        // 为了演示，我们直接将 StartGraphCommand 实例传递给 App
        app.handleInboundMessage(startCmd, clientConnection);

        // 给予一些时间让 Engine 启动和 Connection 迁移
        TimeUnit.SECONDS.sleep(2);

        // 6. 模拟客户端发送一个数据消息
        Location dataSrcLoc = new Location().setAppUri(appUri).setGraphId("test_graph_001")
                .setNodeId("echo_ext");
        Location dataDestLoc = new Location().setAppUri(appUri).setGraphId("test_graph_001").setNodeId("echo_ext");
        DataMessage dataMsg = new DataMessage(
                UUID.randomUUID().toString(),
                MessageType.DATA_MESSAGE,
                dataSrcLoc,
                Collections.singletonList(dataDestLoc),
                "Hello, ten-framework!");
        dataMsg.setName("test_data");

        log.info("DemoApp: 模拟客户端发送 DataMessage...");
        // 直接提交给已迁移的 Connection，它会转发给 Engine
        clientConnection.onMessageReceived(dataMsg);

        // 给予一些时间让消息流转和 Extension 处理
        TimeUnit.SECONDS.sleep(2);

        // 7. 关闭应用
        log.info("DemoApp: 正在关闭演示应用...");
        app.stop();
        log.info("DemoApp: 演示应用已关闭。");
    }
}