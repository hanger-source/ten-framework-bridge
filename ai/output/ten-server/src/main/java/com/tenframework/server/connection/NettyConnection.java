package com.tenframework.server.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.connection.AbstractConnection;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageConstants;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.runloop.Runloop;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * NettyConnection 是 Connection 接口的实现，用于封装 Netty Channel。
 * 它处理消息的编解码和通过 Netty Channel 的发送。
 */
@Slf4j
public class NettyConnection extends AbstractConnection {

    // 用于在 Netty Channel 中存储 NettyConnection 实例的属性键
    public static final AttributeKey<NettyConnection> CONNECTION_ATTRIBUTE_KEY = AttributeKey
            .newInstance("NettyConnection");

    private final Channel channel;
    private final ObjectMapper objectMapper; // 用于消息序列化

    public NettyConnection(String connectionId, SocketAddress remoteAddress, Channel channel, Runloop initialRunloop) {
        super(connectionId, remoteAddress, initialRunloop); // 调用父类构造函数
        this.channel = channel;
        this.objectMapper = new ObjectMapper(); // 或从一个共享的 MapperProvider 获取
        log.info("NettyConnection: {} 实例创建，绑定到 Channel {}", connectionId, channel.id().asShortText());
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    protected CompletableFuture<Void> sendOutboundMessageInternal(Message message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (channel.isActive()) {
            try {
                // 将 Message 对象序列化为 MsgPack 字节数组
                byte[] msgpackData = objectMapper.writeValueAsBytes(message);
                ByteBuf buffer = Unpooled.wrappedBuffer(msgpackData);
                BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);

                ChannelFuture writeFuture = channel.writeAndFlush(frame);
                writeFuture.addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("NettyConnection {}: 消息 {} (类型: {}) 发送成功。", getConnectionId(), message.getId(),
                                message.getType());
                        future.complete(null);
                    } else {
                        log.error("NettyConnection {}: 消息 {} (类型: {}) 发送失败: {}", getConnectionId(), message.getId(),
                                message.getType(), f.cause().getMessage());
                        future.completeExceptionally(f.cause());
                    }
                });
            } catch (IOException e) {
                log.error("NettyConnection {}: 消息 {} (类型: {}) 序列化失败: {}", getConnectionId(), message.getId(),
                        message.getType(), e.getMessage());
                future.completeExceptionally(e);
            }
        } else {
            log.warn("NettyConnection {}: Channel 不活跃，消息 {} (类型: {}) 无法发送。", getConnectionId(), message.getId(),
                    message.getType());
            future.completeExceptionally(new IllegalStateException("Channel is not active."));
        }
        return future;
    }

    @Override
    public void close() {
        if (channel.isOpen()) {
            log.info("NettyConnection {}: 关闭底层 Netty Channel {}", getConnectionId(), channel.id().asShortText());
            channel.close();
        }
        super.close();
    }
}