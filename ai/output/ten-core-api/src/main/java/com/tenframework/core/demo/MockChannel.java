package com.tenframework.core.demo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * MockChannel 是 io.netty.channel.Channel 的一个简单模拟实现，仅用于演示。
 * 它不包含实际的网络通信逻辑，只提供最基本的方法。
 */
public class MockChannel implements Channel {

    private final ChannelId id = new MockChannelId();
    private final SocketAddress localAddress = new SocketAddress() {
    };
    private final SocketAddress remoteAddress = new SocketAddress() {
    };
    private volatile boolean active = true; // 模拟通道的活跃状态

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public EventLoop eventLoop() {
        // 简单模拟，实际可能需要一个专用的 EventLoop
        return null;
    }

    @Override
    public ChannelPipeline pipeline() {
        // 简单模拟
        return null;
    }

    @Override
    public ChannelConfig config() {
        // 简单模拟
        return null;
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return new ChannelMetadata(false);
    }

    @Override
    public SocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public ChannelPromise voidPromise() {
        return null;
    }

    @Override
    public ChannelOutboundBuffer outboundBuffer() {
        return null;
    }

    @Override
    public Unsafe unsafe() {
        return null;
    }

    @Override
    public ChannelFuture closeFuture() {
        return null;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public long bytesBeforeUnwritable() {
        return 0;
    }

    @Override
    public long bytesBeforeWritable() {
        return 0;
    }

    @Override
    public Channel read() {
        return this;
    }

    @Override
    public Channel flush() {
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return null;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return null;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return null;
    }

    @Override
    public ChannelFuture disconnect() {
        return null;
    }

    @Override
    public ChannelFuture close() {
        this.active = false;
        return null;
    }

    @Override
    public ChannelFuture deregister() {
        return null;
    }

    @Override
    public ChannelFuture write(Object msg) {
        // 模拟写入操作，通常这里会打印或处理消息
        // System.out.println("MockChannel: 写入消息: " + msg);
        return null;
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        // System.out.println("MockChannel: 写入消息 (带promise): " + msg);
        promise.setSuccess(); // 假设写入成功
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        // System.out.println("MockChannel: 写入并刷新消息 (带promise): " + msg);
        promise.setSuccess();
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        // System.out.println("MockChannel: 写入并刷新消息: " + msg);
        return null;
    }

    @Override
    public ChannelPromise newPromise() {
        return null;
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return null;
    }

    @Override
    public io.netty.util.concurrent.EventExecutor executor() {
        return null;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return false;
    }

    @Override
    public int compareTo(Channel o) {
        return 0;
    }

    // 内部类用于模拟 ChannelId
    private static class MockChannelId implements ChannelId {
        private final String id = UUID.randomUUID().toString();

        @Override
        public String asShortText() {
            return id.substring(0, 8);
        }

        @Override
        public String asLongText() {
            return id;
        }

        @Override
        public int compareTo(ChannelId o) {
            return id.compareTo(o.asLongText());
        }
    }
}