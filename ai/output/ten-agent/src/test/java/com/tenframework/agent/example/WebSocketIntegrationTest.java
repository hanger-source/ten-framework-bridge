package com.tenframework.agent.example;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.graph.GraphLoader;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.server.TenServer;
import com.tenframework.server.handler.ByteBufToWebSocketFrameEncoder;
import com.tenframework.server.handler.WebSocketFrameToByteBufDecoder;
import com.tenframework.server.handler.WebSocketMessageFrameHandler;
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
        engine = new Engine("test-websocket-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 使用TenServer启动服务，让操作系统自动分配端口
        tenServer = new TenServer(ANY_AVAILABLE_PORT, engine); // 传入一个端口
        tenServer.start().get(10, TimeUnit.SECONDS); // 延长超时时间
        // 获取实际绑定的端口
        int actualPort = tenServer.getPort(); // 统一获取端口
        log.info("TenServer (WebSocket) started on port {}", actualPort); // 更新日志

        clientGroup = new NioEventLoopGroup();

        // 初始化Extension实例 (如果这些Extension只在测试代码中模拟 Engine.submitMessage，
        // 那么它们可能不需要实际注册到 Engine，除非测试涉及到 Engine 内部对它们的调用)
        echoExtension = new SimpleEchoExtension();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tenServer != null) {
            // 500 方便开发人员debug
            tenServer.shutdown().get(500, TimeUnit.SECONDS); // 延长关闭超时时间
        }
        if (engine != null) {
            engine.stop();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(1, 10, TimeUnit.SECONDS).sync(); // 延长客户端组关闭时间
        }
        TimeUnit.MILLISECONDS.sleep(500); // 添加短暂延迟，确保所有资源已完全释放
        log.info("所有测试服务已关闭。");
    }

    @Test
    void testWebSocketEchoFlow() throws Exception {
        log.info("--- 测试 WebSocket/MsgPack Data 消息回显 ---");
        CompletableFuture<Message> wsEchoResponseFuture = new CompletableFuture<>();
        URI websocketUri = new URI("ws://localhost:" + tenServer.getPort() + "/websocket");
        String graphId = UUID.randomUUID().toString();
        String appUri = "test-app";

        // 1. 模拟发送 start_graph 命令来启动图实例
        // 构建图的JSON结构（作为ObjectNode）

        // 从JSON文件加载图配置
        String graphConfigPath = "src/test/resources/test_websocket_echo_graph.json";
        GraphConfig graphConfig = GraphLoader.loadGraphConfigFromFile(graphConfigPath);
        // 确保graphId与测试用例中生成的UUID一致
        graphConfig.setGraphId(graphId);
        graphConfig.setAppUri(appUri);

        String graphJson = objectMapper.writeValueAsString(graphConfig); // 将GraphConfig对象转换为JSON字符串

        log.info("WebSocket测试图配置: {}", graphJson);

        Command startGraphCommand = Command.builder()
                .name("start_graph")
                .commandId(UUID.randomUUID().toString())
                .properties(new HashMap<>() {
                    {
                        put("graph_id", graphId);
                        put("app_uri", appUri);
                        put("graph_json", graphJson);
                    }
                })
                .sourceLocation(new Location(MessageConstants.APP_URI_SYSTEM, "N/A", ClientConnectionExtension.NAME))
                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                .build();
        engine.submitMessage(startGraphCommand);
        TimeUnit.SECONDS.sleep(3); // 延长等待时间，确保图实例启动稳定

        GraphInstance actualGraphInstance = engine.getGraphInstance(graphId)
                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));
        log.info("成功获取到图实例: {}", actualGraphInstance.getGraphId());

        sendWebSocketDataMessage(websocketUri, graphId, appUri, "echo_test_data",
                Map.of("content", "Hello WebSocket Echo!"),
                wsEchoResponseFuture, clientGroup);

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(500, TimeUnit.SECONDS); // 延长超时时间
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
                .sourceLocation(new Location(MessageConstants.APP_URI_SYSTEM, null, ClientConnectionExtension.NAME))
                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                .build();
        engine.submitMessage(stopGraphCommand);
        TimeUnit.SECONDS.sleep(1);

        assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
    }

    private void sendWebSocketDataMessage(URI uri, String graphId, String appUri, String messageName,
            Map<String, Object> payload,
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
                        ch.pipeline().addLast(new MessageDecoder()); // Move this line
                        ch.pipeline().addLast(new WebSocketMessageFrameHandler(engine)); // Move this line
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
        testData.setProperty(MessageConstants.PROPERTY_CLIENT_LOCATION_URI, graphId);
        testData.setProperty(MessageConstants.PROPERTY_CLIENT_APP_URI, appUri);

        handler.sendMessage(testData).sync();

        log.info("WebSocket客户端发送Data消息: messageName={}, sourceLocation={}, destinationLocations={}",
                testData.getName(), testData.getSourceLocation(), testData.getDestinationLocations());
    }

    private static class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final Promise<Void> handshakeFuture;
        private final CompletableFuture<Message> responseFuture;
        // private final MessageEncoder messageEncoder = new MessageEncoder(); // 移除
        // private final MessageDecoder messageDecoder = new MessageDecoder(); // 移除
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

            if (msg instanceof FullHttpResponse response) {
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status() + ", content="
                                + response.content().toString(CharsetUtil.UTF_8) + ')');
            }

            // 现在应该接收到 Message 类型，因为解码器已经在前面处理了
            if (msg instanceof Message decodedMsg) {
                responseFuture.complete(decodedMsg);
                log.info("WebSocket客户端成功解码并接收到回显消息: {}", decodedMsg.getName());
            } else if (msg instanceof WebSocketFrame frame) {
                switch (frame) {
                    case TextWebSocketFrame textFrame -> log.warn("WebSocket客户端收到文本帧: {}", textFrame.text());
                    case PongWebSocketFrame pongWebSocketFrame -> log.debug("WebSocket客户端收到Pong帧。");
                    case CloseWebSocketFrame closeWebSocketFrame -> {
                        log.info("WebSocket客户端收到Close帧，关闭连接: code={}, reason={}",
                                closeWebSocketFrame.statusCode(), closeWebSocketFrame.reasonText());
                        ch.close();
                    }
                    default -> {
                    }
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