package com.tenframework.server.handler;

import com.tenframework.core.app.App;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.server.connection.NettyConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_CHANNEL_ID;

/**
 * WebSocketMessageDispatcher 负责从 WebSocket Channel 读取 Message，并将其分发到 App。
 * 它处理消息的初步校验和路由，并将 Channel ID 作为消息属性传递。
 */
@Slf4j
public class WebSocketMessageDispatcher extends SimpleChannelInboundHandler<Message> {

    private final App app;

    public WebSocketMessageDispatcher(App app) {
        this.app = app;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        String channelId = ctx.channel().id().asShortText();
        msg.setProperty(PROPERTY_CLIENT_CHANNEL_ID, channelId); // 将 Channel ID 设置为消息属性

        NettyConnection connection = ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).get();
        if (connection == null) {
            log.error("WebSocketMessageDispatcher: Channel {} 没有关联的 NettyConnection。", channelId);
            return;
        }

        // 设置消息的 sourceLocation 为 Connection 的 remoteLocation
        msg.setSourceLocation(connection.getRemoteLocation());

        // 将消息分发给 App 处理
        app.handleInboundMessage(msg, connection);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // WebSocketServerProtocolHandler 会在握手成功时触发 HandshakeComplete
        // 这里可以进行一些连接建立后的初始化操作
        super.userEventTriggered(ctx, evt);
    }

    // 移除 channelActive 和 channelInactive，因为这些生命周期事件现在由 NettyConnectionHandler 处理
    /*
     * @Override
     * public void channelActive(ChannelHandlerContext ctx) {
     * // ...
     * }
     *
     * @Override
     * public void channelInactive(ChannelHandlerContext ctx) {
     * // ...
     * }
     */

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageDispatcher: Channel {} 发生异常: {}", ctx.channel().id().asShortText(),
                cause.getMessage(), cause);
        ctx.close(); // 关闭 Channel
    }
}