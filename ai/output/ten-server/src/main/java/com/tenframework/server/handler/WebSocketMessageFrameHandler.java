package com.tenframework.server.handler;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.extension.ClientConnectionExtension;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tenframework.core.message.MessageConstants.PROPERTY_CLIENT_LOCATION_URI;

@Slf4j
public class WebSocketMessageFrameHandler extends SimpleChannelInboundHandler<Message> {

    private final Engine engine;
    private final ClientConnectionExtension clientConnectionExtension;

    public WebSocketMessageFrameHandler(Engine engine) {
        this.engine = engine;
        this.clientConnectionExtension = engine.getClientConnectionExtension(); // 获取ClientConnectionExtension实例
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        // 获取当前 Channel 的 ID，并作为临时属性添加到消息中
        String channelId = ctx.channel().id().asShortText();
        String clientLocationUri = msg.getProperty(PROPERTY_CLIENT_LOCATION_URI, String.class); // 获取客户端Location URI

        // 交给ClientConnectionExtension处理入站消息，它会负责注册映射并添加必要属性
        Message processedMsg = clientConnectionExtension.handleInboundClientMessage(msg, channelId, clientLocationUri);

        boolean submitted = engine.submitMessage(processedMsg);
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
        clientConnectionExtension.removeClientChannelMapping(channelId); // 通知ClientConnectionExtension移除映射
        log.info("WebSocket client disconnected: {}, channelId={}", ctx.channel().remoteAddress(), channelId);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageFrameHandler exceptionCaught: {}", cause.getMessage(), cause);
        ctx.close();
    }
}