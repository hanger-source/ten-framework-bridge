package com.tenframework.agent.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import com.tenframework.server.message.MessageDecoder;
import com.tenframework.server.message.MessageEncoder;
import com.tenframework.server.message.TenMessagePackMapperProvider;
import com.tenframework.server.handler.WebSocketFrameToByteBufDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 一个简单的WebSocket客户端，用于与TEN Framework的App进行交互。
 * 支持发送和接收TEN Framework的Message对象。
 */
@Slf4j
public class SimpleWebSocketClient {

    private final URI uri;
    private final EventLoopGroup group;
    private Channel channel;
    private WebSocketClientHandshaker handshaker;
    private final CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Message>> responseFutures = new ConcurrentHashMap<>();
    private Consumer<Message> messageHandler; // 用于处理非CommandResult和非Data的通用消息

    public SimpleWebSocketClient(URI uri) {
        this.uri = uri;
        this.group = new NioEventLoopGroup();
    }

    /**
     * 设置一个通用的消息处理器，用于接收所有非CommandResult和非Data的消息。
     * @param handler 消息处理器
     */
    public void setMessageHandler(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<Void> connect() {
        handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192),
                                new WebSocketClientHandler(handshaker, handshakeFuture, responseFutures, messageHandler));
                        // 在握手完成后动态添加MessageDecoder和MessageEncoder
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

    /**
     * 发送TEN Framework的Message对象。
     * @param message 要发送的消息
     * @return ChannelFuture，表示发送操作的异步结果
     * @throws IllegalStateException 如果WebSocket通道未激活
     */
    public ChannelFuture sendMessage(Message message) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("WebSocket channel is not active.");
        }
        // MessageEncoder会自动将Message对象编码为BinaryWebSocketFrame
        return channel.writeAndFlush(message);
    }

    /**
     * 发送一个Command消息，并返回一个CompletableFuture用于获取CommandResult。
     * @param command 要发送的命令消息
     * @return 包含CommandResult的CompletableFuture
     * @throws IllegalStateException 如果WebSocket通道未激活
     */
    public CompletableFuture<Message> sendCommandWithResponse(Message command) {
        if (command.getId() == null) {
            // 为命令生成一个ID，以便追踪响应
            command.setId(java.util.UUID.randomUUID().toString());
        }
        CompletableFuture<Message> future = new CompletableFuture<>();
        responseFutures.put(Long.parseLong(command.getId()), future); // 假设id可以转为Long
        sendMessage(command); // 发送命令
        return future;
    }

    public CompletableFuture<Void> disconnect() {
        if (channel != null && channel.isActive()) {
            return channel.writeAndFlush(new CloseWebSocketFrame())
                    .syncUninterruptibly()
                    .channel().closeFuture().toCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    public void shutdown() {
        group.shutdownGracefully();
        log.info("SimpleWebSocketClient EventLoopGroup已关闭。");
    }

    /**
     * 内部Netty ChannelHandler，用于处理WebSocket握手和消息帧。
     * 在握手完成后，动态添加TEN Framework的消息编解码器。
     */
    private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private final CompletableFuture<Void> handshakeFuture;
        private final ConcurrentHashMap<Long, CompletableFuture<Message>> responseFutures;
        private final Consumer<Message> messageHandler;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker,
                                      CompletableFuture<Void> handshakeFuture,
                                      ConcurrentHashMap<Long, CompletableFuture<Message>> responseFutures,
                                      Consumer<Message> messageHandler) {
            this.handshaker = handshaker;
            this.handshakeFuture = handshakeFuture;
            this.responseFutures = responseFutures;
            this.messageHandler = messageHandler;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                // WebSocket 握手响应
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                handshakeFuture.complete(null);
                log.info("SimpleWebSocketClient: WebSocket 客户端连接成功！");

                // 握手完成后，动态添加TEN Framework的消息处理链
                ch.pipeline().addLast(new WebSocketFrameToByteBufDecoder()); // WebSocket frame -> ByteBuf
                ch.pipeline().addLast(new MessageDecoder()); // ByteBuf -> Message
                ch.pipeline().addLast(new MessageEncoder()); // Message -> ByteBuf
                // Add a new handler to process decoded Message objects
                ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
                        handleDecodedMessage(msg);
                    }
                });
                return;
            }

            if (msg instanceof FullHttpResponse response) {
                throw new IllegalStateException(
                        "收到意外的 FullHttpResponse (getStatus=" + response.status() + ", content="
                                + response.content().toString(CharsetUtil.UTF_8) + ')');
            }

            // WebSocket帧将由WebSocketFrameToByteBufDecoder和MessageDecoder处理
            // 这里只处理CloseWebSocketFrame
            if (msg instanceof CloseWebSocketFrame closeFrame) {
                log.info(
                        "SimpleWebSocketClient: 收到 CloseWebSocketFrame，关闭连接: code={}, reason={}",
                        closeFrame.statusCode(), closeFrame.reasonText());
                ch.close();
            } else if (msg instanceof BinaryWebSocketFrame binaryFrame) {
                // 将BinaryWebSocketFrame传递给下一个处理器 (WebSocketFrameToByteBufDecoder)
                ctx.fireChannelRead(binaryFrame.retain());
            } else if (msg instanceof TextWebSocketFrame textFrame) {
                log.warn("SimpleWebSocketClient: 收到未处理的 TextWebSocketFrame: {}", textFrame.text());
                textFrame.release();
            }
        }

        private void handleDecodedMessage(Message decodedMsg) {
            if (decodedMsg instanceof CommandResult commandResult) {
                CompletableFuture<Message> future = responseFutures.remove(Long.parseLong(commandResult.getCommandId()));
                if (future != null) {
                    future.complete(commandResult);
                } else {
                    log.warn("SimpleWebSocketClient: 未找到CommandResult对应的CompletableFuture: commandId={}",
                            commandResult.getCommandId());
                }
            } else if (messageHandler != null) {
                // 将非CommandResult的消息传递给外部处理器
                messageHandler.accept(decodedMsg);
            } else {
                log.warn("SimpleWebSocketClient: 收到未处理的Message: type={}, id={}",
                        decodedMsg.getType(), decodedMsg.getId());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("SimpleWebSocketClient: 客户端发生异常", cause);
            handshakeFuture.completeExceptionally(cause);
            responseFutures.values().forEach(future -> future.completeExceptionally(cause));
            if (messageHandler != null && messageHandler instanceof CompletableFuture && !((CompletableFuture<?>) messageHandler).isDone()) {
                ((CompletableFuture<?>) messageHandler).completeExceptionally(cause);
            }
            ctx.close();
        }
    }
}