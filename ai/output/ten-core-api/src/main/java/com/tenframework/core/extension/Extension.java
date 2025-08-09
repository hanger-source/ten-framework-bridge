package com.tenframework.core.extension;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.AddExtensionToGraphCommand;
import com.tenframework.core.message.command.CloseAppCommand;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.RemoveExtensionFromGraphCommand;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.message.command.StopGraphCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.message.command.TimeoutCommand;

/**
 * `Extension` 接口定义了 ten-framework 中 Extension 的生命周期回调和消息处理方法。
 * Extension 是 Engine 内部的业务处理单元，通过 `AsyncExtensionEnv` 与 Engine 交互。
 * 它对应 C 语言中的 `ten_extension_t`。
 */
public interface Extension {

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
        // 根据具体的命令类型分发到更细粒度的处理方法
        switch (command.getType()) {
            case CMD_START_GRAPH:
                onStartGraphCommand((StartGraphCommand) command, env);
                break;
            case CMD_STOP_GRAPH:
                onStopGraphCommand((StopGraphCommand) command, env);
                break;
            case CMD_ADD_EXTENSION_TO_GRAPH:
                onAddExtensionToGraphCommand((AddExtensionToGraphCommand) command, env);
                break;
            case CMD_REMOVE_EXTENSION_FROM_GRAPH:
                onRemoveExtensionFromGraphCommand((RemoveExtensionFromGraphCommand) command, env);
                break;
            case CMD_TIMER:
                onTimerCommand((TimerCommand) command, env);
                break;
            case CMD_TIMEOUT:
                onTimeoutCommand((TimeoutCommand) command, env);
                break;
            case CMD_CLOSE_APP:
                onCloseAppCommand((CloseAppCommand) command, env);
                break;
            default:
                env.getVirtualThreadExecutor()
                        .execute(() -> env.sendResult(CommandResult.fail(command.getId(), "Unsupported Command Type")));
                break;
        }
    }

    /**
     * 处理 `StartGraphCommand` 命令。
     * 
     * @param command `StartGraphCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onStartGraphCommand(StartGraphCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env.sendResult(
                CommandResult.fail(command.getId(), "StartGraphCommand not supported for this extension.")));
    }

    /**
     * 处理 `StopGraphCommand` 命令。
     * 
     * @param command `StopGraphCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onStopGraphCommand(StopGraphCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env
                .sendResult(CommandResult.fail(command.getId(), "StopGraphCommand not supported for this extension.")));
    }

    /**
     * 处理 `AddExtensionToGraphCommand` 命令。
     * 
     * @param command `AddExtensionToGraphCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onAddExtensionToGraphCommand(AddExtensionToGraphCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env.sendResult(
                CommandResult.fail(command.getId(), "AddExtensionToGraphCommand not supported for this extension.")));
    }

    /**
     * 处理 `RemoveExtensionFromGraphCommand` 命令。
     * 
     * @param command `RemoveExtensionFromGraphCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onRemoveExtensionFromGraphCommand(RemoveExtensionFromGraphCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env.sendResult(CommandResult.fail(command.getId(),
                "RemoveExtensionFromGraphCommand not supported for this extension.")));
    }

    /**
     * 处理 `TimerCommand` 命令。
     * 
     * @param command `TimerCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onTimerCommand(TimerCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env
                .sendResult(CommandResult.fail(command.getId(), "TimerCommand not supported for this extension.")));
    }

    /**
     * 处理 `TimeoutCommand` 命令。
     * 
     * @param command `TimeoutCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onTimeoutCommand(TimeoutCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env
                .sendResult(CommandResult.fail(command.getId(), "TimeoutCommand not supported for this extension.")));
    }

    /**
     * 处理 `CloseAppCommand` 命令。
     * 
     * @param command `CloseAppCommand` 消息。
     * @param env     Extension 的运行时环境。
     */
    default void onCloseAppCommand(CloseAppCommand command, AsyncExtensionEnv env) {
        env.getVirtualThreadExecutor().execute(() -> env
                .sendResult(CommandResult.fail(command.getId(), "CloseAppCommand not supported for this extension.")));
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