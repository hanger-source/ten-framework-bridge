package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息系统的抽象基类
 * 使用Lombok大幅减少样板代码
 */
@Data
@EqualsAndHashCode
@Slf4j
public abstract non-sealed class AbstractMessage implements Message {

    @JsonProperty("name")
    private String name;

    @JsonProperty("source_location")
    private Location sourceLocation;

    @JsonProperty("destination_locations")
    private List<Location> destinationLocations = new ArrayList<>();

    @JsonProperty("properties")
    private Map<String, Object> properties = new ConcurrentHashMap<>();

    @JsonProperty("timestamp")
    private long timestamp = System.currentTimeMillis();

    /**
     * 拷贝构造函数
     */
    protected AbstractMessage(AbstractMessage other) {
        this.name = other.name;
        this.sourceLocation = other.sourceLocation;
        this.destinationLocations = new ArrayList<>(other.destinationLocations);
        this.properties = MessageUtils.deepCopyMap(other.properties);
        this.timestamp = other.timestamp;
    }

    /**
     * 默认构造函数
     */
    protected AbstractMessage() {
        // Lombok会处理字段初始化
    }

    /**
     * 构造函数，用于初始化源位置和目标位置
     */
    protected AbstractMessage(Location sourceLocation, List<Location> destinationLocations) {
        this.sourceLocation = sourceLocation;
        this.destinationLocations = destinationLocations != null ? new ArrayList<>(destinationLocations)
                : new ArrayList<>();
        this.timestamp = System.currentTimeMillis(); // 初始化时间戳
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // 重写getter/setter以提供防御性拷贝和业务逻辑
    @Override
    public List<Location> getDestinationLocations() {
        return new ArrayList<>(destinationLocations);
    }

    @Override
    public void setDestinationLocations(List<Location> locations) {
        this.destinationLocations.clear();
        if (locations != null) {
            this.destinationLocations.addAll(locations);
        }
    }

    @Override
    public void addDestinationLocation(Location location) {
        if (location != null) {
            this.destinationLocations.add(location);
        }
    }

    @Override
    public void setDestinationLocation(Location location) {
        this.destinationLocations.clear();
        if (location != null) {
            this.destinationLocations.add(location);
        }
    }

    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        this.properties.clear();
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    public Object getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public <T> T getProperty(String key, Class<T> type) {
        return MessageUtils.getTypedValue(properties, key, type, null);
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key != null) {
            if (value != null) {
                properties.put(key, value);
            } else {
                properties.remove(key);
            }
        }
    }

    @Override
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    @Override
    public Object removeProperty(String key) {
        return properties.remove(key);
    }

    @Override
    public boolean checkIntegrity() {
        // 基础完整性检查
        if (getType() == null || getType() == MessageType.INVALID) {
            log.warn("消息类型无效: {}", getType());
            return false;
        }

        if (timestamp <= 0) {
            log.warn("消息时间戳无效: {}", timestamp);
            return false;
        }

        return true;
    }

    /**
     * 抽象clone方法，由子类实现
     */
    @Override
    public abstract Message clone() throws CloneNotSupportedException;

    @Override
    @JsonIgnore
    public abstract MessageType getType();

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return toDebugString();
    }
}