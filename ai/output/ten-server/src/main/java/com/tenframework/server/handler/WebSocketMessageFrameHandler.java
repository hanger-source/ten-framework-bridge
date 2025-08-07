package com.tenframework.server.handler;

import com.tenframework.core.message.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.system.ClientConnectionExtension;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;

@Slf4j
public class WebSocketMessageFrameHandler extends SimpleChannelInboundHandler<Message> {

    private final Engine engine;

    public WebSocketMessageFrameHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        String channelId = ctx.channel().id().asShortText();
        String clientLocationUri = msg.getProperty(PROPERTY_CLIENT_LOCATION_URI, String.class); // 获取客户端Location URI
        String clientGraphId = msg.getProperty(PROPERTY_CLIENT_GRAPH_ID, String.class);

        // 客户端入站消息需要将Channel ID和ClientLocation
        // URI作为属性带上，以便ClientConnectionExtension能获取并建立映射
        if (clientLocationUri != null && !clientLocationUri.isEmpty() && channelId != null && !channelId.isEmpty()) {
            if (clientGraphId != null && !clientGraphId.isEmpty()) {
                msg.setProperty(PROPERTY_CLIENT_LOCATION_URI, clientLocationUri); // 确保属性存在
                msg.setProperty(MessageConstants.PROPERTY_CLIENT_CHANNEL_ID, channelId);
                // client过来的数据 自动根据 clientGraphId 进行路由 默认交给 ClientConnectionExtension
                msg.setDestinationLocation(new Location(PROPERTY_CLIENT_APP_URI, clientGraphId,
                        ClientConnectionExtension.NAME));
            } else {
                log.warn(
                        "WebSocketMessageFrameHandler: 入站消息缺少PROPERTY_CLIENT_GRAPH_ID: messageType={}, channelId={}, clientLocationUri={}",
                        msg.getType(), channelId, clientLocationUri);
            }
        } else {
            log.warn(
                    "WebSocketMessageFrameHandler: 入站消息缺少PROPERTY_CLIENT_LOCATION_URI或Channel ID，可能无法回传: messageType={}, channelId={}, clientLocationUri={}",
                    msg.getType(), channelId, clientLocationUri);
        }

        // 直接将消息提交给Engine，由Engine进行路由
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