package com.tenframework.core.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.SimpleEchoExtension;
import com.tenframework.core.message.Data;
import com.tenframework.core.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageDecoder;
import com.tenframework.core.message.MessageEncoder;
import com.tenframework.core.server.NettyHttpServer;
import com.tenframework.core.server.NettyMessageServer;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import io.netty.util.CharsetUtil;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class EndToEndIntegrationTest {

    private static final int TCP_PORT = 8080;
    private static final int HTTP_PORT = 8081;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Engine engine;
    private NettyMessageServer messageServer;
    private NettyHttpServer httpServer;

    @BeforeEach
    void setUp() throws Exception {
        engine = new Engine("test-engine");
        engine.start();
        log.info("Engine [{}] started for test.", engine.getEngineId());

        // 注册 SimpleEchoExtension
        SimpleEchoExtension echoExtension = new SimpleEchoExtension();
        Map<String, Object> echoConfig = Map.of("name", "SimpleEcho");
        boolean registered = engine.registerExtension("SimpleEcho", echoExtension, echoConfig);
        assertTrue(registered, "SimpleEchoExtension should register successfully.");
        log.info("SimpleEchoExtension registered successfully for test.");

        messageServer = new NettyMessageServer(TCP_PORT, engine);
        new Thread(() -> {
            try {
                messageServer.start();
            } catch (Exception e) {
                log.error("NettyMessageServer启动失败", e);
            }
        }, "Test-TCP-Server-Thread").start();

        httpServer = new NettyHttpServer(HTTP_PORT, engine);
        new Thread(() -> {
            try {
                httpServer.start();
            } catch (Exception e) {
                log.error("NettyHttpServer启动失败", e);
            }
        }, "Test-HTTP-Server-Thread").start();

        TimeUnit.SECONDS.sleep(2); // 等待服务器启动
        log.info("所有测试服务已启动。");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.shutdown();
        }
        if (messageServer != null) {
            messageServer.shutdown();
        }
        if (engine != null) {
            engine.stop();
        }
        TimeUnit.SECONDS.sleep(2); // 等待服务器完全关闭
        log.info("所有测试服务已关闭。");
    }

    @Test
    void testEndToEndFlow() throws Exception {
        // 1. 通过 HTTP 接口发送一个 start_graph 命令
        log.info("--- 测试 HTTP /start_graph 命令 ---");
        String graphId = UUID.randomUUID().toString();
        Map<String, Object> startGraphPayload = new HashMap<>();
        startGraphPayload.put("graph_id", graphId);
        startGraphPayload.put("app_uri", "default_app");
        startGraphPayload.put("properties", Map.of("initial_message", "Hello from HTTP start_graph!"));

        String httpResponse = sendHttpRequest(HTTP_PORT, "/start_graph", "POST", startGraphPayload);
        assertTrue(httpResponse.contains("\"status\":\"accepted\""), "HTTP start_graph 响应应包含 'status:accepted'");
        log.info("HTTP /start_graph 响应: {}", httpResponse);
        TimeUnit.SECONDS.sleep(1);

        // 2. 通过 TCP/MsgPack 接口发送一个 Data 消息给 SimpleEchoExtension
        log.info("--- 测试 TCP/MsgPack Data 消息回显 ---");
        CompletableFuture<Message> echoResponseFuture = new CompletableFuture<>();
        sendTcpDataMessage(TCP_PORT, graphId, "echo_test_data", Map.of("content", "Hello TCP Echo!"),
                echoResponseFuture);

        Message receivedMessage = echoResponseFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "应收到回显消息");
        assertTrue(receivedMessage instanceof Data, "回显消息应为Data类型");
        Data echoData = (Data) receivedMessage;
        assertEquals("echo_test_data", echoData.getName());
        assertEquals("Hello TCP Echo!", echoData.getPayload().get("content"));
        log.info("成功接收到回显Data消息: {}", echoData.getName());
        TimeUnit.SECONDS.sleep(1);

        // 3. 通过 HTTP 接口发送 stop_graph 命令
        log.info("--- 测试 HTTP /stop_graph 命令 ---");
        Map<String, Object> stopGraphPayload = Map.of("graph_id", graphId, "app_uri", "default_app");
        String stopResponse = sendHttpRequest(HTTP_PORT, "/stop_graph", "POST", stopGraphPayload);
        assertTrue(stopResponse.contains("\"status\":\"accepted\""), "HTTP stop_graph 响应应包含 'status:accepted'");
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
            CompletableFuture<Message> responseFuture) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                            ch.pipeline().addLast(new MessageDecoder());
                            ch.pipeline().addLast(new LengthFieldPrepender(4));
                            ch.pipeline().addLast(new MessageEncoder());
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
                                    log.debug("TCP客户端收到消息: type={}, name={}", msg.getType(), msg.getName());
                                    responseFuture.complete(msg);
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

            ChannelFuture f = b.connect("localhost", port).sync();
            Channel channel = f.channel();

            // 构建 Data 消息
            Data testData = Data.builder()
                    .commandId(UUID.randomUUID().toString())
                    .name(messageName)
                    .sourceLocation(Location.builder().appUri("integration_test_tcp_client").graphId(graphId)
                            .extensionName("N/A").build())
                    .destinationLocation(Location.builder().appUri("default_app").graphId(graphId)
                            .extensionName("SimpleEcho").build())
                    .payload(payload)
                    .build();

            log.debug("TCP客户端发送Data消息: {}", testData.getName());
            channel.writeAndFlush(testData).sync();

            // 为了接收回显，客户端需要保持连接一段时间
            // 但这里是测试，收到消息后即可关闭
            // 如果希望持续监听，则需要更复杂的客户端逻辑
            // channel.closeFuture().sync();
        } finally {
            // group.shutdownGracefully();
        }
    }
}