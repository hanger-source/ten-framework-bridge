package com.agora.tenframework.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Worker Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Worker {

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("http_server_port")
    private Integer httpServerPort;

    @JsonProperty("log_file")
    private String logFile;

    @JsonProperty("log2_stdout")
    private boolean log2Stdout;

    @JsonProperty("property_json_file")
    private String propertyJsonFile;

    @JsonProperty("graph_name")
    private String graphName;

    @JsonProperty("pid")
    private Integer pid;

    @JsonProperty("quit_timeout_seconds")
    private Integer quitTimeoutSeconds;

    @JsonProperty("create_ts")
    private Long createTs;

    @JsonProperty("update_ts")
    private Long updateTs;

    public Worker() {
        this.createTs = Instant.now().getEpochSecond();
        this.updateTs = Instant.now().getEpochSecond();
        this.quitTimeoutSeconds = 60;
    }

    // Getters and Setters
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