package com.tenframework.core.path;

import java.util.UUID;

import com.tenframework.core.message.Location;

// 类似于 PathOut，用于跟踪从客户端进入的 Data 消息的路径和上下文
public class DataInPath {
    private final UUID dataPathId; // 唯一标识这条 Data 消息的路径
    private final Location clientLocation; // 原始客户端的完整 Location
    private final String channelId; // 原始客户端连接的 Channel ID
    private final long creationTimestamp; // 路径创建时间，用于超时管理
    private final String clientAppUri;
    private final String clientGraphId;

    public DataInPath(UUID dataPathId, Location clientLocation, String channelId) {
        this.dataPathId = dataPathId;
        this.clientLocation = clientLocation;
        this.channelId = channelId;
        this.creationTimestamp = System.currentTimeMillis();
        
        if (clientLocation != null) {
            this.clientAppUri = clientLocation.appUri();
            this.clientGraphId = clientLocation.graphId();
        } else {
            this.clientAppUri = null;
            this.clientGraphId = null;
        }
    }

    public UUID getDataPathId() {
        return dataPathId;
    }

    public Location getClientLocation() {
        return clientLocation;
    }

    public String getChannelId() {
        return channelId;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }
}