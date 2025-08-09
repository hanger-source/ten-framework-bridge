package com.tenframework.server.remote;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.server.connection.Connection;
import com.tenframework.server.message.WebSocketMessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty ChannelHandler，用于处理 Remote 建立的客户端 WebSocket 连接。
 * 负责管理 Connection 实例，并将解码后的消息传递给本地 Engine。
 */
@Slf4j
public class RemoteConnectionHandler extends SimpleChannelInboundHandler<Object> {

    private final Remote remote; // 关联的 Remote 实例
    private final Engine localEngine; // 本地 Engine 实例的引用
    private WebSocketClientHandshaker handshaker; // WebSocket 握手器
    private Connection connection; // 当前连接的Connection实例

    public RemoteConnectionHandler(Remote remote, Engine localEngine) {
        this.remote = remote;
        this.localEngine = localEngine;
    }

    public void setHandshaker(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("RemoteConnectionHandler: Channel {} 添加到管道", ctx.channel().id());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel活跃时，执行WebSocket握手
        if (handshaker != null) {
            handshaker.handshake(ctx.channel());
            log.info("RemoteConnectionHandler: Channel {} 活跃，开始WebSocket握手", ctx.channel().id());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当Channel不活跃时，通知Remote关闭Connection
        if (connection != null) {
            remote.onConnectionClosed(connection);
            log.info("RemoteConnectionHandler: Channel {} 不活跃，Connection {} 已关闭", ctx.channel().id(),
                    connection.getConnectionId());
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 首先处理WebSocket握手响应
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ctx.channel(), response);
                    log.info("RemoteConnectionHandler: WebSocket握手完成. Status: {}", response.status());
                    // 握手成功后，创建Connection实例并添加到Remote的活跃连接列表
                    this.connection = new Connection(ctx.channel());
                    this.connection.bindToEngine(localEngine);
                    remote.getActiveConnections().add(this.connection);
                    log.info("RemoteConnectionHandler: Connection {} 新建并添加到Remote活跃连接列表", connection.getConnectionId());
                } catch (Exception e) {
                    log.error("RemoteConnectionHandler: WebSocket握手失败", e);
                    ctx.close();
                }
                return;
            }
        }

        // 处理WebSocket帧（通常是TextWebSocketFrame或BinaryWebSocketFrame，它们会经过解码器）
        if (msg instanceof WebSocketFrame) {
            // 将WebSocketFrame的内容传递给下一个处理器，例如MessageDecoder
            // 注意：这里传递的是ByteBuf内容，如果MessageDecoder期望的是WebSocketFrame，需要调整
            // 我们的MessageDecoder现在直接处理ByteBuf
            ctx.fireChannelRead(((WebSocketFrame) msg).content().retain()); // retain 确保 ByteBuf 在链中传递
            return;
        }

        // MessageDecoder已经将ByteBuf解码为Message对象
        if (msg instanceof Message) {
            if (connection != null) {
                // 将消息提交给本地Engine处理
                localEngine.submitMessage((Message) msg);
                log.debug("RemoteConnectionHandler: 收到Message并提交给Engine: type={}, id={}", ((Message) msg).getType(),
                        ((Message) msg).getId());
            } else {
                log.warn("RemoteConnectionHandler: 收到Message但Connection未初始化或握手未完成，消息将被丢弃: type={}, id={}",
                        ((Message) msg).getType(), ((Message) msg).getId());
            }
        } else {
            // 处理其他未知类型的消息
            log.warn("RemoteConnectionHandler: 收到未知消息类型: {}", msg.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RemoteConnectionHandler: Channel {} 发生异常", ctx.channel().id(), cause);
        ctx.close();
    }
}