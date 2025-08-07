package com.tenframework.core.engine;

import com.tenframework.core.message.Message;

/**
 * 消息提交器接口，定义了向Engine提交消息的方法。
 * 用于解耦EngineAsyncExtensionEnv和Engine的直接依赖。
 */
public interface MessageSubmitter {
    /**
     * 向Engine提交消息（非阻塞）
     *
     * @param message   要处理的消息
     * @param channelId 可选的Channel ID，如果消息来自特定Channel
     * @return true如果成功提交，false如果队列已满
     */
    void submitMessage(Message message, String channelId);

    /**
     * 向Engine提交消息（非阻塞）
     *
     * @param message 要处理的消息
     * @return true如果成功提交，false如果队列已满
     */
    default void submitMessage(Message message) {
        submitMessage(message, null);
    }
}