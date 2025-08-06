package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import com.tenframework.core.message.MessageConstants;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import io.netty.channel.ChannelId;
import io.netty.buffer.ByteBuf;

@Slf4j
public class WebSocketMessageFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final Engine engine;
    // 维护 ChannelId 到 Channel 的映射，以便在Engine处理完消息后回传
    private static final Map<String, Channel> activeChannels = new ConcurrentHashMap<>();

    public WebSocketMessageFrameHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本帧，例如 JSON RPC 命令
            String requestText = ((TextWebSocketFrame) frame).text();
            log.warn("Received TextWebSocketFrame: {}", requestText);
            // TODO: 这里可以根据需要解析 JSON RPC 并转换为 TEN Command
            // 目前，我们主要关注 BinaryWebSocketFrame
        } else if (frame instanceof BinaryWebSocketFrame) {
            // 处理二进制帧，预计是 MsgPack 封装的 TEN 消息
            // 将 Channel ID 作为临时属性添加到 ByteBuf 中，以便 MessageDecoder 或 Engine
            // 可以根据此ID将响应消息回传给正确的客户端通道。
            // 注意：这里将ByteBuf作为消息发送，但我们将其视为一个内部Message的包装，
            // MessageDecoder将负责从ByteBuf中解码出真实的Message。
            ByteBuf processedContent = ((BinaryWebSocketFrame) frame).content();
            Message message = (Message) processedContent; // 假设这里MessageDecoder已经处理
            message.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, ctx.channel().id().asShortText()); // 使用常量
        } else {
            String message = "Unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // WebSocketServerProtocolHandler 会在握手成功时触发 HandshakeComplete
        // 这里可以进行一些连接建立后的初始化操作
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        activeChannels.put(channelId, ctx.channel()); // 注册 Channel
        engine.addChannel(ctx.channel()); // 将Channel添加到Engine的Channel管理中
        log.info("WebSocket client connected: {}, channelId={}", ctx.channel().remoteAddress(), channelId);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        activeChannels.remove(channelId); // 移除 Channel
        engine.removeChannel(channelId); // 从Engine的Channel管理中移除
        log.info("WebSocket client disconnected: {}, channelId={}", ctx.channel().remoteAddress(), channelId);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageFrameHandler exceptionCaught: {}", cause.getMessage(), cause);
        ctx.close();
    }

    // 重写 channelRead 方法，使其能够处理 Message 对象
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Message) {
            Message message = (Message) msg;
            // 获取当前 Channel 的 ID，并作为临时属性添加到消息中
            String channelId = ctx.channel().id().asShortText();
            message.setProperty("__client_channel_id__", channelId); // 将 channelId 作为消息的临时属性

            boolean submitted = engine.submitMessage(message);
            if (!submitted) {
                // TODO: 处理回压，例如队列满时的策略
                if (message.getType() == MessageType.COMMAND) {
                    log.error("Engine queue full, failed to submit Command: {}", message);
                    // 可以考虑返回一个 CommandResult 错误给客户端
                } else {
                    log.warn("Engine queue full, discarding Data/Audio/Video Frame: {}", message.getType());
                }
            }
        } else {
            // 如果不是 Message 对象，继续传递给下一个处理器
            super.channelRead(ctx, msg);
        }
    }
}