package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 生成Token请求模型类
 *
 * 该类定义了生成Agora Token的请求数据结构，包括：
 *
 * 核心功能：
 * 1. 请求标识管理 - 通过requestId跟踪请求状态
 * 2. 频道信息管理 - 指定要生成Token的频道名称
 * 3. 用户标识管理 - 指定用户的唯一标识符
 *
 * 使用场景：
 * - RTC Token生成 - 为实时音视频通话生成Token
 * - RTM Token生成 - 为实时消息传输生成Token
 * - Worker Token生成 - 为Worker进程生成Token
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的GenerateTokenRequest结构体
 * - 保持相同的JSON字段名称和数据结构
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class GenerateTokenRequest {

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 频道名称
     * 指定要生成Token的频道名称
     */
    @JsonProperty("channel_name")
    private String channelName;

    /**
     * 用户ID
     * 指定用户的唯一标识符，用于Token生成
     */
    @JsonProperty("uid")
    private Long uid;

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

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }
}