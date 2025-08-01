package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Start Request Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class StartRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("graph_name")
    private String graphName;

    @JsonProperty("user_uid")
    private Long remoteStreamId;

    @JsonProperty("bot_uid")
    private Long botStreamId;

    @JsonProperty("token")
    private String token;

    @JsonProperty("worker_http_server_port")
    private Integer workerHttpServerPort;

    @JsonProperty("properties")
    private Map<String, Map<String, Object>> properties;

    @JsonProperty("timeout")
    private Integer quitTimeoutSeconds;

    // Getters and Setters
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