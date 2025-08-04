package com.agora.tenframework.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Worker进程模型类
 *
 * 该类定义了TEN Framework中Worker进程的完整数据结构，包括：
 *
 * 1. 基本信息 - 频道名称、图形名称等标识信息
 * 2. 网络配置 - HTTP服务器端口、网络连接设置
 * 3. 日志配置 - 日志文件路径、输出方式设置
 * 4. 进程管理 - 进程ID、超时设置、生命周期时间戳
 * 5. 配置管理 - property.json配置文件路径
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的Worker结构体
 * - 使用@JsonProperty注解进行JSON序列化映射
 * - 保持与Go版本相同的字段命名规范
 *
 * 生命周期：
 * - 创建时：设置createTs和updateTs为当前时间戳
 * - 运行时：定期更新updateTs时间戳
 * - 停止时：记录停止时间和清理资源
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Worker {

    // ==================== 基本信息 ====================
    /**
     * 频道名称
     * 唯一标识Worker进程的频道名，用于区分不同的AI代理实例
     * 对应Go版本：ChannelName string
     */
    @JsonProperty("channel_name")
    private String channelName;

    /**
     * 图形名称
     * AI代理使用的图形配置名称，定义AI代理的行为逻辑
     * 对应Go版本：GraphName string
     */
    @JsonProperty("graph_name")
    private String graphName;

    // ==================== 网络配置 ====================
    /**
     * HTTP服务器端口
     * Worker进程内部HTTP服务器监听的端口号
     * 用于Worker进程与外部系统的API通信
     * 对应Go版本：HttpServerPort int32
     */
    @JsonProperty("http_server_port")
    private Integer httpServerPort;

    // ==================== 日志配置 ====================
    /**
     * 日志文件路径
     * Worker进程日志文件的完整路径
     * 用于记录Worker进程的运行日志和错误信息
     * 对应Go版本：LogFile string
     */
    @JsonProperty("log_file")
    private String logFile;

    /**
     * 是否输出到标准输出
     * 控制Worker进程日志是否同时输出到控制台
     * 用于调试和开发环境下的日志查看
     * 对应Go版本：Log2Stdout bool
     */
    @JsonProperty("log2_stdout")
    private boolean log2Stdout;

    // ==================== 配置管理 ====================
    /**
     * property.json配置文件路径
     * Worker进程使用的配置文件路径
     * 包含AI代理的完整配置，包括图形定义、扩展模块配置等
     * 对应Go版本：PropertyJsonFile string
     */
    @JsonProperty("property_json_file")
    private String propertyJsonFile;

    // ==================== 进程管理 ====================
    /**
     * 进程ID (PID)
     * Worker进程的操作系统进程ID
     * 用于进程管理和信号发送（如停止进程）
     * 对应Go版本：Pid int
     */
    @JsonProperty("pid")
    private Integer pid;

    /**
     * 退出超时时间（秒）
     * Worker进程优雅关闭的最大等待时间
     * 超过此时间后强制终止进程
     * 默认值：60秒
     * 对应Go版本：QuitTimeoutSeconds int
     */
    @JsonProperty("quit_timeout_seconds")
    private Integer quitTimeoutSeconds;

    // ==================== 生命周期时间戳 ====================
    /**
     * 创建时间戳（秒）
     * Worker进程创建时的Unix时间戳
     * 用于计算Worker进程的运行时长
     * 对应Go版本：CreateTs int64
     */
    @JsonProperty("create_ts")
    private Long createTs;

    /**
     * 更新时间戳（秒）
     * Worker进程最后更新时的Unix时间戳
     * 用于监控Worker进程的活动状态
     * 对应Go版本：UpdateTs int64
     */
    @JsonProperty("update_ts")
    private Long updateTs;

    /**
     * 默认构造函数
     *
     * 初始化Worker对象时：
     * 1. 设置创建时间和更新时间为当前时间戳
     * 2. 设置默认的退出超时时间为60秒
     */
    public Worker() {
        this.createTs = Instant.now().getEpochSecond(); // 当前时间戳（秒）
        this.updateTs = Instant.now().getEpochSecond(); // 当前时间戳（秒）
        this.quitTimeoutSeconds = 60; // 默认超时时间60秒
    }

    // ==================== Getter和Setter方法 ====================
    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Integer getHttpServerPort() {
        return httpServerPort;
    }

    public void setHttpServerPort(Integer httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public boolean isLog2Stdout() {
        return log2Stdout;
    }

    public void setLog2Stdout(boolean log2Stdout) {
        this.log2Stdout = log2Stdout;
    }

    public String getPropertyJsonFile() {
        return propertyJsonFile;
    }

    public void setPropertyJsonFile(String propertyJsonFile) {
        this.propertyJsonFile = propertyJsonFile;
    }

    public String getGraphName() {
        return graphName;
    }

    public void setGraphName(String graphName) {
        this.graphName = graphName;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public Integer getQuitTimeoutSeconds() {
        return quitTimeoutSeconds;
    }

    public void setQuitTimeoutSeconds(Integer quitTimeoutSeconds) {
        this.quitTimeoutSeconds = quitTimeoutSeconds;
    }

    public Long getCreateTs() {
        return createTs;
    }

    public void setCreateTs(Long createTs) {
        this.createTs = createTs;
    }

    public Long getUpdateTs() {
        return updateTs;
    }

    public void setUpdateTs(Long updateTs) {
        this.updateTs = updateTs;
    }
}