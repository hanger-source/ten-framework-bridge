package com.tenframework.agent.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.BaseExtension;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.server.message.MessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import com.tenframework.server.TenServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.Command;
import io.netty.handler.codec.http.FullHttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;

@Slf4j
public class WebSocketIntegrationTest {

    private static final int WEBSOCKET_PORT = findAvailablePort();
    private static final int HTTP_PORT = findAvailablePort(); // 为TenServer添加HTTP端口
    private Engine engine;
    private TenServer tenServer; // 使用TenServer
    private EventLoopGroup clientGroup;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private SimpleEchoExtension echoExtension;
    private BaseExtension tcpClientExtension;

    @BeforeEach
    void setUp() throws Exception {
        engine = new Engine("test-websocket-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 使用TenServer启动WebSocket服务
        tenServer = new TenServer(WEBSOCKET_PORT, HTTP_PORT, engine); // 提供HTTP端口
        tenServer.start().get(5, TimeUnit.SECONDS);
        log.info("TenServer (WebSocket) started on port {}", WEBSOCKET_PORT);

        clientGroup = new NioEventLoopGroup();

        // 初始化Extension实例 (如果这些Extension只在测试代码中模拟 Engine.submitMessage，
        // 那么它们可能不需要实际注册到 Engine，除非测试涉及到 Engine 内部对它们的调用)
        echoExtension = new SimpleEchoExtension();
        tcpClientExtension = new BaseExtension() {
            @Override
            protected void handleCommand(com.tenframework.core.message.Command command,
                    com.tenframework.core.extension.ExtensionContext context) {
            }

            @Override
            protected void handleData(com.tenframework.core.message.Data data,
                    com.tenframework.core.extension.ExtensionContext context) {
            }

            @Override
            protected void handleAudioFrame(com.tenframework.core.message.AudioFrame audioFrame,
                    com.tenframework.core.extension.ExtensionContext context) {
            }

            @Override
            protected void handleVideoFrame(com.tenframework.core.message.VideoFrame videoFrame,
                    com.tenframework.core.extension.ExtensionContext context) {
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tenServer != null) {
            tenServer.shutdown().get(5, TimeUnit.SECONDS);
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
    void testWebSocketEchoFlow() throws Exception {
        log.info("--- 测试 WebSocket/MsgPack Data 消息回显 ---");
        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        URI websocketUri = new URI("ws://localhost:" + WEBSOCKET_PORT + "/websocket");
        String graphId = UUID.randomUUID().toString();
        String appUri = "test-app";

        // 1. 模拟发送 start_graph 命令来启动图实例
        // 构建图的JSON结构（作为ObjectNode）
        ObjectNode graphJsonNode = objectMapper.createObjectNode();
        graphJsonNode.put("graph_id", graphId);
        // 定义节点
        com.fasterxml.jackson.databind.node.ArrayNode nodesArray = objectMapper.createArrayNode(); // 更改为ArrayNode
        nodesArray.add(objectMapper.createObjectNode()
                .put("name", "SimpleEcho")
                .put("type", "com.tenframework.core.extension.SimpleEchoExtension"));
        nodesArray.add(objectMapper.createObjectNode()
                .put("name", "tcp-client-extension")
                .put("type", "com.tenframework.core.extension.SimpleEchoExtension"));
        // ObjectNode nodes = objectMapper.createObjectNode(); // 原始的ObjectNode定义，注释掉或删除
        // nodes.put("SimpleEcho", objectMapper.createObjectNode()
        // .put("type", "com.tenframework.core.extension.SimpleEchoExtension")
        // .put("name", "SimpleEcho"));
        // nodes.put("tcp-client-extension", objectMapper.createObjectNode()
        // .put("type", "com.tenframework.core.extension.SimpleEchoExtension")
        // .put("name", "tcp-client-extension"));
        graphJsonNode.set("nodes", nodesArray); // 将 ArrayNode 设置为 nodes

        // 定义连接
        com.fasterxml.jackson.databind.node.ArrayNode connections = objectMapper.createArrayNode();
        // tcp-client-extension -> SimpleEcho
        connections.add(objectMapper.createObjectNode()
                .put("source", "tcp-client-extension")
                .set("destinations", objectMapper.createArrayNode().add("SimpleEcho")));
        // SimpleEcho -> tcp-client-extension (回显)
        connections.add(objectMapper.createObjectNode()
                .put("source", "SimpleEcho")
                .set("destinations", objectMapper.createArrayNode().add("tcp-client-extension")));
        graphJsonNode.set("connections", connections);

        Command startGraphCommand = Command.builder()
                .name("start_graph")
                .commandId(UUID.randomUUID().toString())
                .properties(new HashMap<>() {
                    { // 使用HashMap来构建properties
                        put("graph_id", graphId);
                        put("app_uri", appUri);
                        put("graph_json", graphJsonNode.toString()); // 将ObjectNode转换为String
                    }
                })
                .sourceLocation(new Location("test://client", graphId, "client"))
                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                .build();
        engine.submitMessage(startGraphCommand);
        TimeUnit.SECONDS.sleep(1);

        GraphInstance actualGraphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", actualGraphInstance.getGraphId());

        sendWebSocketDataMessage(websocketUri, graphId, "echo_test_data", Map.of("content", "Hello WebSocket Echo!"),
                wsEchoResponseFuture, clientGroup);

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(5, TimeUnit.SECONDS);
        log.info("成功接收到WebSocket回显消息: {}", receivedWsMessage);
        assertNotNull(receivedWsMessage, "应收到WebSocket回显消息");
        assertTrue(receivedWsMessage instanceof Data, "WebSocket回显消息应为Data类型");
        Data wsEchoData = (Data) receivedWsMessage;
        assertEquals(MessageConstants.DATA_NAME_ECHO_DATA, wsEchoData.getName());

        Map<String, Object> receivedPayload = objectMapper.readValue(wsEchoData.getData().toString(CharsetUtil.UTF_8),
                Map.class);
        assertEquals("Echo: Hello WebSocket Echo!", receivedPayload.get("content"));

        log.info("成功接收到回显WebSocket Data消息: {}", wsEchoData.getName());

        // 2. 模拟发送 stop_graph 命令进行清理
        Command stopGraphCommand = Command.builder()
                .name("stop_graph")
                .commandId(UUID.randomUUID().toString())
                .properties(new HashMap<>() {
                    { // 使用HashMap构建properties
                        put("graph_id", graphId);
                        put("app_uri", appUri);
                    }
                })
                .sourceLocation(new Location("test://client", graphId, "client"))
                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                .build();
        engine.submitMessage(stopGraphCommand);
        TimeUnit.SECONDS.sleep(1);

        assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
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

    private static class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
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
            List<Object> encodedFrames = new java.util.ArrayList<>();
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
                    List<Object> decodedMsgs = new java.util.ArrayList<>();
                    messageDecoder.decode(ctx, new BinaryWebSocketFrame(binaryFrame.content().retain()), decodedMsgs);

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