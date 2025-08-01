package com.agora.tenframework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server Configuration
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "ten.server")
public class ServerConfig implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    private String appId;
    private String appCertificate;
    private String logPath;
    private String port;
    private int workersMax = 10;
    private int workerQuitTimeoutSeconds = 60;
    private boolean log2Stdout = false;

    // Getters and Setters
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

    /**
     * Validate configuration after properties are set - equivalent to Go's
     * environment validation
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // Validate AGORA_APP_ID - equivalent to Go's validation
        if (appId == null || appId.length() != 32) {
            logger.error("environment AGORA_APP_ID invalid");
            throw new RuntimeException("environment AGORA_APP_ID invalid");
        }

        // Validate WORKERS_MAX - equivalent to Go's validation
        if (workersMax <= 0) {
            logger.error("environment WORKERS_MAX invalid");
            throw new RuntimeException("environment WORKERS_MAX invalid");
        }

        // Validate WORKER_QUIT_TIMEOUT_SECONDES - equivalent to Go's validation
        if (workerQuitTimeoutSeconds <= 0) {
            logger.error("environment WORKER_QUIT_TIMEOUT_SECONDES invalid");
            throw new RuntimeException("environment WORKER_QUIT_TIMEOUT_SECONDES invalid");
        }

        // Validate LOG_PATH - equivalent to Go's directory creation
        if (logPath != null && !logPath.isEmpty()) {
            java.io.File logDir = new java.io.File(logPath);
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    logger.error("create log directory failed");
                    throw new RuntimeException("create log directory failed");
                }
            }
        }

        logger.info("Server configuration validated successfully");
    }
}