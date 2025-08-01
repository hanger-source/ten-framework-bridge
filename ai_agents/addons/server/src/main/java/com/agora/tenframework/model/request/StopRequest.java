package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stop Request Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class StopRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("channel_name")
    private String channelName;

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
}