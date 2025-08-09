package com.tenframework.core.connection;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.ConnectionMigrationState;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.protocol.Protocol;
import com.tenframework.core.runloop.Runloop; // 引入 Runloop
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
// import java.util.concurrent.ExecutorService; // 移除 ExecutorService 导入

/**
 * AbstractConnection 提供了 Connection 接口的骨架实现，
 * 对齐C/Python中的ten_connection_t结构体。
 */
@Slf4j
public abstract class AbstractConnection implements Connection {

    protected final String connectionId;
    protected final Channel channel;
    protected Engine engine;
    protected Location remoteLocation;
    protected ConnectionMigrationState migrationState; // 追踪连接迁移状态
    protected Protocol protocol; // 关联的协议处理器

    // TODO: 添加 App 和 Engine 的引用（或通过 App/Engine 注入此 Connection 实例）

    public AbstractConnection(Channel channel, Protocol protocol) {
        this.channel = channel;
        this.connectionId = UUID.randomUUID().toString();
        this.migrationState = ConnectionMigrationState.INITIAL; // 初始状态
        this.protocol = protocol;
        log.info("Connection {}: 新建连接 from {}", connectionId, channel.remoteAddress());
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public Location getRemoteLocation() {
        return remoteLocation;
    }

    public void setRemoteLocation(Location remoteLocation) {
        this.remoteLocation = remoteLocation;
    }

    @Override
    public ConnectionMigrationState getMigrationState() {
        return migrationState;
    }

    public void setMigrationState(ConnectionMigrationState migrationState) {
        this.migrationState = migrationState;
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public void onMessageReceived(Message message) {
        // 消息接收逻辑，可能涉及线程迁移判断
        if (engine != null) {
            log.debug("Connection {}: 接收到消息，提交给Engine: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            // TODO: 这里需要考虑线程迁移，消息可能需要 post 到 Engine 的线程循环
            engine.submitMessage(message);
        } else {
            log.warn("Connection {}: 接收到消息但未绑定Engine，消息将被丢弃: type={}, id={}", connectionId, message.getType(),
                    message.getId());
        }
    }

    @Override
    public void sendMessage(Message message) {
        if (channel.isActive()) {
            log.debug("Connection {}: 发送消息到远程客户端: type={}, id={}", connectionId, message.getType(), message.getId());
            // 使用 protocol 编码消息
            byte[] encodedMessage = protocol.encode(message);
            channel.writeAndFlush(encodedMessage);
        } else {
            log.warn("Connection {}: 连接不活跃，无法发送消息: type={}, id={}", connectionId, message.getType(), message.getId());
        }
    }

    @Override
    public void close() {
        if (channel.isOpen()) {
            log.info("Connection {}: 关闭连接 {}", connectionId, channel.remoteAddress());
            cleanup(); // 执行清理逻辑
            channel.close();
        }
    }

    @Override
    public void bindToEngine(Engine engine) {
        if (this.engine == null) {
            this.engine = engine;
            // TODO: 在绑定 Engine 后，可能需要触发迁移状态更新
            log.info("Connection {}: 绑定到Engine {}", connectionId, engine.getGraphId()); // 假设Engine有getGraphId方法
        } else {
            log.warn("Connection {}: 尝试重复绑定Engine，已忽略。", connectionId);
        }
    }

    @Override
    public void migrate(Runloop targetRunloop) { // 修改方法签名，接受 Runloop
        // 实现 C 语言中 ten_connection_migrate 的逻辑
        // 将 Connection 的所有权和后续处理任务提交到 targetExecutor
        if (migrationState == ConnectionMigrationState.FIRST_MSG
                || migrationState == ConnectionMigrationState.INITIAL) {
            this.migrationState = ConnectionMigrationState.MIGRATING;
            // 提交一个任务到目标 Runloop，以完成迁移的后续步骤
            targetRunloop.postTask(() -> { // 使用 postTask 提交任务
                try {
                    protocol.handshake(); // 协议层进行迁移握手
                    onMigrated(); // 通知 Connection 迁移完成
                } catch (Exception e) {
                    log.error("Connection {}: 迁移失败: {}", connectionId, e.getMessage());
                    // TODO: 错误处理和回滚
                }
            });
        } else {
            log.warn("Connection {}: 无法迁移，当前状态为 {}", connectionId, migrationState);
        }
    }

    @Override
    public void onMigrated() {
        // 迁移完成后的回调，更新状态
        this.migrationState = ConnectionMigrationState.MIGRATED;
        log.info("Connection {}: 迁移到新线程完成，当前状态: {}", connectionId, migrationState);
        onProtocolMigrated();
    }

    @Override
    public void cleanup() {
        // 清理 Connection 相关的资源
        if (migrationState != ConnectionMigrationState.CLEANED) {
            this.migrationState = ConnectionMigrationState.CLEANING;
            log.info("Connection {}: 开始清理资源...", connectionId);
            if (protocol != null) {
                protocol.cleanup(); // 清理协议资源
            }
            // TODO: 清理其他资源，如解除与 Engine 的绑定等
            this.migrationState = ConnectionMigrationState.CLEANED;
            log.info("Connection {}: 资源清理完成，当前状态: {}", connectionId, migrationState);
            onProtocolCleaned();
        }
    }

    @Override
    public void onProtocolMigrated() {
        log.info("Connection {}: 协议层已迁移", connectionId);
    }

    @Override
    public void onProtocolCleaned() {
        log.info("Connection {}: 协议层已清理", connectionId);
    }
}