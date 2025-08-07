package com.tenframework.agent.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tenframework.core.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.server.TenServer;
import com.tenframework.server.message.MessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tenframework.server.handler.ByteBufToWebSocketFrameEncoder;
import com.tenframework.server.handler.WebSocketFrameToByteBufDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class EndToEndIntegrationTest {

    // 将端口设置为动态获取，避免端口冲突
    private static final int ANY_AVAILABLE_PORT = 0; // 修改为更通用的名称
    // private static final int HTTP_PORT = 0; // 移除此行
    private static final int FIXED_HTTP_PORT = 8080; // 明确HTTP固定端口，用于测试
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Engine engine;
    private TenServer tenServer; // 使用TenServer
    private EventLoopGroup clientGroup;

    @BeforeEach
    void setUp() throws Exception {
        engine = new Engine("test-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 使用TenServer启动服务，让操作系统自动分配端口
        tenServer = new TenServer(ANY_AVAILABLE_PORT, engine); // 传入一个端口
        tenServer.start().get(10, TimeUnit.SECONDS); // 延长超时时间
        // 获取实际绑定的端口
        int actualPort = tenServer.getPort(); // 统一获取端口
        log.info("TenServer started on port {}", actualPort); // 更新日志

        clientGroup = new NioEventLoopGroup();
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
        // 1. 通过 HTTP 接口发送一个 start_graph 命令
        log.info("--- 测试 HTTP /start_graph 命令 ---");
        String graphId = UUID.randomUUID().toString();

        // 构建 start_graph 命令的 properties
        Map<String, Object> commandProperties = new HashMap<>();
        commandProperties.put("graph_id", graphId);
        commandProperties.put("app_uri", "test-app");

        // 定义节点
        com.fasterxml.jackson.databind.node.ArrayNode nodesArray = objectMapper.createArrayNode(); // 更改为ArrayNode
        nodesArray.add(objectMapper.createObjectNode()
                .put("name", "SimpleEcho")
                .put("type", "com.tenframework.core.extension.SimpleEchoExtension"));
        nodesArray.add(objectMapper.createObjectNode()
                .put("name", "tcp-client-extension")
                .put("type", "com.tenframework.core.extension.SimpleEchoExtension"));
        // nodes.put("tcp-client-extension", objectMapper.createObjectNode()
        // .put("type", "com.tenframework.core.extension.SimpleEchoExtension"));

        // 定义连接
        com.fasterxml.jackson.databind.node.ArrayNode connections = objectMapper.createArrayNode();
        connections.add(objectMapper.createObjectNode()
                .put("source", "tcp-client-extension")
                .set("destinations", objectMapper.createArrayNode().add("SimpleEcho")));
        connections.add(objectMapper.createObjectNode()
                .put("source", "SimpleEcho")
                .set("destinations", objectMapper.createArrayNode().add("tcp-client-extension")));

        // 构建 graph_json ObjectNode，并转换为字符串
        ObjectNode graphJsonNode = objectMapper.createObjectNode();
        graphJsonNode.put("graph_id", graphId); // 这里的 graph_id 是冗余的，但保留以匹配原始结构
        graphJsonNode.set("nodes", nodesArray); // 将 ArrayNode 设置为 nodes
        graphJsonNode.set("connections", connections);

        commandProperties.put("graph_json", graphJsonNode.toString()); // 将ObjectNode转换为String

        // 构建整个 HTTP 请求的 payload (它将成为 Command 的参数)
        Map<String, Object> startGraphPayload = new HashMap<>();
        startGraphPayload.put("command_id", UUID.randomUUID().toString());
        startGraphPayload.put("name", "start_graph");
        startGraphPayload.put("properties", commandProperties); // 将构建好的properties放入payload
        startGraphPayload.put("source_location",
                Map.of("app_uri", "http_client", "graph_id", "N/A", "extension_name", "N/A"));
        startGraphPayload.put("destination_locations", Collections.emptyList());

        String httpResponse = sendHttpRequest(FIXED_HTTP_PORT, "/start_graph", "POST", startGraphPayload);
        assertTrue(httpResponse.contains("\"status\":\"success\""), "HTTP start_graph 响应应包含 'status:success'");
        log.info("HTTP /start_graph 响应: {}", httpResponse);
        TimeUnit.SECONDS.sleep(1);

        // 获取并验证 GraphInstance，然后注册 Extension
        GraphInstance graphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", graphInstance.getGraphId());

        // 2. 通过 WebSocket/MsgPack 接口发送一个 Data 消息给 SimpleEchoExtension
        log.info("--- 测试 WebSocket/MsgPack Data 消息回显 ---");
        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        URI websocketUri = new URI("ws://localhost:" + tenServer.getPort() + "/websocket"); // 使用统一端口

        sendWebSocketDataMessage(websocketUri, graphId, "echo_test_data", Map.of("content", "Hello WebSocket Echo!"),
                wsEchoResponseFuture, clientGroup);

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(5, TimeUnit.SECONDS);
        log.info("成功接收到WebSocket回显消息: {}", receivedWsMessage);
        assertNotNull(receivedWsMessage, "应收到WebSocket回显消息");
        assertTrue(receivedWsMessage instanceof Data, "WebSocket回显消息应为Data类型");
        Data wsEchoData = (Data) receivedWsMessage;
        assertEquals(MessageConstants.DATA_NAME_ECHO_DATA, wsEchoData.getName());

        // 解析数据内容并断言
        Map<String, Object> receivedPayload = objectMapper.readValue(wsEchoData.getData().toString(CharsetUtil.UTF_8),
                Map.class);
        assertEquals("Echo: Hello WebSocket Echo!", receivedPayload.get("content"));

        log.info("成功接收到回显WebSocket Data消息: {}", wsEchoData.getName());
        TimeUnit.SECONDS.sleep(1);

        // 3. 通过 HTTP 接口发送 stop_graph 命令
        log.info("--- 测试 HTTP /stop_graph 命令 ---");
        Map<String, Object> stopGraphPayload = new HashMap<>();
        Map<String, Object> stopGraphProperties = Map.of("graph_id", graphId, "app_uri", "test-app");
        stopGraphPayload.put("command_id", UUID.randomUUID().toString());
        stopGraphPayload.put("name", "stop_graph");
        stopGraphPayload.put("properties", stopGraphProperties);
        stopGraphPayload.put("source_location",
                Map.of("app_uri", "http_client", "graph_id", "N/A", "extension_name", "N/A"));
        stopGraphPayload.put("destination_locations", Collections.emptyList());

        String stopResponse = sendHttpRequest(FIXED_HTTP_PORT, "/stop_graph", "POST", stopGraphPayload);
        assertTrue(stopResponse.contains("\"status\":\"success\""), "HTTP stop_graph 响应应包含 'status:success'");
        log.info("HTTP /stop_graph 响应: {}", stopResponse);
        TimeUnit.SECONDS.sleep(1);

        // 4. 测试 Engine /ping
        log.info("--- 测试 HTTP /ping 命令 ---");
        String pingResponse = sendHttpRequest(FIXED_HTTP_PORT, "/ping", "POST", Collections.emptyMap());
        assertTrue(pingResponse.contains("\"status\":\"pong\""), "HTTP /ping 响应应包含 'status:pong'");
        log.info("HTTP /ping 响应: {}", pingResponse);
    }

    // 简化版的HTTP客户端发送请求（仅用于示例）
    private String sendHttpRequest(int port, String path, String method, Map<String, Object> payload) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String requestBody = objectMapper.writeValueAsString(payload);
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(CharsetUtil.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        log.debug("HTTP请求 {} {}，响应码: {}", method, path, responseCode);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), CharsetUtil.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        } catch (Exception e) {
            log.warn("读取HTTP响应失败或无响应体", e);
            if (connection.getErrorStream() != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), CharsetUtil.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = br.readLine()) != null) {
                        errorResponse.append(errorLine.trim());
                    }
                    log.error("HTTP错误响应体: {}", errorResponse.toString());
                }
            }
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    private void sendWebSocketDataMessage(URI uri, String graphId, String messageName, Map<String, Object> payload,
            CompletableFuture<Message> responseFuture, EventLoopGroup group) throws Exception {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192));
                        // WebSocket 握手处理
                        ch.pipeline().addLast("websocket-client-handler", new WebSocketClientHandler( // 添加名称
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()),
                                responseFuture));

                        // WebSocket帧到ByteBuf解码 (入站)
                        ch.pipeline().addLast(new WebSocketFrameToByteBufDecoder());
                        // ByteBuf到Message解码 (入站)
                        ch.pipeline().addLast(new MessageDecoder());
                        // Message到ByteBuf编码 (出站)
                        ch.pipeline().addLast(new MessageEncoder());
                        // ByteBuf到WebSocket帧编码 (出站)
                        ch.pipeline().addLast(new ByteBufToWebSocketFrameEncoder());

                    }
                });

        Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();

        // 通过名称获取 WebSocketClientHandler 实例
        WebSocketClientHandler handler = (WebSocketClientHandler) ch.pipeline().get("websocket-client-handler");
        handler.handshakeFuture().sync();

        Data testData = Data.json(messageName, objectMapper.writeValueAsString(payload));
        testData.setSourceLocation(Location.builder().appUri("test-client").graphId(graphId)
                .extensionName("tcp-client-extension").build());
        testData.setDestinationLocations(
                Collections.singletonList(Location.builder().appUri("test-app").graphId(graphId)
                        .extensionName("SimpleEcho").build()));

        handler.sendMessage(testData).sync();

        log.info("WebSocket客户端发送Data消息: messageName={}, sourceLocation={}, destinationLocations={}",
                testData.getName(), testData.getSourceLocation(), testData.getDestinationLocations());
    }

    private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private final Promise<Void> handshakeFuture;
        private final CompletableFuture<Message> responseFuture;
        private Channel channel;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker, CompletableFuture<Message> responseFuture) {
            this.handshaker = handshaker;
            handshakeFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            this.responseFuture = responseFuture;
        }

        public Promise<Void> handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            channel = ctx.channel();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("WebSocket Client disconnected.");
        }

        public ChannelFuture sendMessage(Message message) throws Exception {
            if (channel == null || !channel.isActive()) {
                throw new IllegalStateException("WebSocket channel is not active.");
            }
            // MessageEncoder现在是pipeline的一部分，直接发送Message
            return channel.writeAndFlush(message); // 直接发送Message，由pipeline中的MessageEncoder处理
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("WebSocket Client连接成功!");
                handshakeFuture.setSuccess(null);
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status() + ", content="
                                + response.content().toString(CharsetUtil.UTF_8) + ')');
            }

            // 现在应该接收到 Message 类型，因为解码器已经在前面处理了
            if (msg instanceof Message) { // 直接处理 Message
                Message decodedMsg = (Message) msg;
                responseFuture.complete(decodedMsg);
                log.info("WebSocket客户端成功解码并接收到回显消息: {}", decodedMsg.getName());
            } else if (msg instanceof WebSocketFrame) { // 仍然处理其他WebSocket帧
                WebSocketFrame frame = (WebSocketFrame) msg;
                if (frame instanceof TextWebSocketFrame) {
                    TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                    log.warn("WebSocket客户端收到文本帧: {}", textFrame.text());
                } else if (frame instanceof PongWebSocketFrame) {
                    log.debug("WebSocket客户端收到Pong帧。");
                } else if (frame instanceof CloseWebSocketFrame) {
                    log.info("WebSocket客户端收到Close帧，关闭连接: code={}, reason={}",
                            ((CloseWebSocketFrame) frame).statusCode(), ((CloseWebSocketFrame) frame).reasonText());
                    ch.close();
                }
            } else {
                log.warn("WebSocket客户端收到未知消息类型: {}", msg.getClass().getName());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("WebSocket客户端处理异常", cause);
            responseFuture.completeExceptionally(cause);
            handshakeFuture.setFailure(cause);
            ctx.close();
        }
    }
}