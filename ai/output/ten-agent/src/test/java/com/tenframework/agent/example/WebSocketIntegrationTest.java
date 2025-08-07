package com.tenframework.agent.example;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.command.InternalCommandType;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.SimpleEchoExtension;
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

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_APP_URI;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_GRAPH_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class WebSocketIntegrationTest {

    private static final int ANY_AVAILABLE_PORT = 0; // 统一端口，动态获取

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Engine engine;

    private TenServer tenServer; // 使用TenServer

    private EventLoopGroup clientGroup;

    private SimpleEchoExtension echoExtension;

    @BeforeEach
    void setUp() throws Exception {
        log.info("--- 初始化测试环境 ---");
        engine = new Engine("test");
        engine.start(); // 启动Engine

        // 使用随机端口启动TenServer
        tenServer = new TenServer(ANY_AVAILABLE_PORT, engine);
        tenServer.start().get(10, TimeUnit.SECONDS); // 启动服务器并等待完成

        clientGroup = new NioEventLoopGroup();
        log.info("--- 测试环境初始化完成 ---");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tenServer != null) {
            log.info("停止TenServer...");
            tenServer.shutdown().get(500, TimeUnit.SECONDS); // 使用 shutdown 方法
            log.info("TenServer已停止。");
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
    }

    @Test
    void testWebSocketEchoFlow() throws Exception {
        // 确保使用随机端口，避免端口冲突
        URI websocketUri = new URI("ws://localhost:" + tenServer.getPort() + "/websocket");

        String graphId = UUID.randomUUID().toString();
        String appUri = "test-app";

        // 1. 模拟发送 start_graph 命令来启动图实例 - 通过WebSocket发送
        String graphConfigPath = "src/test/resources/test_websocket_echo_graph.json";
        GraphConfig graphConfig = GraphLoader.loadGraphConfigFromFile(graphConfigPath);

        // 构建start_graph命令
        Command startGraphCommand = Command.builder()
            .commandId(UUID.randomUUID().getMostSignificantBits())
            .name(InternalCommandType.START_GRAPH.getCommandName())
            .properties(Map.of(
                PROPERTY_CLIENT_LOCATION_URI, "front_client_generate_id_xxx",
                PROPERTY_CLIENT_APP_URI, "mock_front_test_app",
                PROPERTY_CLIENT_GRAPH_ID, graphId,
                "graph_json", objectMapper.writeValueAsString(graphConfig)))
            // 外部进来的command都不指定destination_locations
            // 不指定destination_locations，默认为空
                .build();

        // 连接WebSocket客户端 (提前连接，因为start_graph也将通过WebSocket发送)
        CompletableFuture<Message> startGraphResponseFuture
            = new CompletableFuture<>(); // 用于接收start_graph的CommandResult
        WebSocketTestClient client = new WebSocketTestClient(websocketUri, clientGroup, engine); // 构造函数修改
        client.registerCommandResponseFuture(startGraphCommand.getCommandId(),
            startGraphResponseFuture); // 注册CommandResult
        // Future
        client.connect().get(10, TimeUnit.SECONDS); // 连接并等待握手完成

        // 编码Command消息为BinaryWebSocketFrame
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new MessageEncoder());
        encoderChannel.writeOutbound(startGraphCommand);
        BinaryWebSocketFrame encodedStartGraphFrame = encoderChannel.readOutbound();
        assertNotNull(encodedStartGraphFrame, "编码后的StartGraph WebSocket帧不应为空");
        assertTrue(encodedStartGraphFrame.content().isReadable(), "编码后的StartGraph WebSocket帧内容应可读");

        client.sendMessage(encodedStartGraphFrame).sync(); // 发送编码后的WebSocket帧

        log.info("WebSocket客户端发送start_graph命令 (已编码): commandId={}, graphId={}, appUri={}",
            startGraphCommand.getCommandId(), startGraphCommand.getArg("graph_id", String.class).orElse("N/A"),
            startGraphCommand.getArg("app_uri", String.class).orElse("N/A")); // 使用getArg获取属性

        log.info("等待接收start_graph命令结果，最长等待10秒...");
        CommandResult startGraphResult = (CommandResult)startGraphResponseFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(startGraphResult, "应收到start_graph的CommandResult");
        assertTrue(startGraphResult.isSuccess(), "start_graph命令应成功");
        log.info("start_graph命令结果: {}", startGraphResult);

        TimeUnit.SECONDS.sleep(3); // 延长等待时间，确保图实例启动稳定

        GraphInstance actualGraphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", actualGraphInstance.getGraphId());

        // 步骤 4: 发送一个Data消息并等待回显
        Data testData = Data.json("echo_test_data",
            objectMapper.writeValueAsString(Map.of("content", "Hello WebSocket Echo!")));
        testData.setProperty(PROPERTY_CLIENT_LOCATION_URI, graphId);
        testData.setProperty(PROPERTY_CLIENT_APP_URI, appUri);

        // 手动编码Data消息为BinaryWebSocketFrame，模拟前端Netty编码行为
        encoderChannel = new EmbeddedChannel(new MessageEncoder()); // 重用encoderChannel
        encoderChannel.writeOutbound(testData);
        BinaryWebSocketFrame encodedDataFrame = encoderChannel.readOutbound();
        assertNotNull(encodedDataFrame, "编码后的Data WebSocket帧不应为空");
        assertTrue(encodedDataFrame.content().isReadable(), "编码后的Data WebSocket帧内容应可读");

        // 使用WebSocketTestClient的dataResponseFuture来接收Data回显
        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        client.setDataResponseFuture(wsEchoResponseFuture); // 更新WebSocketTestClient的回调Future
        client.sendMessage(encodedDataFrame).sync(); // 发送编码后的WebSocket帧

        log.info("WebSocket客户端发送Data消息 (已编码): messageName={}, sourceLocation={}, destinationLocations={}",
            testData.getName(), testData.getSourceLocation(), testData.getDestinationLocations());

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(5, TimeUnit.SECONDS); // 延长超时时间
        log.info("成功接收到WebSocket回显消息: {}", receivedWsMessage);
        assertNotNull(receivedWsMessage, "应收到WebSocket回显消息");
        assertTrue(receivedWsMessage instanceof Data, "WebSocket回显消息应为Data类型");
        Data wsEchoData = (Data) receivedWsMessage;
        assertEquals(MessageConstants.DATA_NAME_ECHO_DATA, wsEchoData.getName());

        Map<String, Object> receivedPayload = objectMapper.readValue(wsEchoData.getData().toString(CharsetUtil.UTF_8),
                Map.class);
        assertEquals("Echo: Hello WebSocket Echo!", receivedPayload.get("content"));

        log.info("成功接收到回显WebSocket Data消息: {}", wsEchoData.getName());

        // 2. 模拟发送 stop_graph 命令进行清理 - 通过WebSocket发送
        Command stopGraphCommand = Command.builder()
            .commandId(UUID.randomUUID().getMostSignificantBits())
            .name("stop_graph") // 设置命令名称
            .properties(Map.of(
                "graph_id", graphId,
                "app_uri", appUri))
            .sourceLocation(Location.builder().appUri("ws_client").graphId("N/A").extensionName("N/A")
                .build()) // 模拟来自WebSocket客户端
                .build();

        CompletableFuture<Message> stopGraphResponseFuture = new CompletableFuture<>();
        client.registerCommandResponseFuture(stopGraphCommand.getCommandId(),
            stopGraphResponseFuture); // 注册CommandResult
        // Future

        encoderChannel = new EmbeddedChannel(new MessageEncoder()); // 重用encoderChannel
        encoderChannel.writeOutbound(stopGraphCommand);
        BinaryWebSocketFrame encodedStopGraphFrame = encoderChannel.readOutbound();
        assertNotNull(encodedStopGraphFrame, "编码后的StopGraph WebSocket帧不应为空");
        assertTrue(encodedStopGraphFrame.content().isReadable(), "编码后的StopGraph WebSocket帧内容应可读");

        client.sendMessage(encodedStopGraphFrame).sync(); // 发送编码后的WebSocket帧

        log.info("WebSocket客户端发送stop_graph命令 (已编码): commandId={}, graphId={}, appUri={}",
            stopGraphCommand.getCommandId(), stopGraphCommand.getArg("graph_id", String.class).orElse("N/A"),
            stopGraphCommand.getArg("app_uri", String.class).orElse("N/A")); // 使用getArg获取属性

        log.info("等待接收stop_graph命令结果，最长等待10秒...");
        CommandResult stopGraphResult = (CommandResult)stopGraphResponseFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(stopGraphResult, "应收到stop_graph的CommandResult");
        assertTrue(stopGraphResult.isSuccess(), "stop_graph命令应成功");
        log.info("stop_graph命令结果: {}", stopGraphResult);

        TimeUnit.SECONDS.sleep(1);

        assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");

        client.disconnect().get(10, TimeUnit.SECONDS); // 客户端断开连接
    }
}
