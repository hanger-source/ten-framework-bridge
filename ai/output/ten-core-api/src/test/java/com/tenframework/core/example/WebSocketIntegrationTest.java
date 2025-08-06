package com.tenframework.core.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.BaseExtension;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageDecoder;
import com.tenframework.core.message.MessageEncoder;
import com.tenframework.core.server.NettyMessageServer;
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

@Slf4j
public class WebSocketIntegrationTest {

    private static final int WEBSOCKET_PORT = findAvailablePort();
    private Engine engine;
    private NettyMessageServer messageServer;
    private EventLoopGroup clientGroup;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = new Engine("test-websocket-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 注册 SimpleEchoExtension
        SimpleEchoExtension echoExtension = new SimpleEchoExtension();
        Map<String, Object> echoConfig = Map.of("name", "SimpleEcho");
        boolean registered = engine.registerExtension("SimpleEcho", echoExtension, echoConfig, "test://app");
        assertTrue(registered, "SimpleEchoExtension should register successfully.");
        log.info("SimpleEchoExtension registered successfully for test.");

        // 注册一个模拟的tcp-client-extension，以便Engine能够正确路由回显消息
        BaseExtension tcpClientExtension = new BaseExtension() {
            @Override
            protected void handleCommand(com.tenframework.core.message.Command command,
                    com.tenframework.core.extension.ExtensionContext context) {
                // 空实现
            }

            @Override
            protected void handleData(com.tenframework.core.message.Data data,
                    com.tenframework.core.extension.ExtensionContext context) {
                // 空实现
            }

            @Override
            protected void handleAudioFrame(com.tenframework.core.message.AudioFrame audioFrame,
                    com.tenframework.core.extension.ExtensionContext context) {
                // 空实现
            }

            @Override
            protected void handleVideoFrame(com.tenframework.core.message.VideoFrame videoFrame,
                    com.tenframework.core.extension.ExtensionContext context) {
                // 空实现
            }
        };
        Map<String, Object> tcpClientConfig = Map.of("name", "tcp-client-extension");
        boolean tcpClientRegistered = engine.registerExtension("tcp-client-extension", tcpClientExtension,
                tcpClientConfig, "test://app");
        assertTrue(tcpClientRegistered, "TCP Client Extension should register successfully.");
        log.info("TCP Client Extension registered successfully for test.");

        messageServer = new NettyMessageServer(WEBSOCKET_PORT, engine);
        new Thread(() -> {
            try {
                messageServer.start();
            } catch (Exception e) {
                log.error("NettyMessageServer启动失败", e);
            }
        }, "Test-WebSocket-Server-Thread").start();
        messageServer.getStartFuture().get(5, TimeUnit.SECONDS);
        log.info("NettyMessageServer (WebSocket) started on port {}", WEBSOCKET_PORT);

        clientGroup = new NioEventLoopGroup();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (messageServer != null) {
            messageServer.shutdown();
            messageServer.getShutdownFuture().get(5, TimeUnit.SECONDS);
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
        URI websocketUri = new URI("ws://localhost:" + WEBSOCKET_PORT + "/websocket"); // WebSocket路径
        String graphId = UUID.randomUUID().toString();

        sendWebSocketDataMessage(websocketUri, graphId, "echo_test_data", Map.of("content", "Hello WebSocket Echo!"),
                wsEchoResponseFuture, clientGroup);

        log.info("等待接收WebSocket回显消息，最长等待5秒...");
        Message receivedWsMessage = wsEchoResponseFuture.get(5, TimeUnit.SECONDS);
        log.info("成功接收到WebSocket回显消息: {}", receivedWsMessage);
        assertNotNull(receivedWsMessage, "应收到WebSocket回显消息");
        assertTrue(receivedWsMessage instanceof Data, "WebSocket回显消息应为Data类型");
        Data wsEchoData = (Data) receivedWsMessage;
        assertEquals("echo_data", wsEchoData.getName());

        // 解析数据内容并断言
        Map<String, Object> receivedPayload = objectMapper.readValue(wsEchoData.getData().toString(CharsetUtil.UTF_8),
                Map.class);
        assertEquals("Echo: Hello WebSocket Echo!", receivedPayload.get("content")); // 修改断言以匹配Echo前缀

        log.info("成功接收到回显WebSocket Data消息: {}", wsEchoData.getName());
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
            messageEncoder.encode(null, message, encodedFrames); // encoder.encode expects List<Object>
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
            return channel.writeAndFlush(binaryFrame);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (io.netty.handler.codec.http.FullHttpResponse) msg);
                log.info("WebSocket Client连接成功!");
                handshakeFuture.setSuccess(null);
                return;
            }

            if (msg instanceof io.netty.handler.codec.http.FullHttpResponse) {
                io.netty.handler.codec.http.FullHttpResponse response = (io.netty.handler.codec.http.FullHttpResponse) msg;
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
                    messageDecoder.decode(ctx, binaryFrame, decodedMsgs); // decoder.decode expects List<Object>

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