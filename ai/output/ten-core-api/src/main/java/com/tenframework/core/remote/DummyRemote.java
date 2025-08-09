package com.tenframework.core.remote;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.Location;
import com.tenframework.core.connection.Connection;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.Optional;

/**
 * DummyRemote 是 Remote 抽象类的一个简化实现，用于测试和占位。
 * 它不包含实际的网络通信逻辑。
 */
@Slf4j
public class DummyRemote extends Remote {

    // 简化：目前只关联一个 Connection，实际 Remote 可能管理多个 Connection
    private Optional<Connection> associatedConnection;

    public DummyRemote(String remoteId, Location remoteEngineLocation, Engine localEngine, Optional<Connection> initialConnection) {
        super(remoteId, remoteEngineLocation, localEngine);
        this.associatedConnection = initialConnection;
        log.info("DummyRemote: 创建了 Remote 实例: remoteId={}, remoteEngineLocation={}, localEngineId={}",
                remoteId, remoteEngineLocation, localEngine.getEngineId());
    }

    @Override
    public void activate() {
        setActive(true);
        log.debug("DummyRemote: 激活 Remote: {}", getRemoteId());
    }

    @Override
    public void sendMessage(Message message) {
        if (!isActive()) {
            log.warn("DummyRemote: Remote {} 不活跃，无法发送消息: {}", getRemoteId(), message.getId());
            return;
        }
        // 模拟消息发送，通常会通过 Connection 或其他网络通道发送
        log.info("DummyRemote: 发送消息: remoteId={}, messageId={}, messageType={}",
                getRemoteId(), message.getId(), message.getType());

        associatedConnection.ifPresent(conn -> {
            conn.sendMessage(message); // 通过关联的 Connection 发送消息
            log.debug("DummyRemote: 消息 {} 已通过关联的 Connection {} 发送。", message.getId(), conn.getConnectionId());
        });
        // TODO: 如果没有 associatedConnection，可能需要通过 Engine 找到对应的 Connection 或其他 Remote 机制发送
    }

    @Override
    public void shutdown() {
        setActive(false);
        log.debug("DummyRemote: 关闭 Remote: {}", getRemoteId());
        // 清理关联的 Connection
        associatedConnection.ifPresent(Connection::close); // 调用 Connection 的关闭方法
        associatedConnection = Optional.empty();
    }

    public Optional<Connection> getAssociatedConnection() {
        return associatedConnection;
    }

    public void setAssociatedConnection(Connection connection) {
        this.associatedConnection = Optional.ofNullable(connection);
    }
}