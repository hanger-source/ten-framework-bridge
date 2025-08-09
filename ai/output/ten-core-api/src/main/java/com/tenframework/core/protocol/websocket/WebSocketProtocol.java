package com.tenframework.core.protocol.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.Message;
import com.tenframework.core.protocol.Protocol;

import java.io.IOException;

// WebSocketProtocol 实现了 Protocol 接口，用于处理 WebSocket 消息的编码和解码
public class WebSocketProtocol implements Protocol {

    private final ObjectMapper objectMapper; // 使用 Jackson 进行 JSON 序列化和反序列化

    public WebSocketProtocol() {
        this.objectMapper = new ObjectMapper();
        // 根据需要配置 ObjectMapper，例如：
        // objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public byte[] encode(Message message) {
        try {
            // 将 Message 对象序列化为 JSON 字节数组
            return objectMapper.writeValueAsBytes(message);
        } catch (IOException e) {
            // TODO: 日志记录和错误处理
            throw new RuntimeException("Failed to encode message: " + e.getMessage(), e);
        }
    }

    @Override
    public Message decode(byte[] data) {
        try {
            // 将 JSON 字节数组反序列化为 Message 对象
            // 需要注意 Message 是抽象类，Jackson 需要 @JsonSubTypes 来识别具体子类
            return objectMapper.readValue(data, Message.class);
        } catch (IOException e) {
            // TODO: 日志记录和错误处理
            throw new RuntimeException("Failed to decode message: " + e.getMessage(), e);
        }
    }

    @Override
    public void handshake() {
        // WebSocket 握手逻辑（在实际应用中通常由 WebSocket 框架处理）
        System.out.println("Performing WebSocket handshake...");
    }

    @Override
    public void cleanup() {
        // 清理 WebSocket 协议相关的资源
        System.out.println("Cleaning up WebSocket protocol resources...");
    }
}