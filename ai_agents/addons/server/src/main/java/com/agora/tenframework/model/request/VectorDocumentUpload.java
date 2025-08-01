package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.multipart.MultipartFile;

/**
 * Vector Document Upload Request Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class VectorDocumentUpload {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("channel_name")
    private String channelName;

    private MultipartFile file;

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

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}