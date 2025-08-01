package com.agora.tenframework.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API Response Model
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class ApiResponse<T> {

    @JsonProperty("code")
    private String code;

    @JsonProperty("msg")
    private String message;

    @JsonProperty("data")
    private T data;

    @JsonProperty("request_id")
    private String requestId;

    public ApiResponse() {
    }

    public ApiResponse(String code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("0", "success", data, requestId);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    // Getters and Setters
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