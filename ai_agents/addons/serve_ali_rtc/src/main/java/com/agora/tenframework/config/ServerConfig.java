package com.agora.tenframework.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TEN Framework 服务器配置类
 *
 * 该类负责管理TEN Framework服务器的核心配置参数，包括：
 *
 * 1. Agora服务配置 - AppId和AppCertificate用于Token生成
 * 2. 日志配置 - 日志路径和输出方式设置
 * 3. 服务器配置 - 监听端口和服务器参数
 * 4. Worker管理配置 - Worker数量限制和超时设置
 * 5. 配置验证 - 启动时验证配置参数的有效性
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的HttpServerConfig结构体
 * - 使用Spring Boot的@ConfigurationProperties进行配置绑定
 * - 实现InitializingBean接口进行启动时验证
 *
 * 配置前缀：ten.server
 * 配置文件：application.yml中的ten.server节点
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "ten.server")
public class ServerConfig implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    // ==================== Agora服务配置 ====================
    /**
     * Agora AppId
     * 用于生成Agora Token，必须为32位字符串
     * 对应Go版本：AppId string
     */
    private String appId;

    /**
     * Agora AppCertificate
     * 用于生成Agora Token的证书，用于服务端Token生成
     * 对应Go版本：AppCertificate string
     */
    private String appCertificate;

    // ==================== 日志配置 ====================
    /**
     * 日志文件路径
     * Worker进程日志文件的存储目录
     * 对应Go版本：LogPath string
     */
    private String logPath;

    /**
     * 是否输出到标准输出
     * 控制Worker进程日志是否同时输出到控制台
     * 对应Go版本：Log2Stdout bool
     */
    private boolean log2Stdout = false;

    // ==================== 服务器配置 ====================
    /**
     * 服务器监听端口
     * TEN Framework服务器监听的端口号
     * 对应Go版本：Port string
     */
    private String port;

    // ==================== Worker管理配置 ====================
    /**
     * 最大Worker数量
     * 限制同时运行的Worker进程数量，防止资源耗尽
     * 默认值：10
     * 对应Go版本：WorkersMax int
     */
    private int workersMax = 10;

    /**
     * Worker退出超时时间（秒）
     * Worker进程优雅关闭的最大等待时间
     * 默认值：60秒
     * 对应Go版本：WorkerQuitTimeoutSeconds int
     */
    private int workerQuitTimeoutSeconds = 60;

    // ==================== Getter和Setter方法 ====================
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppCertificate() {
        return appCertificate;
    }

    public void setAppCertificate(String appCertificate) {
        this.appCertificate = appCertificate;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public int getWorkersMax() {
        return workersMax;
    }

    public void setWorkersMax(int workersMax) {
        this.workersMax = workersMax;
    }

    public int getWorkerQuitTimeoutSeconds() {
        return workerQuitTimeoutSeconds;
    }

    public void setWorkerQuitTimeoutSeconds(int workerQuitTimeoutSeconds) {
        this.workerQuitTimeoutSeconds = workerQuitTimeoutSeconds;
    }

    public boolean isLog2Stdout() {
        return log2Stdout;
    }

    public void setLog2Stdout(boolean log2Stdout) {
        this.log2Stdout = log2Stdout;
    }

    // ==================== 配置验证 ====================
    /**
     * 配置验证方法
     *
     * 在Spring Boot启动时自动调用，验证配置参数的有效性
     * 对应Go版本main.go中的环境变量验证逻辑
     *
     * 验证规则：
     * 1. AGORA_APP_ID可以为空，在请求时动态验证
     * 2. 如果AGORA_APP_ID不为空，则必须为32位字符串
     * 3. WORKERS_MAX必须大于0
     * 4. WORKER_QUIT_TIMEOUT_SECONDES必须大于0
     * 5. LOG_PATH目录必须存在或可创建
     *
     * @throws Exception 当配置验证失败时抛出异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 验证AGORA_APP_ID - 对应Go版本的验证逻辑
        // 允许AGORA_APP_ID为空，在请求时动态验证
        if (StringUtils.isNoneBlank(appId) && appId.length() != 32) {
            logger.error("environment AGORA_APP_ID invalid - 必须是32位字符串");
            throw new RuntimeException("environment AGORA_APP_ID invalid - 必须是32位字符串");
        }

        // 验证WORKERS_MAX - 对应Go版本的验证逻辑
        if (workersMax <= 0) {
            logger.error("environment WORKERS_MAX invalid - 必须大于0");
            throw new RuntimeException("environment WORKERS_MAX invalid - 必须大于0");
        }

        // 验证WORKER_QUIT_TIMEOUT_SECONDES - 对应Go版本的验证逻辑
        if (workerQuitTimeoutSeconds <= 0) {
            logger.error("environment WORKER_QUIT_TIMEOUT_SECONDES invalid - 必须大于0");
            throw new RuntimeException("environment WORKER_QUIT_TIMEOUT_SECONDES invalid - 必须大于0");
        }

        // 验证LOG_PATH - 对应Go版本的目录创建逻辑
        if (logPath != null && !logPath.isEmpty()) {
            java.io.File logDir = new java.io.File(logPath);
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    logger.error("create log directory failed - 无法创建日志目录: {}", logPath);
                    throw new RuntimeException("create log directory failed - 无法创建日志目录: " + logPath);
                }
                logger.info("成功创建日志目录: {}", logPath);
            }
        }

        logger.info("服务器配置验证成功 - AppId: {}, WorkersMax: {}, WorkerTimeout: {}秒",
                appId, workersMax, workerQuitTimeoutSeconds);
    }
}