package com.agora.tenframework.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.multipart.MultipartFile;

/**
 * 向量文档上传请求模型类
 *
 * 该类定义了上传向量文档的请求数据结构，包括：
 *
 * 核心功能：
 * 1. 请求标识管理 - 通过requestId跟踪请求状态
 * 2. 频道信息管理 - 指定要上传向量文档的Worker所属频道
 * 3. 文件上传管理 - 处理上传的向量文档文件
 *
 * 使用场景：
 * - 向量文档上传 - 上传向量文档到Worker
 * - 文件处理 - 处理上传的文档文件
 * - 向量索引构建 - 为上传的文档构建向量索引
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的VectorDocumentUpload结构体
 * - 保持相同的JSON字段名称和数据结构
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class VectorDocumentUpload {

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 频道名称
     * 指定要上传向量文档的Worker所属频道
     */
    @JsonProperty("channel_name")
    private String channelName;

    /**
     * 上传的文件
     * 要上传的向量文档文件
     */
    private MultipartFile file;

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

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}