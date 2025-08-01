package com.agora.tenframework.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Worker Update Request Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class WorkerUpdateReq {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("collection")
    private String collection;

    @JsonProperty("filename")
    private String fileName;

    @JsonProperty("path")
    private String path;

    @JsonProperty("ten")
    private WorkerUpdateReqTen ten;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public WorkerUpdateReqTen getTen() {
        return ten;
    }

    public void setTen(WorkerUpdateReqTen ten) {
        this.ten = ten;
    }

    public static class WorkerUpdateReqTen {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}