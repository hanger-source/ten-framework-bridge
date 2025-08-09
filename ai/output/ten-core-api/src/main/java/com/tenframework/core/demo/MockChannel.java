package com.tenframework.core.demo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * `MockChannel` 是 `io.netty.channel.Channel` 接口的一个模拟实现。
 * 它用于在不依赖真实网络栈的情况下，模拟 Netty `Channel` 的行为。
 * 主要用于单元测试和演示，以隔离网络层的复杂性。
 */
public class MockChannel implements Channel {

    private final ChannelId id = new MockChannelId();
    private final SocketAddress localAddress = new SocketAddress() {
    };
    private final SocketAddress remoteAddress = new SocketAddress() {
    };
    private volatile boolean active = true; // 模拟连接活跃状态

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelFuture close() {
        this.active = false;
        // 返回一个已完成的 ChannelFuture，表示关闭操作立即完成
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture closeFuture() {
        return new MockChannelFuture(); // 模拟返回一个 future
    }

    @Override
    public Channel read() {
        return this; // 模拟读操作
    }

    @Override
    public Channel flush() {
        return this; // 模拟 flush 操作
    }

    @Override
    public ChannelFuture write(Object msg) {
        System.out.println("MockChannel: 写入消息 -> " + msg); // 模拟写入消息
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        System.out.println("MockChannel: 写入消息 (带Promise) -> " + msg); // 模拟写入消息
        promise.setSuccess(); // 立即成功
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        System.out.println("MockChannel: 写入并刷新消息 (带Promise) -> " + msg); // 模拟写入消息
        promise.setSuccess(); // 立即成功
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        System.out.println("MockChannel: 写入并刷新消息 -> " + msg); // 模拟写入消息
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture disconnect() {
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture deregister() {
        return new MockChannelFuture();
    }

    @Override
    public ChannelPromise newPromise() {
        return new MockChannelPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return null; // 简化实现，不处理 ProgressivePromise
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return new MockChannelFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new MockChannelFuture(cause); // 模拟失败的 Future
    }

    @Override
    public ChannelPromise voidPromise() {
        return new MockChannelPromise(); // 简单的 void promise
    }

    // 最小化实现其他不常用的方法

    @Override
    public Channel parent() {
        return null;
    }

    @Override
    public ChannelConfig config() {
        return null; // 简化实现
    }

    @Override
    public Unsafe unsafe() {
        return null; // 简化实现
    }

    @Override
    public EventLoop eventLoop() {
        return null; // 简化实现
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
    public boolean isRegistered() {
        return true; // 简化实现
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return null; // 简化实现
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return false; // 简化实现
    }

    @Override
    public int compareTo(Channel o) {
        return id().compareTo(o.id());
    }

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

    private static class MockChannelFuture implements ChannelFuture {
        private Throwable cause;

        public MockChannelFuture() {
        }

        public MockChannelFuture(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public boolean isSuccess() {
            return cause == null;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Channel channel() {
            return null; // 简化
        }

        @Override
        public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            // 简化：不实际调用监听器，假设立即完成
            return this;
        }

        @Override
        public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return this;
        }

        @Override
        public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            return this;
        }

        @Override
        public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return this;
        }

        @Override
        public ChannelFuture sync() throws InterruptedException {
            return this; // 简化
        }

        @Override
        public ChannelFuture syncUninterruptibly() {
            return this; // 简化
        }

        @Override
        public ChannelFuture await() throws InterruptedException {
            return this; // 简化
        }

        @Override
        public ChannelFuture awaitUninterruptibly() {
            return this; // 简化
        }

        @Override
        public boolean await(long timeoutMillis, TimeUnit unit) throws InterruptedException {
            return true; // 简化
        }

        @Override
        public boolean await(long timeoutMillis) throws InterruptedException {
            return true; // 简化
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis, TimeUnit unit) {
            return true; // 简化
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            return true; // 简化
        }

        @Override
        public Void getNow() {
            return null; // 简化
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false; // 简化
        }

        @Override
        public boolean isCancelled() {
            return false; // 简化
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            if (cause != null) {
                throw new ExecutionException(cause);
            }
            return null; // 简化
        }

        @Override
        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get(); // 简化
        }
    }

    private static class MockChannelPromise extends MockChannelFuture implements ChannelPromise {
        @Override
        public ChannelPromise setSuccess() {
            return this;
        }

        @Override
        public ChannelPromise setSuccess(Void result) {
            return this;
        }

        @Override
        public boolean trySuccess() {
            return true;
        }

        @Override
        public boolean trySuccess(Void result) {
            return true;
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            super.cause = cause;
            return this;
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            super.cause = cause;
            return true;
        }

        @Override
        public boolean setUncancellable() {
            return true;
        }

        @Override
        public ChannelPromise await() throws InterruptedException {
            return this;
        }

        @Override
        public ChannelPromise awaitUninterruptibly() {
            return this;
        }

        @Override
        public ChannelPromise sync() throws InterruptedException {
            return this;
        }

        @Override
        public ChannelPromise syncUninterruptibly() {
            return this;
        }
    }
}