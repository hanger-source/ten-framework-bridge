package com.tenframework.server.handler;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.tenframework.core.message.CommandResult;
import com.fasterxml.jackson.core.type.TypeReference; // 新增导入

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
            JsonNode rootNode = objectMapper.readTree(requestBody); // rootNode 现在代表整个 Command JSON

            String commandId = rootNode.path("command_id").asText(UUID.randomUUID().toString());
            String commandName = rootNode.path("name").asText();

            // 从 rootNode 的 "properties" 字段中提取参数
            JsonNode propertiesNode = rootNode.path("properties");
            Map<String, Object> commandProperties = new HashMap<>();
            if (propertiesNode.isObject()) {
                propertiesNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        commandProperties.put(entry.getKey(), entry.getValue().asText());
                    } else if (entry.getValue().isObject() || entry.getValue().isArray()) { // 处理嵌套对象和数组
                        commandProperties.put(entry.getKey(), entry.getValue().toString()); // 将嵌套对象和数组转为字符串
                    } else {
                        commandProperties.put(entry.getKey(),
                                objectMapper.convertValue(entry.getValue(), Object.class));
                    }
                });
            }

            String graphId = commandProperties.getOrDefault("graph_id", UUID.randomUUID().toString()).toString();
            String appUri = commandProperties.getOrDefault("app_uri", "default_app").toString();

            // 提取 graph_json，并确保它是 String 类型
            String graphJson = null;
            Object tempGraphJson = commandProperties.get("graph_json");
            if (tempGraphJson instanceof String) {
                graphJson = (String) tempGraphJson;
            } else if (tempGraphJson instanceof JsonNode) { // 如果是JsonNode，转为String
                graphJson = tempGraphJson.toString();
            } else if (tempGraphJson != null) { // 如果是其他类型，尝试转为String
                graphJson = tempGraphJson.toString();
            }

            if (graphJson == null || graphJson.isEmpty()) {
                log.warn("start_graph命令中graph_json属性缺失或无效");
                sendErrorResponse(ctx, BAD_REQUEST, "start_graph命令缺少有效的 'graph_json' 属性");
                return;
            }

            // 重新构建传递给 Engine 的 properties，确保其结构符合 Engine 的期望
            Map<String, Object> engineCommandProperties = new HashMap<>();
            engineCommandProperties.put("graph_id", graphId);
            engineCommandProperties.put("app_uri", appUri);
            engineCommandProperties.put("graph_json", graphJson); // 确保这里是String类型

            // 复制其他原始properties，但注意不要覆盖已设置的graph_id, app_uri, graph_json
            commandProperties.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("graph_id") && !entry.getKey().equals("app_uri")
                            && !entry.getKey().equals("graph_json"))
                    .forEach(entry -> engineCommandProperties.put(entry.getKey(), entry.getValue()));

            // 添加CompletableFuture和channelId到properties中，以便Engine回传结果
            CompletableFuture<CommandResult> resultFuture = new CompletableFuture<>();
            engineCommandProperties.put("__result_future__", resultFuture);
            engineCommandProperties.put("__channel_id__", ctx.channel().id().asShortText());

            Command command = Command.builder()
                    .commandId(commandId)
                    .name(commandName)
                    .properties(engineCommandProperties) // 使用新的 engineCommandProperties
                    .sourceLocation(
                            Location.builder().appUri("http_client").graphId("N/A").extensionName("N/A").build())
                    .destinationLocations(Collections.emptyList())
                    .build();

            boolean submitted = engine.submitMessage(command, ctx.channel().id().asShortText());
            if (submitted) {
                // 成功提交后，等待Engine返回CommandResult，并根据结果发送HTTP响应
                try {
                    CommandResult finalResult = resultFuture.get(10, TimeUnit.SECONDS); // 最多等待10秒
                    if (finalResult.isSuccess()) {
                        sendJsonResponse(ctx, OK, Map.of("status", "success", "command_id", finalResult.getCommandId(),
                                "message", finalResult.getResult().getOrDefault("message", "")));
                    } else {
                        sendJsonResponse(ctx, INTERNAL_SERVER_ERROR, Map.of("status", "failed", "command_id",
                                finalResult.getCommandId(), "error", finalResult.getError()));
                    }
                } catch (Exception e) {
                    log.error("等待start_graph命令结果超时或发生异常", e);
                    sendErrorResponse(ctx, GATEWAY_TIMEOUT, "处理命令超时或发生异常: " + e.getMessage());
                }
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

            String commandId = rootNode.path("command_id").asText(UUID.randomUUID().toString()); // 新增
            String commandName = rootNode.path("name").asText("stop_graph"); // 新增

            JsonNode propertiesNode = rootNode.path("properties"); // 新增
            Map<String, Object> commandProperties = new HashMap<>(); // 新增
            if (propertiesNode.isObject()) { // 新增
                propertiesNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        commandProperties.put(entry.getKey(), entry.getValue().asText());
                    } else if (entry.getValue().isObject() || entry.getValue().isArray()) { // 处理嵌套对象和数组
                        commandProperties.put(entry.getKey(), entry.getValue().toString()); // 将嵌套对象和数组转为字符串
                    } else {
                        commandProperties.put(entry.getKey(),
                                objectMapper.convertValue(entry.getValue(), Object.class));
                    }
                });
            }

            String graphId = commandProperties.getOrDefault("graph_id", "").toString(); // 从properties中获取
            String appUri = commandProperties.getOrDefault("app_uri", "default_app").toString(); // 从properties中获取

            if (graphId.isEmpty()) {
                sendErrorResponse(ctx, BAD_REQUEST, "停止图请求缺少 'graph_id'");
                return;
            }

            // 重新构建传递给 Engine 的 properties
            Map<String, Object> engineCommandProperties = new HashMap<>();
            engineCommandProperties.put("graph_id", graphId);
            engineCommandProperties.put("app_uri", appUri);
            // 复制其他原始properties
            commandProperties.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("graph_id") && !entry.getKey().equals("app_uri"))
                    .forEach(entry -> engineCommandProperties.put(entry.getKey(), entry.getValue()));

            // 添加CompletableFuture和channelId到properties中，以便Engine回传结果
            CompletableFuture<CommandResult> resultFuture = new CompletableFuture<>();
            engineCommandProperties.put("__result_future__", resultFuture);
            engineCommandProperties.put("__channel_id__", ctx.channel().id().asShortText());

            // 构建 stop_graph 命令
            Command command = Command.builder()
                    .commandId(commandId)
                    .name(commandName)
                    .properties(engineCommandProperties) // 使用新的 engineCommandProperties
                    .sourceLocation(
                            Location.builder().appUri("http_client").graphId("N/A").extensionName("N/A").build())
                    .destinationLocations(Collections.emptyList())
                    .build();

            boolean submitted = engine.submitMessage(command, ctx.channel().id().asShortText());
            if (submitted) {
                // 成功提交后，等待Engine返回CommandResult，并根据结果发送HTTP响应
                try {
                    CommandResult finalResult = resultFuture.get(10, TimeUnit.SECONDS); // 最多等待10秒
                    if (finalResult.isSuccess()) {
                        sendJsonResponse(ctx, OK, Map.of("status", "success", "command_id", finalResult.getCommandId(),
                                "message", finalResult.getResult().getOrDefault("message", "")));
                    } else {
                        sendJsonResponse(ctx, INTERNAL_SERVER_ERROR, Map.of("status", "failed", "command_id",
                                finalResult.getCommandId(), "error", finalResult.getError()));
                    }
                } catch (Exception e) {
                    log.error("等待stop_graph命令结果超时或发生异常", e);
                    sendErrorResponse(ctx, GATEWAY_TIMEOUT, "处理命令超时或发生异常: " + e.getMessage());
                }
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