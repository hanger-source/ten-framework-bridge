package com.tenframework.core.extension;

import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Message;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * ExtensionContext接口
 * 作为Extension与Engine交互的桥梁，提供Extension所需的核心功能
 * 对应C语言中ten_env_t的部分功能
 */
public interface ExtensionContext {

    /**
     * 向Engine发送消息，由Engine进行后续路由和分发
     * Extension可以通过此方法向其他Extension发送Command或Data消息
     *
     * @param message 要发送的消息
     * @return true如果消息成功提交到Engine队列，否则false
     */
    boolean sendMessage(Message message);

    /**
     * 返回命令执行结果给Engine，Engine会负责将结果回溯给原始调用方
     *
     * @param result 命令结果
     * @return true如果结果成功提交到Engine队列，否则false
     */
    boolean sendResult(CommandResult result);

    /**
     * 获取当前Extension实例的配置属性，支持类型转换
     *
     * @param key  属性键
     * @param type 属性的期望类型
     * @param <T>  属性的泛型类型
     * @return 包含属性值的Optional，如果不存在则为Optional.empty()
     */
    <T> Optional<T> getProperty(String key, Class<T> type);

    /**
     * 获取Extension用于执行非阻塞异步操作的虚拟线程ExecutorService
     * Extension内部如果涉及阻塞I/O或耗时计算，应使用此ExecutorService
     *
     * @return 虚拟线程ExecutorService实例
     */
    ExecutorService getVirtualThreadExecutor();

    /**
     * 获取Extension的名称
     *
     * @return Extension名称
     */
    String getExtensionName();

    /**
     * 获取Extension所属的Graph ID
     *
     * @return Graph ID
     */
    String getGraphId();
}