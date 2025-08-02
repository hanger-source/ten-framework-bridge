package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 向量文档更新请求模型类
 *
 * 该类定义了更新向量文档的请求数据结构，包括：
 *
 * 核心功能：
 * 1. 请求标识管理 - 通过requestId跟踪请求状态
 * 2. 频道信息管理 - 指定要更新向量文档的Worker所属频道
 * 3. 集合信息管理 - 指定向量数据库中的集合名称
 * 4. 文件信息管理 - 指定要处理的文件名
 *
 * 使用场景：
 * - 向量数据库更新 - 更新Worker的向量数据库配置
 * - 文档索引更新 - 更新向量文档的索引信息
 * - 查询配置更新 - 更新向量查询的相关配置
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的VectorDocumentUpdate结构体
 * - 保持相同的JSON字段名称和数据结构
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class VectorDocumentUpdate {

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 频道名称
     * 指定要更新向量文档的Worker所属频道
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
     * 要处理的向量文档文件名
     */
    @JsonProperty("file_name")
    private String fileName;

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
}