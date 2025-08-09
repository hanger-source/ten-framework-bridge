package com.tenframework.core.engine;

import com.tenframework.core.message.Message;
import com.tenframework.core.extension.ExtensionContext; // 引入 ExtensionContext

/**
 * 负责将消息分发到Engine内部的Extension。
 * 解耦Engine的核心消息循环与Extension的具体调用逻辑。
 * 现在它主要作为 Engine 与 ExtensionContext 之间消息分发的桥梁。
 */
public interface ExtensionMessageDispatcher {

    /**
     * 分发通用消息到对应的Extension。
     *
     * @param message 待分发的消息
     */
    void dispatchMessage(Message message);

    // 移除所有具体的 dispatch 方法，统一为 dispatchMessage
    // void dispatchCommand(CommandMessage command, Engine engine);
    // void dispatchData(DataMessage data, Engine engine);
    // void dispatchVideoFrame(VideoFrameMessage videoFrame, Engine engine);
    // void dispatchAudioFrame(AudioFrameMessage audioFrame, Engine engine);
}