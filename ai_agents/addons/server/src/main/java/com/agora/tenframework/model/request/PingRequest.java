package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ping请求模型类
 *
 * 该类定义了Ping Worker进程的请求数据结构，包括：
 *
 * 核心功能：
 * 1. 请求标识管理 - 通过requestId跟踪请求状态
 * 2. 频道信息管理 - 指定要Ping的Worker所属频道
 *
 * 使用场景：
 * - 健康检查 - 检查Worker进程是否正常运行
 * - 连接测试 - 测试与Worker进程的网络连接
 * - 状态监控 - 监控Worker进程的响应状态
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的PingRequest结构体
 * - 保持相同的JSON字段名称和数据结构
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class PingRequest {

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 频道名称
     * 指定要Ping的Worker所属频道
     */
    @JsonProperty("channel_name")
    private String channelName;

    // ==================== Getter和Setter方法 ====================
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }
}