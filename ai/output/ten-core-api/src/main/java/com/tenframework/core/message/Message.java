package com.tenframework.core.message;

import com.tenframework.core.Location;
import java.util.Map;
import java.util.List;

/**
 * TEN框架消息系统的根接口
 * 使用sealed interface确保类型安全和完整性
 * 对应C语言中的ten_msg_t结构
 */
public sealed interface Message
        permits AbstractMessage {

    /**
     * 获取消息类型
     */
    MessageType getType();

    /**
     * 获取消息名称，用于路由
     * 如果消息名称为空，只能流向图中未指定名称的目标扩展
     */
    String getName();

    /**
     * 设置消息名称
     */
    void setName(String name);

    /**
     * 获取源位置
     */
    Location getSourceLocation();

    /**
     * 设置源位置
     */
    void setSourceLocation(Location location);

    /**
     * 获取目标位置列表
     * 支持1对多的消息分发
     */
    List<Location> getDestinationLocations();

    /**
     * 设置目标位置列表
     */
    void setDestinationLocations(List<Location> locations);

    /**
     * 添加目标位置
     */
    void addDestinationLocation(Location location);

    /**
     * 清空并设置单个目标位置
     */
    void setDestinationLocation(Location location);

    /**
     * 获取消息属性
     * 支持动态属性存储和访问
     */
    Map<String, Object> getProperties();

    /**
     * 设置消息属性
     */
    void setProperties(Map<String, Object> properties);

    /**
     * 获取指定属性值
     */
    Object getProperty(String key);

    /**
     * 获取指定属性值，带类型转换
     */
    <T> T getProperty(String key, Class<T> type);

    /**
     * 设置属性值
     */
    void setProperty(String key, Object value);

    /**
     * 检查属性是否存在
     */
    boolean hasProperty(String key);

    /**
     * 移除属性
     */
    Object removeProperty(String key);

    /**
     * 获取时间戳（毫秒）
     */
    long getTimestamp();

    /**
     * 设置时间戳
     */
    void setTimestamp(long timestamp);

    /**
     * 深拷贝消息
     * 确保在多路分发时每个接收方都有独立的副本
     *
     * @return 深拷贝的消息实例
     */
    Message clone() throws CloneNotSupportedException;

    /**
     * 验证消息完整性
     *
     * @return 如果消息有效返回true，否则返回false
     */
    boolean checkIntegrity();

    /**
     * 获取消息的调试字符串表示
     */
    default String toDebugString() {
        return String.format("%s[name=%s, src=%s, dest=%s, timestamp=%d]",
                getType().getValue(),
                getName(),
                getSourceLocation(),
                getDestinationLocations(),
                getTimestamp());
    }
}