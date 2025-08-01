package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vector Document Update Request Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class VectorDocumentUpdate {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("collection")
    private String collection;

    @JsonProperty("file_name")
    private String fileName;

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

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}