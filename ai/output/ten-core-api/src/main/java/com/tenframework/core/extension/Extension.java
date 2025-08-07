package com.tenframework.core.extension;

import com.tenframework.core.extension.AsyncExtensionEnv;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.message.CommandResult;

/**
 * TEN框架的Extension接口
 * 定义了Extension的生命周期回调和消息处理方法
 * 对应C语言中的ten_extension_t部分功能
 */
public interface Extension {

    /**
     * 配置阶段：用于读取和验证Extension的配置
     *
     * @param context Extension上下文
     */
    default void onConfigure(AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 初始化阶段：用于分配Extension运行所需的各类资源
     *
     * @param context Extension上下文
     */
    default void onInit(AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 启动阶段：Extension的核心工作开始执行
     *
     * @param context Extension上下文
     */
    default void onStart(AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 停止阶段：优雅地停止Extension正在进行的工作
     *
     * @param context Extension上下文
     */
    default void onStop(AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 清理阶段：释放Extension在onInit阶段分配的所有资源
     *
     * @param context Extension上下文
     */
    default void onDeinit(AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 处理命令消息
     *
     * @param command 命令消息
     * @param context Extension上下文
     */
    default void onCommand(Command command, AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 处理通用数据消息
     *
     * @param data    数据消息
     * @param context Extension上下文
     */
    default void onData(Data data, AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 处理音频帧消息
     *
     * @param audioFrame 音频帧消息
     * @param context    Extension上下文
     */
    default void onAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 处理视频帧消息
     *
     * @param videoFrame 视频帧消息
     * @param context    Extension上下文
     */
    default void onVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 处理命令结果消息。
     *
     * @param commandResult 命令结果消息
     * @param context       Extension上下文
     */
    default void onCommandResult(CommandResult commandResult, AsyncExtensionEnv context) {
        // 默认实现为空，Extension可以选择性实现
    }

    /**
     * 获取Extension的应用URI
     */
    String getAppUri();
}