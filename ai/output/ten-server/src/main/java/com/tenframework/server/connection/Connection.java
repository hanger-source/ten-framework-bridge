package com.tenframework.server.connection;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.Location;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 代表一个与外部客户端的活跃网络连接。
 * 它管理连接的生命周期，并作为 Protocol 层与 Engine 层之间的桥梁。
 * 对齐C/Python中的ten_connection_t结构体。
 */
@Data
@Slf4j
public class Connection {

    private final String connectionId; // 连接的唯一标识，类似于 C/Python 中的 connection_id
    private final Channel channel; // Netty 的 Channel 实例
    private Engine engine; // 关联的 Engine 实例，可能通过构造函数注入或后续绑定
    private Location remoteLocation; // 如果已知，表示远程连接的 Location

    public Connection(Channel channel) {
        this.channel = channel;
        this.connectionId = UUID.randomUUID().toString(); // 生成唯一ID
        log.info("Connection {}: 新建连接 from {}", connectionId, channel.remoteAddress());
    }

    /**
     * 接收从 Protocol 层解码后的 Message。
     * 此方法会将消息提交到关联 Engine 的输入队列。
     * 
     * @param message 解码后的消息
     */
    public void onMessageReceived(Message message) {
        if (engine != null) {
            log.debug("Connection {}: 接收到消息，提交给Engine: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            engine.submitMessage(message);
        } else {
            log.warn("Connection {}: 接收到消息但未绑定Engine，消息将被丢弃: type={}, id={}", connectionId, message.getType(),
                    message.getId());
        }
    }

    /**
     * 将 Message 序列化并通过 channel 发送到远程客户端。
     * 
     * @param message 要发送的消息
     */
    public void sendMessage(Message message) {
        if (channel.isActive()) {
            log.debug("Connection {}: 发送消息到远程客户端: type={}, id={}", connectionId, message.getType(), message.getId());
            channel.writeAndFlush(message); // Netty管道会自动通过MessageEncoder进行编码
        } else {
            log.warn("Connection {}: 连接不活跃，无法发送消息: type={}, id={}", connectionId, message.getType(), message.getId());
        }
    }

    /**
     * 关闭底层 Netty Channel 并清理资源。
     */
    public void close() {
        if (channel.isOpen()) {
            log.info("Connection {}: 关闭连接 {}", connectionId, channel.remoteAddress());
            channel.close();
        }
    }

    /**
     * 将此 Connection 绑定到特定的 Engine 实例。
     * 
     * @param engine 要绑定的 Engine 实例
     */
    public void bindToEngine(Engine engine) {
        if (this.engine == null) {
            this.engine = engine;
            log.info("Connection {}: 绑定到Engine {}", connectionId, engine.getAppUri()); // 假设Engine有getAppUri方法
        } else {
            log.warn("Connection {}: 尝试重复绑定Engine，已忽略。", connectionId);
        }
    }
}