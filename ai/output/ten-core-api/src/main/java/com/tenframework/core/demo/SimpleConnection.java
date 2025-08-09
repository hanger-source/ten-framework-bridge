package com.tenframework.core.demo;

import com.tenframework.core.connection.AbstractConnection;
import com.tenframework.core.protocol.Protocol;
import io.netty.channel.Channel;

/**
 * SimpleConnection 是 AbstractConnection 的一个简单实现，用于演示。
 * 它使用 MockChannel 和 Protocol 模拟真实的连接行为。
 */
public class SimpleConnection extends AbstractConnection {

    public SimpleConnection(Channel channel, Protocol protocol) {
        super(channel, protocol);
    }

    // 可以在这里添加 SimpleConnection 特有的模拟行为或测试方法
}