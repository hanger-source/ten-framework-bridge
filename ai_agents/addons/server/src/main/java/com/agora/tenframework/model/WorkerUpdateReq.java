package com.agora.tenframework.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Worker更新请求模型类
 *
 * 该类定义了更新Worker进程配置的请求数据结构，包括：
 *
 * 核心功能：
 * 1. 请求标识管理 - 通过requestId跟踪请求状态
 * 2. 频道信息管理 - 指定要更新的频道名称
 * 3. 文件信息管理 - 管理上传的文件信息（集合、文件名、路径）
 * 4. TEN指令管理 - 通过ten字段指定要执行的TEN指令
 *
 * 使用场景：
 * - 向量文档更新 - 更新Worker的向量数据库配置
 * - 文件上传处理 - 处理上传的文件并更新Worker配置
 * - 动态配置更新 - 运行时更新Worker的配置参数
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的WorkerUpdateReq结构体
 * - 保持相同的JSON字段名称和数据结构
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class WorkerUpdateReq {

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 频道名称
     * 指定要更新的Worker所属频道
     */
    @JsonProperty("channel_name")
    private String channelName;

    /**
     * 集合名称
     * 向量数据库中的集合名称
     */
    @JsonProperty("collection")
    private String collection;

    /**
     * 文件名
     * 上传文件的名称
     */
    @JsonProperty("filename")
    private String fileName;

    /**
     * 文件路径
     * 上传文件在服务器上的存储路径
     */
    @JsonProperty("path")
    private String path;

    /**
     * TEN指令配置
     * 指定要执行的TEN指令类型和参数
     */
    @JsonProperty("ten")
    private WorkerUpdateReqTen ten;

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

    /**
     * TEN指令配置内部类
     *
     * 定义了TEN指令的名称和类型
     * 对应Go版本的WorkerUpdateReqTen结构体
     */
    public static class WorkerUpdateReqTen {
        /**
         * 指令名称
         * 指定要执行的TEN指令名称（如update_querying_collection、file_chunk等）
         */
        @JsonProperty("name")
        private String name;

        /**
         * 指令类型
         * 指定指令的类型（如cmd、event等）
         */
        @JsonProperty("type")
        private String type;

        // ==================== Getter和Setter方法 ====================
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