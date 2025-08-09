package com.tenframework.core.protocol;

import com.tenframework.core.message.Message;

// Protocol 接口定义了数据协议层应具备的能力
public interface Protocol {

    // 编码消息，将内部 Message 对象转换为字节数组或特定协议格式
    byte[] encode(Message message);

    // 解码数据，将字节数组或特定协议格式转换为内部 Message 对象
    Message decode(byte[] data);

    // 协议握手或初始化操作
    void handshake();

    // 清理协议相关的资源
    void cleanup();
}