package com.tenframework.agent.example;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.server.handler.WebSocketFrameToByteBufDecoder;
import com.tenframework.server.message.WebSocketMessageDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketTestClient {

    private final URI uri;
    private final EventLoopGroup group;
    private final Map<Long, CompletableFuture<Message>> commandResponseFutures; // 用于CommandResult
    private final CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    private final Engine engine; // Needs engine to initialize WebSocketMessageFrameHandler
    private Channel channel;
    private CompletableFuture<Message> dataResponseFuture; // 用于Data消息的回显

    public WebSocketTestClient(URI uri, EventLoopGroup group, Engine engine) {
        this.uri = uri;
        this.group = group;
        this.engine = engine;
        commandResponseFutures = new ConcurrentHashMap<>();
        dataResponseFuture = new CompletableFuture<>(); // 初始化为新的CompletableFuture
    }

    public void setDataResponseFuture(CompletableFuture<Message> future) {
        dataResponseFuture = future;
    }

    public void registerCommandResponseFuture(long commandId, CompletableFuture<Message> future) {
        commandResponseFutures.put(commandId, future);
    }

    public CompletableFuture<Void> connect() {
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(8192));

                    // WebSocket handshake handling
                    WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

                    // Add the WebSocketClientHandler first to handle handshake and then pass frames
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<>() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            handshaker.handshake(ctx.channel());
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                            Channel ch = ctx.channel();
                            if (!handshaker.isHandshakeComplete()) {
                                // WebSocket handshake response
                                handshaker.finishHandshake(ch, (FullHttpResponse)msg);
                                handshakeFuture.complete(null);
                                log.info("WebSocket Client connected!");
                                // Once handshake is complete, add the TEN Framework message processing chain
                                ch.pipeline().addLast(new WebSocketFrameToByteBufDecoder()); // WebSocket frame ->
                                // ByteBuf
                                ch.pipeline().addLast(new WebSocketMessageDecoder());
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                                        throws Exception {
                                        if (msg instanceof Message decodedMsg) {
                                            // This path should ideally not be taken by this handler if
                                            // MessageDecoder is
                                            // after it
                                            // But as a fallback or if pipeline order changes, handle it here.
                                            if (decodedMsg instanceof CommandResult commandResult) {
                                                CompletableFuture<Message> future = commandResponseFutures
                                                    .remove(commandResult.getCommandId());
                                                if (future != null) {
                                                    future.complete(commandResult);
                                                } else {
                                                    log.warn("未找到CommandResult对应的CompletableFuture: commandId={}",
                                                        commandResult.getCommandId());
                                                }
                                            } else if (decodedMsg instanceof Data data) {
                                                if (!dataResponseFuture.isDone()) {
                                                    dataResponseFuture.complete(data);
                                                } else {
                                                    log.warn("dataResponseFuture已完成，但收到新的Data消息: {}", data);
                                                }
                                            } else {
                                                log.warn("WebSocketClientHandler收到未知类型的Message: {}",
                                                    decodedMsg.getClass().getName());
                                            }
                                        } else {
                                            log.warn("WebSocketClientHandler received unknown message type: {}",
                                                msg.getClass().getName());
                                        }
                                    }
                                });
                                return;
                            }

                            if (msg instanceof FullHttpResponse response) {
                                throw new IllegalStateException(
                                    "Unexpected FullHttpResponse (getStatus=" + response.status() + ", content="
                                        + response.content().toString(CharsetUtil.UTF_8) + ')');
                            }
                            if (msg instanceof BinaryWebSocketFrame frame) {
                                // Pass BinaryWebSocketFrame to the next handler for decoding into Message
                                ctx.fireChannelRead(frame.retain());
                            } else if (msg instanceof CloseWebSocketFrame closeWebSocketFrame) {
                                log.info(
                                    "WebSocketClientHandler received CloseWebSocketFrame, closing connection: "
                                        + "code={}, reason={}",
                                    closeWebSocketFrame.statusCode(), closeWebSocketFrame.reasonText());
                                ch.close();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            log.error("WebSocketClientHandler exceptionCaught", cause);
                            handshakeFuture.completeExceptionally(cause);
                            // 对于所有未完成的commandResponseFutures和dataResponseFuture，都将其标记为异常
                            commandResponseFutures.values().forEach(future -> future.completeExceptionally(cause));
                            if (!dataResponseFuture.isDone()) {
                                dataResponseFuture.completeExceptionally(cause);
                            }
                            ctx.close();
                        }
                    });
                }
            });

        try {
            ChannelFuture f = b.connect(uri.getHost(), uri.getPort()).sync();
            channel = f.channel();
            return handshakeFuture;
        } catch (Exception e) {
            handshakeFuture.completeExceptionally(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public ChannelFuture sendMessage(BinaryWebSocketFrame frame) throws Exception {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("WebSocket channel is not active.");
        }
        return channel.writeAndFlush(frame);
    }

    public CompletableFuture<Void> disconnect() {
        if (channel != null && channel.isActive()) {
            return CompletableFuture.runAsync(() -> {
                try {
                    channel.writeAndFlush(new CloseWebSocketFrame()).sync();
                    channel.closeFuture().sync();
                } catch (Exception e) {
                    log.error("Failed to close WebSocket channel gracefully", e);
                }
            }, GlobalEventExecutor.INSTANCE); // Use Netty's GlobalEventExecutor for async ops
        }
        return CompletableFuture.completedFuture(null);
    }
}
