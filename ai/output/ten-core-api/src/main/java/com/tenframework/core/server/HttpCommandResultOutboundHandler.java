package com.tenframework.core.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.CommandResult;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * 将Engine返回的CommandResult编码为HTTP响应的ChannelHandler
 */
@Slf4j
public class HttpCommandResultOutboundHandler extends MessageToMessageEncoder<CommandResult> {

    private final ObjectMapper objectMapper;

    public HttpCommandResultOutboundHandler() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, CommandResult msg, List<Object> out) throws Exception {
        try {
            // 将CommandResult转换为Map，便于JSON序列化
            Map<String, Object> responseMap = Map.of(
                    "status", msg.isSuccess() ? "OK" : "ERROR",
                    "command_id", msg.getCommandId() != null ? msg.getCommandId() : "N/A",
                    "result", msg.getResult() != null ? msg.getResult() : Map.of(),
                    "is_final", msg.isFinal(),
                    "message", msg.getError() != null ? msg.getError() : (msg.isSuccess() ? "Success" : "Failure")
            );

            String json = objectMapper.writeValueAsString(responseMap);
            HttpResponseStatus httpStatus = msg.isSuccess() ? OK : INTERNAL_SERVER_ERROR;

            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpStatus,
                    Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            out.add(response);
            log.debug("CommandResult编码为HTTP响应: commandId={}, status={}", msg.getCommandId(), httpStatus);

        } catch (Exception e) {
            log.error("编码CommandResult为HTTP响应失败: commandId={}", msg.getCommandId(), e);
            // 抛出异常，让Netty的exceptionCaught处理
            throw e;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HttpCommandResultOutboundHandler处理时发生异常", cause);
        ctx.close();
    }
}