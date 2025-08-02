package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 启动Worker请求模型类
 *
 * 该类定义了启动AI代理Worker进程的完整请求数据结构，包括：
 *
 * 1. 请求标识 - requestId用于请求追踪和日志记录
 * 2. 频道配置 - channelName用于标识唯一的AI代理实例
 * 3. 图形配置 - graphName指定AI代理使用的行为图形
 * 4. 音视频配置 - 远程流ID和机器人流ID用于Agora RTC
 * 5. 安全配置 - token用于Agora服务的身份验证
 * 6. 网络配置 - workerHttpServerPort用于Worker内部HTTP服务
 * 7. 扩展配置 - properties用于传递额外的配置参数
 * 8. 超时配置 - quitTimeoutSeconds用于进程生命周期管理
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的StartReq结构体
 * - 使用@JsonProperty注解进行JSON序列化映射
 * - 保持与Go版本相同的字段命名规范
 *
 * 请求流程：
 * 1. 客户端发送StartRequest到TEN Framework服务器
 * 2. 服务器验证请求参数的有效性
 * 3. 根据channelName检查是否已存在Worker进程
 * 4. 生成property.json配置文件
 * 5. 启动Worker进程并返回结果
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class StartRequest {

    // ==================== 请求标识 ====================
    /**
     * 请求ID
     * 用于请求追踪、日志记录和错误定位
     * 每个请求都应该有唯一的requestId
     * 对应Go版本：RequestId string
     */
    @JsonProperty("request_id")
    private String requestId;

    // ==================== 频道配置 ====================
    /**
     * 频道名称
     * 唯一标识AI代理实例的频道名
     * 用于区分不同的AI代理，确保同一频道只有一个Worker进程
     * 对应Go版本：ChannelName string
     */
    @JsonProperty("channel_name")
    private String channelName;

    // ==================== 图形配置 ====================
    /**
     * 图形名称
     * 指定AI代理使用的行为图形配置
     * 定义AI代理的对话逻辑、扩展模块调用等行为
     * 对应Go版本：GraphName string
     */
    @JsonProperty("graph_name")
    private String graphName;

    // ==================== 音视频配置 ====================
    /**
     * 远程用户流ID
     * 远程用户（客户端）在Agora RTC中的流ID
     * 用于标识远程用户的音视频流
     * 对应Go版本：RemoteStreamId uint32
     */
    @JsonProperty("user_uid")
    private Long remoteStreamId;

    /**
     * 机器人流ID
     * AI代理（机器人）在Agora RTC中的流ID
     * 用于标识AI代理的音视频流
     * 对应Go版本：BotStreamId uint32
     */
    @JsonProperty("bot_uid")
    private Long botStreamId;

    // ==================== 安全配置 ====================
    /**
     * Agora Token
     * 用于Agora RTC/RTM服务的身份验证
     * 确保只有授权的客户端和AI代理可以访问音视频服务
     * 对应Go版本：Token string
     */
    @JsonProperty("token")
    private String token;

    // ==================== 网络配置 ====================
    /**
     * Worker HTTP服务器端口
     * Worker进程内部HTTP服务器监听的端口号
     * 用于Worker进程与外部系统的API通信
     * 如果未指定，系统会自动分配可用端口
     * 对应Go版本：WorkerHttpServerPort int32
     */
    @JsonProperty("worker_http_server_port")
    private Integer workerHttpServerPort;

    // ==================== 扩展配置 ====================
    /**
     * 扩展属性配置
     * 用于传递额外的配置参数到AI代理
     * 支持动态配置AI代理的行为和功能
     * 对应Go版本：Properties map[string]map[string]interface{}
     */
    @JsonProperty("properties")
    private Map<String, Map<String, Object>> properties;

    // ==================== 超时配置 ====================
    /**
     * 退出超时时间（秒）
     * Worker进程优雅关闭的最大等待时间
     * 超过此时间后强制终止进程
     * 对应Go版本：QuitTimeoutSeconds int
     */
    @JsonProperty("timeout")
    private Integer quitTimeoutSeconds;

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

    public String getGraphName() {
        return graphName;
    }

    public void setGraphName(String graphName) {
        this.graphName = graphName;
    }

    public Long getRemoteStreamId() {
        return remoteStreamId;
    }

    public void setRemoteStreamId(Long remoteStreamId) {
        this.remoteStreamId = remoteStreamId;
    }

    public Long getBotStreamId() {
        return botStreamId;
    }

    public void setBotStreamId(Long botStreamId) {
        this.botStreamId = botStreamId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getWorkerHttpServerPort() {
        return workerHttpServerPort;
    }

    public void setWorkerHttpServerPort(Integer workerHttpServerPort) {
        this.workerHttpServerPort = workerHttpServerPort;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Map<String, Object>> properties) {
        this.properties = properties;
    }

    public Integer getQuitTimeoutSeconds() {
        return quitTimeoutSeconds;
    }

    public void setQuitTimeoutSeconds(Integer quitTimeoutSeconds) {
        this.quitTimeoutSeconds = quitTimeoutSeconds;
    }
}