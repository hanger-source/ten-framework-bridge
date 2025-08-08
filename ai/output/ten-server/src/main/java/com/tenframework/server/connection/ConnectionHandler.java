package com.tenframework.server.connection;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.server.app.App;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty ChannelHandler，用于处理WebSocket连接和消息分发。
 * 负责创建和管理 Connection 实例，并将解码后的消息传递给 App 和 Engine。
 */
@Slf4j
public class ConnectionHandler extends SimpleChannelInboundHandler<Object> {

    private final App app; // App 实例的引用
    private final Engine engine; // Engine 实例的引用
    private Connection connection; // 当前连接的Connection实例

    public ConnectionHandler(App app, Engine engine) {
        this.app = app;
        this.engine = engine;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel激活时，创建新的Connection实例并注册到App
        this.connection = app.onNewConnection(ctx.channel());
        // 将Connection绑定到Engine，以便Engine可以将消息路由回此连接
        this.connection.bindToEngine(engine);
        log.info("ConnectionHandler: Channel {} 活跃，新建 Connection {}", ctx.channel().id(), connection.getConnectionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel不活跃时，从App中移除Connection
        if (connection != null) {
            app.onConnectionClosed(connection.getConnectionId());
            log.info("ConnectionHandler: Channel {} 不活跃，Connection {} 已关闭", ctx.channel().id(),
                    connection.getConnectionId());
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 处理WebSocket帧
        if (msg instanceof WebSocketFrame) {
            if (msg instanceof TextWebSocketFrame) {
                // 通常WebSocket的文本帧会经过MessageDecoder处理，这里主要处理其他可能的TextWebSocketFrame
                log.debug("ConnectionHandler: 收到TextWebSocketFrame: {}", ((TextWebSocketFrame) msg).text());
            } else if (msg instanceof BinaryWebSocketFrame) {
                // 二进制帧应该会被MessageDecoder处理为Message对象，这里通常不会直接处理原始二进制帧
                log.debug("ConnectionHandler: 收到BinaryWebSocketFrame");
            }
            // 将WebSocketFrame传递给下一个处理器，例如MessageDecoder
            ctx.fireChannelRead(((WebSocketFrame) msg).content()); // 将ByteBuf内容传递下去
        } else if (msg instanceof Message) {
            // MessageDecoder已经将ByteBuf解码为Message对象
            if (connection != null) {
                // 将消息传递给Connection处理，最终提交给Engine
                connection.onMessageReceived((Message) msg);
            } else {
                log.warn("ConnectionHandler: 收到Message但Connection未初始化，消息将被丢弃: type={}, id={}",
                        ((Message) msg).getType(), ((Message) msg).getId());
            }
        } else {
            // 处理其他未知类型的消息
            log.warn("ConnectionHandler: 收到未知消息类型: {}", msg.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("ConnectionHandler: Channel {} 发生异常", ctx.channel().id(), cause);
        ctx.close();
    }
}