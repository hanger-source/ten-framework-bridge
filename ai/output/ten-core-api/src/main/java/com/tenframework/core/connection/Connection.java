package com.tenframework.core.connection;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.ConnectionMigrationState;
import com.tenframework.core.protocol.Protocol;
import com.tenframework.core.runloop.Runloop;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Connection 接口定义了与外部客户端连接的核心行为。
 */
public interface Connection {
    String getConnectionId();

    Channel getChannel();

    Engine getEngine();

    Location getRemoteLocation();

    ConnectionMigrationState getMigrationState();

    Protocol getProtocol();

    void onMessageReceived(Message message);

    void sendMessage(Message message);

    void close();

    void bindToEngine(Engine engine);

    void migrate(Runloop targetRunloop);

    void onMigrated();

    void cleanup();

    void onProtocolMigrated();

    void onProtocolCleaned();
}