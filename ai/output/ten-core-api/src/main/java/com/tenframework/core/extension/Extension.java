package com.tenframework.core.extension;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;

/**
 * `Extension` 接口定义了 ten-framework 中 Extension 的生命周期回调和消息处理方法。
 * Extension 是 Engine 内部的业务处理单元，通过 `AsyncExtensionEnv` 与 Engine 交互。
 * 它对应 C 语言中的 `ten_extension_t`。
 */
public interface Extension {

    String getExtensionName();
    /**
     * Extension 配置回调方法。
     *
     * @param env Extension 的运行时环境。
     */
    default void onConfigure(AsyncExtensionEnv env) {
    }

    /**
     * Extension 初始化回调方法。
     *
     * @param env Extension 的运行时环境。
     */
    default void onInit(AsyncExtensionEnv env) {
    }

    /**
     * Extension 启动回调方法。
     *
     * @param env Extension 的运行时环境。
     */
    default void onStart(AsyncExtensionEnv env) {
    }

    /**
     * Extension 停止回调方法。
     *
     * @param env Extension 的运行时环境。
     */
    default void onStop(AsyncExtensionEnv env) {
    }

    /**
     * Extension 去初始化回调方法。
     *
     * @param env Extension 的运行时环境。
     */
    default void onDeinit(AsyncExtensionEnv env) {
    }

    /**
     * 处理命令消息。
     *
     * @param command 命令消息。
     * @param env     Extension 的运行时环境。
     */
    default void onCommand(Command command, AsyncExtensionEnv env) {
        // Extension 级别的命令处理，通常由具体的 Extension 实现根据 command 的名称或特定属性进行处理。
        // 这里提供一个默认的“不支持”实现，如果具体 Extension 不覆盖此方法，则表示它不支持该命令。
        env.getVirtualThreadExecutor()
                .execute(() -> env
                        .sendResult(CommandResult.fail(command.getId(), "Extension does not support this command.")));
    }

    /**
     * 处理数据消息。
     *
     * @param data 数据消息。
     * @param env  Extension 的运行时环境。
     */
    default void onData(DataMessage data, AsyncExtensionEnv env) {
    }

    /**
     * 处理音频帧消息。
     *
     * @param audioFrame 音频帧消息。
     * @param env        Extension 的运行时环境。
     */
    default void onAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
    }

    /**
     * 处理视频帧消息。
     *
     * @param videoFrame 视频帧消息。
     * @param env        Extension 的运行时环境。
     */
    default void onVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
    }

    /**
     * 处理命令结果消息。
     *
     * @param commandResult 命令结果消息。
     * @param env           Extension 的运行时环境。
     */
    default void onCommandResult(CommandResult commandResult, AsyncExtensionEnv env) {
    }

    /**
     * 获取 Extension 所属的 App URI。
     *
     * @return App URI。
     */
    String getAppUri();
}