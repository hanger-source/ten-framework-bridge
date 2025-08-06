package com.tenframework.server.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;

@Slf4j
public class WebSocketMessageFrameHandler extends SimpleChannelInboundHandler<Message> {

    // 维护 ChannelId 到 Channel 的映射，以便在Engine处理完消息后回传
    private static final Map<String, Channel> activeChannels = new ConcurrentHashMap<>();
    private final Engine engine;

    public WebSocketMessageFrameHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        // 获取当前 Channel 的 ID，并作为临时属性添加到消息中
        String channelId = ctx.channel().id().asShortText();
        msg.setProperty(PROPERTY_CLIENT_CHANNEL_ID, channelId); // 使用常量

        boolean submitted = engine.submitMessage(msg);
        if (!submitted) {
            // TODO: 处理回压，例如队列满时的策略
            if (msg.getType() == MessageType.COMMAND) {
                log.error("Engine queue full, failed to submit Command: {}", msg);
                // 可以考虑返回一个 CommandResult 错误给客户端
            } else {
                log.warn("Engine queue full, discarding Data/Audio/Video Frame: {}", msg.getType());
            }
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
}