package com.agora.tenframework.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API响应模型类
 *
 * 该类定义了TEN Framework API的统一响应格式，包括：
 *
 * 核心功能：
 * 1. 响应码管理 - 统一的响应状态码
 * 2. 响应消息管理 - 统一的响应消息格式
 * 3. 响应数据管理 - 泛型支持不同类型的响应数据
 * 4. 请求跟踪管理 - 通过requestId跟踪请求状态
 *
 * 响应格式：
 * - code: 响应状态码（0表示成功，其他表示错误）
 * - msg: 响应消息
 * - data: 响应数据（泛型）
 * - request_id: 请求ID（用于跟踪）
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的ApiResponse结构体
 * - 保持相同的JSON字段名称和响应格式
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class ApiResponse<T> {

    /**
     * 响应状态码
     * 0表示成功，其他值表示错误
     */
    @JsonProperty("code")
    private String code;

    /**
     * 响应消息
     * 描述响应结果的文本信息
     */
    @JsonProperty("msg")
    private String message;

    /**
     * 响应数据
     * 泛型支持不同类型的响应数据
     */
    @JsonProperty("data")
    private T data;

    /**
     * 请求ID
     * 用于跟踪和标识请求
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 默认构造函数
     */
    public ApiResponse() {
    }

    /**
     * 构造函数
     *
     * @param code      响应状态码
     * @param message   响应消息
     * @param data      响应数据
     * @param requestId 请求ID
     */
    public ApiResponse(String code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    /**
     * 创建成功响应
     *
     * @param data      响应数据
     * @param requestId 请求ID
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("0", "success", data, requestId);
    }

    /**
     * 创建成功响应（无请求ID）
     *
     * @param data 响应数据
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, null);
    }

    /**
     * 创建错误响应
     *
     * @param code      错误码
     * @param message   错误消息
     * @param requestId 请求ID
     * @return 错误响应对象
     */
    public static <T> ApiResponse<T> error(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }

    /**
     * 创建错误响应（无请求ID）
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 错误响应对象
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    // ==================== Getter和Setter方法 ====================
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}