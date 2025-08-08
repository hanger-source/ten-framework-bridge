package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.util.ClientLocationUriUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;
import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;
import static com.tenframework.core.message.MessageConstants.SYS_EXTENSION_NAME;

/**
 *
 */
@Slf4j
public class WebSocketMessageDispatcher extends SimpleChannelInboundHandler<Message> {

    private final Engine engine;

    public WebSocketMessageDispatcher(Engine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        String channelId = ctx.channel().id().asShortText();
        msg.setProperty(PROPERTY_CLIENT_CHANNEL_ID, channelId);

        if (MessageType.COMMAND != msg.getType()) {
            // 非command 从外部进来的消息 例如Data、AudioFrame、VideoFrame
            // 必须携带 客户端Location URI
            String clientLocationUri = msg.getProperty(PROPERTY_CLIENT_LOCATION_URI, String.class); // 获取客户端Location URI
            String clientChannelId = ClientLocationUriUtils.getChannelId(clientLocationUri);
            String clientGraphId = ClientLocationUriUtils.getGraphId(clientLocationUri);
            String clientAppUri = ClientLocationUriUtils.getAppUri(clientLocationUri);

            // 客户端Location URI
            if (clientChannelId != null && !clientChannelId.equals(channelId)) {
                log.warn("WebSocketMessageFrameHandler: 忽略来自{}的消息，因为其ChannelId与当前ChannelId不一致: messageName={}, channelId={}, clientLocationUri={}",
                    clientLocationUri, msg.getName(), channelId, clientLocationUri);
            }
            msg.setSourceLocation(new Location(clientAppUri, clientGraphId, SYS_EXTENSION_NAME));
            log.debug(
                "WebSocketMessageFrameHandler: 收到来自{}消息，不设置默认路由，由Engine处理: messageName={}",
                clientLocationUri, msg.getName());
        }

        // 由Engine进行路由
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
        engine.addChannel(ctx.channel()); // 将Channel添加到Engine的Channel管理中
        log.info("WebSocket client connected: {}, channelId={}", ctx.channel().remoteAddress(), channelId);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
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