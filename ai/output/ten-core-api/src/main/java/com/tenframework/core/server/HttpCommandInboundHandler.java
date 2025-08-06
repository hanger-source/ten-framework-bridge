package com.tenframework.core.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.Location;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.buffer.Unpooled;

@Slf4j
public class HttpCommandInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Engine engine;
    private final ObjectMapper objectMapper;

    public HttpCommandInboundHandler(Engine engine) {
        this.engine = engine;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        if (msg.method() == HttpMethod.POST) {
            String uri = msg.uri();
            log.debug("收到HTTP POST请求: URI={}", uri);

            if ("/start_graph".equals(uri)) {
                handleStartGraph(ctx, msg);
            } else if ("/stop_graph".equals(uri)) {
                handleStopGraph(ctx, msg);
            } else if ("/ping".equals(uri)) {
                handlePing(ctx);
            } else {
                sendErrorResponse(ctx, NOT_FOUND, "未知的URI: " + uri);
            }
        } else {
            sendErrorResponse(ctx, METHOD_NOT_ALLOWED, "只支持POST方法");
        }
    }

    private void handleStartGraph(ChannelHandlerContext ctx, FullHttpRequest msg) {
        try {
            String requestBody = msg.content().toString(CharsetUtil.UTF_8);
            JsonNode rootNode = objectMapper.readTree(requestBody);

            String graphId = rootNode.path("graph_id").asText(UUID.randomUUID().toString());
            String appUri = rootNode.path("app_uri").asText("default_app");
            String extensionName = rootNode.path("extension_name").asText(); // 通常在 start_graph 命令中不会直接指定 extension_name

            // 提取 properties，用于传递给 Engine 和 Extension
            Map<String, Object> properties = objectMapper.convertValue(rootNode.path("properties"), Map.class);
            if (properties == null) {
                properties = new HashMap<>();
            }
            // 确保properties中包含graph_id和app_uri，虽然Location中也有
            properties.put("graph_id", graphId);
            properties.put("app_uri", appUri);

            // 构建 Command 对象
            Command command = Command.builder()
                    .commandId(UUID.randomUUID().toString())
                    .properties(properties)
                    .build();
            command.setName("start_graph"); // 在构建后设置name
            command.setSourceLocation(
                    Location.builder().appUri("http_client").graphId("N/A").extensionName("N/A").build());
            command.setDestinationLocations(
                    Collections.singletonList(
                            Location.builder().appUri(appUri).graphId(graphId).extensionName("engine").build())); // 目的地指向Engine

            // 提交命令到 Engine
            boolean submitted = engine.submitMessage(command, ctx.channel().id().asShortText()); // 关联 channelId
            if (submitted) {
                // 成功提交后，等待Engine返回CommandResult，并在CommandResultHandler中处理响应
                // TODO: 需要一个机制来映射 commandId 到
                // ChannelHandlerContext，以便在CommandResult返回时找到正确的Channel进行响应
                // 暂时发送一个同步的接受响应
                sendJsonResponse(ctx, OK, Map.of("status", "accepted", "command_id", command.getCommandId()));
            } else {
                sendErrorResponse(ctx, SERVICE_UNAVAILABLE, "Engine队列已满，无法处理请求");
            }

        } catch (Exception e) {
            log.error("处理 /start_graph 请求时发生异常", e);
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "处理请求时发生内部错误: " + e.getMessage());
        }
    }

    private void handleStopGraph(ChannelHandlerContext ctx, FullHttpRequest msg) {
        try {
            String requestBody = msg.content().toString(CharsetUtil.UTF_8);
            JsonNode rootNode = objectMapper.readTree(requestBody);

            String graphId = rootNode.path("graph_id").asText();
            String appUri = rootNode.path("app_uri").asText("default_app");

            if (graphId.isEmpty()) {
                sendErrorResponse(ctx, BAD_REQUEST, "停止图请求缺少 'graph_id'");
                return;
            }

            // 构建 stop_graph 命令
            Command command = Command.builder()
                    .commandId(UUID.randomUUID().toString())
                    .properties(Map.of("graph_id", graphId, "app_uri", appUri))
                    .build();
            command.setName("stop_graph"); // 在构建后设置name
            command.setSourceLocation(
                    Location.builder().appUri("http_client").graphId("N/A").extensionName("N/A").build());
            command.setDestinationLocations(
                    Collections.singletonList(
                            Location.builder().appUri(appUri).graphId(graphId).extensionName("engine").build()));

            boolean submitted = engine.submitMessage(command, ctx.channel().id().asShortText());
            if (submitted) {
                sendJsonResponse(ctx, OK, Map.of("status", "accepted", "command_id", command.getCommandId()));
            } else {
                sendErrorResponse(ctx, SERVICE_UNAVAILABLE, "Engine队列已满，无法处理请求");
            }

        } catch (Exception e) {
            log.error("处理 /stop_graph 请求时发生异常", e);
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "处理请求时发生内部错误: " + e.getMessage());
        }
    }

    private void handlePing(ChannelHandlerContext ctx) {
        sendJsonResponse(ctx, OK,
                Map.of("status", "pong", "engine_id", engine.getEngineId(), "engine_state", engine.getState().name()));
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
            Map<String, Object> jsonResponse) {
        try {
            String json = objectMapper.writeValueAsString(jsonResponse);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("发送JSON响应时发生异常", e);
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "发送响应时发生内部错误");
        }
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HttpCommandInboundHandler处理时发生异常", cause);
        sendErrorResponse(ctx, INTERNAL_SERVER_ERROR, "服务器内部错误: " + cause.getMessage());
        ctx.close();
    }
}