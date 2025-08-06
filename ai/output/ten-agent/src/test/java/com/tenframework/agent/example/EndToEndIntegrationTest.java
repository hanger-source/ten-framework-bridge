package com.tenframework.agent.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.BaseExtension;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Data;
import com.tenframework.core.Location;
import com.tenframework.core.message.Message;
import com.tenframework.server.message.MessageDecoder; // 从ten-server导入
import com.tenframework.server.message.MessageEncoder; // 从ten-server导入
import com.tenframework.server.TenServer; // 从ten-server导入

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.channel.SimpleChannelInboundHandler;
import com.tenframework.core.extension.ExtensionContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.FullHttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import io.netty.util.CharsetUtil;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.ArrayList;
import java.util.List;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.graph.GraphInstance;

@Slf4j
public class EndToEndIntegrationTest {

    // 将端口设置为动态获取，避免端口冲突
    private static final int TCP_PORT = findAvailablePort();
    private static final int HTTP_PORT = findAvailablePort();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Engine engine;
    private TenServer tenServer; // 使用TenServer
    private EventLoopGroup clientGroup;

    @BeforeEach
    void setUp() throws Exception {
        engine = new Engine("test-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 使用TenServer启动TCP和HTTP服务
        tenServer = new TenServer(TCP_PORT, HTTP_PORT, engine);
        tenServer.start().get(5, TimeUnit.SECONDS); // 阻塞等待服务器启动完成
        log.info("TenServer started on TCP port {} and HTTP port {}", TCP_PORT, HTTP_PORT);

        clientGroup = new NioEventLoopGroup();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tenServer != null) {
            tenServer.shutdown().get(5, TimeUnit.SECONDS); // 关闭TenServer
        }
        if (engine != null) {
            engine.stop();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        log.info("所有测试服务已关闭。");
    }

    @Test
    void testEndToEndFlow() throws Exception {
        // 1. 通过 HTTP 接口发送一个 start_graph 命令
        log.info("--- 测试 HTTP /start_graph 命令 ---");
        String graphId = UUID.randomUUID().toString();
        Map<String, Object> startGraphPayload = new HashMap<>();
        startGraphPayload.put("graph_id", graphId);
        startGraphPayload.put("app_uri", "test-app");
        startGraphPayload.put("properties", Map.of("initial_message", "Hello from HTTP start_graph!"));

        // 添加 graph_json 属性，定义图的结构
        String graphJson = String.format(
                "{\"graph_id\":\"%s\",\"nodes\":[{\"name\":\"SimpleEcho\",\"type\":\"com.tenframework.core.extension.SimpleEchoExtension\"},{\"name\":\"tcp-client-extension\",\"type\":\"com.tenframework.core.extension.BaseExtension\"}],\"connections\":[{\"source\":\"tcp-client-extension\",\"destinations\":[\"SimpleEcho\"]},{\"source\":\"SimpleEcho\",\"destinations\":[\"tcp-client-extension\"]}]}",
                graphId);
        startGraphPayload.put("graph_json", graphJson);

        String httpResponse = sendHttpRequest(HTTP_PORT, "/start_graph", "POST", startGraphPayload);
        assertTrue(httpResponse.contains("\"status\":\"success\""), "HTTP start_graph 响应应包含 'status:success'");
        log.info("HTTP /start_graph 响应: {}", httpResponse);
        TimeUnit.SECONDS.sleep(1);

        // 获取并验证 GraphInstance，然后注册 Extension
        GraphInstance graphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", graphInstance.getGraphId());

        // 注册 SimpleEchoExtension
        SimpleEchoExtension echoExtension = new SimpleEchoExtension();
        Map<String, Object> echoConfig = Map.of("name", "SimpleEcho");
        boolean registeredEcho = graphInstance.registerExtension("SimpleEcho", echoExtension, echoConfig);
        assertTrue(registeredEcho, "SimpleEchoExtension should register successfully.");
        log.info("SimpleEchoExtension registered successfully on GraphInstance.");

        // 注册一个模拟的tcp-client-extension，以便Engine能够正确路由回显消息
        BaseExtension tcpClientExtension = new BaseExtension() {
            @Override
            protected void handleCommand(com.tenframework.core.message.Command command, ExtensionContext context) {
            }

            @Override
            protected void handleData(com.tenframework.core.message.Data data, ExtensionContext context) {
            }

            @Override
            protected void handleAudioFrame(com.tenframework.core.message.AudioFrame audioFrame,
                    ExtensionContext context) {
            }

            @Override
            protected void handleVideoFrame(com.tenframework.core.message.VideoFrame videoFrame,
                    ExtensionContext context) {
            }
        };
        Map<String, Object> tcpClientConfig = Map.of("name", "tcp-client-extension");
        boolean registeredTcpClient = graphInstance.registerExtension("tcp-client-extension", tcpClientExtension,
                tcpClientConfig);
        assertTrue(registeredTcpClient, "TCP Client Extension should register successfully on GraphInstance.");
        log.info("TCP Client Extension registered successfully on GraphInstance.");

        // 2. 通过 WebSocket/MsgPack 接口发送一个 Data 消息给 SimpleEchoExtension
        log.info("--- 测试 WebSocket/MsgPack Data 消息回显 ---");
        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        URI websocketUri = new URI("ws://localhost:" + TCP_PORT + "/websocket");

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
        Map<String, Object> stopGraphPayload = Map.of("graph_id", graphId, "app_uri", "test-app");
        String stopResponse = sendHttpRequest(HTTP_PORT, "/stop_graph", "POST", stopGraphPayload);
        assertTrue(stopResponse.contains("\"status\":\"success\""), "HTTP stop_graph 响应应包含 'status:success'");
        log.info("HTTP /stop_graph 响应: {}", stopResponse);
        TimeUnit.SECONDS.sleep(1);

        // 4. 测试 Engine /ping
        log.info("--- 测试 HTTP /ping 命令 ---");
        String pingResponse = sendHttpRequest(HTTP_PORT, "/ping", "POST", Collections.emptyMap());
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

    // TCP/MsgPack 客户端发送 Data 消息并接收回显
    private void sendTcpDataMessage(int port, String graphId, String messageName, Map<String, Object> payload,
            CompletableFuture<Message> responseFuture, EventLoopGroup group) throws Exception {
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 出站处理器 (编码消息以便发送)
                            ch.pipeline().addLast(new LengthFieldPrepender(4));
                            ch.pipeline().addLast(new MessageEncoder());

                            // 入站处理器 (解码接收到的消息)
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                            ch.pipeline().addLast(new MessageDecoder());

                            // 业务逻辑处理器
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
                                    log.debug("TCP客户端收到消息: type={}, name={}", msg.getType(), msg.getName());
                                    responseFuture.complete(msg);
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    log.error("TCP客户端处理消息时发生异常", cause);
                                    responseFuture.completeExceptionally(cause);
                                    ctx.close();
                                }
                            });
                        }
                    });

            log.info("TCP客户端尝试连接到localhost:{}", port);
            ChannelFuture f = b.connect("localhost", port).sync();
            log.info("TCP客户端已连接到localhost:{}", port);
            Channel channel = f.channel();

            // 构建 Data 消息
            // 将 payload 转换为 JSON 字符串
            String jsonPayload = objectMapper.writeValueAsString(payload);
            Data testData = Data.json(messageName, jsonPayload);
            testData.setSourceLocation(Location.builder().appUri("test-app").graphId(graphId)
                    .extensionName("tcp-client-extension").build());
            testData.setDestinationLocations(
                    Collections.singletonList(Location.builder().appUri("test-app").graphId(graphId)
                            .extensionName("SimpleEcho").build()));

            log.info("TCP客户端发送Data消息: messageName={}, sourceLocation={}, destinationLocations={}",
                    testData.getName(), testData.getSourceLocation(), testData.getDestinationLocations());
            channel.writeAndFlush(testData).sync();

        } catch (Exception e) {
            log.error("TCP客户端发送消息失败", e);
            throw e;
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
                        ch.pipeline().addLast(new WebSocketClientHandler(
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()),
                                responseFuture));
                    }
                });

        Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();

        WebSocketClientHandler handler = (WebSocketClientHandler) ch.pipeline().last();
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

        private final MessageEncoder messageEncoder = new MessageEncoder();
        private final MessageDecoder messageDecoder = new MessageDecoder();

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker, CompletableFuture<Message> responseFuture) {
            this.handshaker = handshaker;
            this.handshakeFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            this.responseFuture = responseFuture;
        }

        public Promise<Void> handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.channel = ctx.channel();
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
            List<Object> encodedFrames = new ArrayList<>();
            messageEncoder.encode(null, message, encodedFrames);
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
            return channel.writeAndFlush(binaryFrame);
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

            if (msg instanceof WebSocketFrame) {
                WebSocketFrame frame = (WebSocketFrame) msg;
                if (frame instanceof TextWebSocketFrame) {
                    TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                    log.warn("WebSocket客户端收到文本帧: {}", textFrame.text());
                } else if (frame instanceof BinaryWebSocketFrame) {
                    BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
                    log.debug("WebSocket客户端收到二进制帧，大小: {} 字节", binaryFrame.content().readableBytes());

                    List<Object> decodedMsgs = new ArrayList<>();
                    messageDecoder.decode(ctx, binaryFrame, decodedMsgs);

                    if (!decodedMsgs.isEmpty() && decodedMsgs.get(0) instanceof Message) {
                        Message decodedMsg = (Message) decodedMsgs.get(0);
                        responseFuture.complete(decodedMsg);
                        log.info("WebSocket客户端成功解码并接收到回显消息: {}", decodedMsg.getName());
                        ctx.close();
                    } else {
                        log.warn("WebSocket客户端无法解码二进制帧为TEN消息。");
                    }
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

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法找到可用端口: " + e.getMessage(), e);
        }
    }
}