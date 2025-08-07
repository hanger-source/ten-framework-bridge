package com.tenframework.agent.example;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.server.TenServer;
import com.tenframework.server.message.MessageEncoder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class EndToEndIntegrationTest {

    // 将端口设置为动态获取，避免端口冲突
    private static final int ANY_AVAILABLE_PORT = 0;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Engine engine;
    private TenServer tenServer;
    private EventLoopGroup clientGroup;

    @BeforeEach
    void setUp() throws Exception {
        log.info("--- 初始化测试环境 ---");
        engine = new Engine("test-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 为Engine注册CommandHandlers
        // Engine内部已注册StartGraphCommandHandler和StopGraphCommandHandler，无需再次手动注册
        // engine.registerCommandHandler(new StartGraphCommandHandler());
        // engine.registerCommandHandler(new StopGraphCommandHandler());
        // TODO: PropertyReadCommandHandler and PropertyWriteCommandHandler are not yet
        // implemented in Java
        // engine.registerCommandHandler(new PropertyReadCommandHandler());
        // engine.registerCommandHandler(new PropertyWriteCommandHandler());

        // 注册系统Extension
        // ClientConnectionExtension由Engine内部管理，不需要手动注册
        // engine.registerExtension(new ClientConnectionExtension(engine));

        // 使用随机端口启动TenServer
        tenServer = new TenServer(ANY_AVAILABLE_PORT, engine);
        tenServer.start().get(10, TimeUnit.SECONDS); // 启动服务器并等待完成

        clientGroup = new NioEventLoopGroup();
        log.info("--- 测试环境初始化完成 ---");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tenServer != null) {
            tenServer.shutdown().get(10, TimeUnit.SECONDS); // 延长关闭超时时间
        }
        if (engine != null) {
            engine.stop();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(1, 5, TimeUnit.SECONDS).sync(); // 延长客户端组关闭时间
        }
        // 添加短暂延迟，确保所有资源已完全释放
        TimeUnit.MILLISECONDS.sleep(500);
        log.info("所有测试服务已关闭。");
    }

    @Test
    void testEndToEndFlow() throws Exception {
        URI websocketUri = new URI("ws://localhost:" + tenServer.getPort() + "/websocket");
        String graphId = UUID.randomUUID().toString();
        String appUri = "test-app";

        // 1. 模拟发送 start_graph 命令来启动图实例 - 通过WebSocket发送
        log.info("--- 测试 WebSocket /start_graph 命令 ---");
        GraphConfig graphConfig = GraphLoader
            .loadGraphConfigFromFile("src/test/resources/test_websocket_echo_graph.json");

        Command startGraphCommand = Command.builder()
            .commandId(UUID.randomUUID().getMostSignificantBits())
            .name("start_graph")
            .properties(Map.of(
                "graph_id", graphId,
                "app_uri", appUri,
                "graph_json", graphConfig))
            .sourceLocation(Location.builder().appUri("ws_client").graphId("N/A").extensionName("N/A").build())
            .build();

        CompletableFuture<Message> startGraphResponseFuture = new CompletableFuture<>();
        WebSocketTestClient client = new WebSocketTestClient(websocketUri, clientGroup, engine);
        client.registerCommandResponseFuture(startGraphCommand.getCommandId(), startGraphResponseFuture);
        client.connect().get(10, TimeUnit.SECONDS);

        EmbeddedChannel encoderChannel = new EmbeddedChannel(new MessageEncoder());
        encoderChannel.writeOutbound(startGraphCommand);
        BinaryWebSocketFrame encodedStartGraphFrame = encoderChannel.readOutbound();
        assertNotNull(encodedStartGraphFrame, "编码后的StartGraph WebSocket帧不应为空");
        assertTrue(encodedStartGraphFrame.content().isReadable(), "编码后的StartGraph WebSocket帧内容应可读");

        client.sendMessage(encodedStartGraphFrame).sync();
        CommandResult startGraphResult = (CommandResult)startGraphResponseFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(startGraphResult, "应收到start_graph的CommandResult");
        assertTrue(startGraphResult.isSuccess(), "start_graph命令应成功");
        log.info("WebSocket /start_graph 命令结果: {}", startGraphResult);

        TimeUnit.SECONDS.sleep(3); // 延长等待时间，确保图实例启动稳定

        GraphInstance actualGraphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", actualGraphInstance.getGraphId());

        // 2. 通过 WebSocket/MsgPack 接口发送一个 Data 消息给 SimpleEchoExtension
        log.info("--- 测试 WebSocket/MsgPack Data 消息回显 ---");
        Data testData = Data.json("echo_test_data",
            objectMapper.writeValueAsString(Map.of("content", "Hello WebSocket Echo!")));
        testData.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, graphId);
        testData.setProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, appUri);

        encoderChannel = new EmbeddedChannel(new MessageEncoder());
        encoderChannel.writeOutbound(testData);
        BinaryWebSocketFrame encodedDataFrame = encoderChannel.readOutbound();
        assertNotNull(encodedDataFrame, "编码后的Data WebSocket帧不应为空");
        assertTrue(encodedDataFrame.content().isReadable(), "编码后的Data WebSocket帧内容应可读");

        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        client.setDataResponseFuture(wsEchoResponseFuture);
        client.sendMessage(encodedDataFrame).sync();

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedWsMessage, "应收到WebSocket回显消息");
        assertTrue(receivedWsMessage instanceof Data, "WebSocket回显消息应为Data类型");
        Data wsEchoData = (Data) receivedWsMessage;
        assertEquals(MessageConstants.DATA_NAME_ECHO_DATA, wsEchoData.getName());

        Map<String, Object> receivedPayload = objectMapper.readValue(wsEchoData.getData().toString(CharsetUtil.UTF_8),
                Map.class);
        assertEquals("Echo: Hello WebSocket Echo!", receivedPayload.get("content"));
        log.info("成功接收到回显WebSocket Data消息: {}", wsEchoData.getName());
        TimeUnit.SECONDS.sleep(1);

        // 3. 通过 WebSocket 接口发送 stop_graph 命令
        log.info("--- 测试 WebSocket /stop_graph 命令 ---");
        Command stopGraphCommand = Command.builder()
            .commandId(UUID.randomUUID().getMostSignificantBits())
            .name("stop_graph")
            .properties(Map.of("graph_id", graphId, "app_uri", appUri))
            .sourceLocation(Location.builder().appUri("ws_client").graphId("N/A").extensionName("N/A").build())
            .build();

        CompletableFuture<Message> stopGraphResponseFuture = new CompletableFuture<>();
        client.registerCommandResponseFuture(stopGraphCommand.getCommandId(), stopGraphResponseFuture);

        encoderChannel = new EmbeddedChannel(new MessageEncoder());
        encoderChannel.writeOutbound(stopGraphCommand);
        BinaryWebSocketFrame encodedStopGraphFrame = encoderChannel.readOutbound();
        assertNotNull(encodedStopGraphFrame, "编码后的StopGraph WebSocket帧不应为空");
        assertTrue(encodedStopGraphFrame.content().isReadable(), "编码后的StopGraph WebSocket帧内容应可读");

        client.sendMessage(encodedStopGraphFrame).sync();
        CommandResult stopGraphResult = (CommandResult)stopGraphResponseFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(stopGraphResult, "应收到stop_graph的CommandResult");
        assertTrue(stopGraphResult.isSuccess(), "stop_graph命令应成功");
        log.info("WebSocket /stop_graph 命令结果: {}", stopGraphResult);

        TimeUnit.SECONDS.sleep(1);

        assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");

        client.disconnect().get(10, TimeUnit.SECONDS); // 客户端断开连接

        // 4. 不再测试 HTTP /ping 命令，因为已切换为WebSocket
        log.info("--- 停止 HTTP /ping 命令测试 (已切换为WebSocket) ---");
    }
}