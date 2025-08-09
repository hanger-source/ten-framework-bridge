package com.tenframework.core.extension;

import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * `AsyncExtensionEnv` 接口定义了 Extension 与 Engine 异步交互的核心 API。
 * 它是 `ten_env_t` 在 Java 中的对等概念，允许 Extension 发送消息、命令、操作属性等。
 */
public interface AsyncExtensionEnv {
    /**
     * 发送一个命令结果消息。
     *
     * @param result 要发送的命令结果。
     */
    void sendResult(CommandResult result);

    /**
     * 发送一个数据消息。
     *
     * @param data 要发送的数据消息。
     * @return 如果消息成功提交则返回 true，否则返回 false。
     */
    default boolean sendData(DataMessage data) {
        return sendMessage(data);
    }

    /**
     * 发送一个视频帧消息。
     *
     * @param videoFrame 要发送的视频帧。
     * @return 如果消息成功提交则返回 true，否则返回 false。
     */
    default boolean sendVideoFrame(VideoFrameMessage videoFrame) {
        return sendMessage(videoFrame);
    }

    /**
     * 发送一个音频帧消息。
     *
     * @param audioFrame 要发送的音频帧。
     * @return 如果消息成功提交则返回 true，否则返回 false。
     */
    default boolean sendAudioFrame(AudioFrameMessage audioFrame) {
        return sendMessage(audioFrame);
    }

    /**
     * 发送一个通用消息。
     *
     * @param message 要发送的消息。
     * @return 如果消息成功提交则返回 true，否则返回 false。
     */
    boolean sendMessage(Message message);

    /**
     * 发送一个命令。
     *
     * @param command 要发送的命令。
     * @return 返回一个 `CompletableFuture`，当命令处理完成时，该 Future 将被完成，包含命令执行的结果。
     */
    CompletableFuture<Object> sendCommand(Command command);

    /**
     * 获取指定路径的属性值。
     *
     * @param path 属性路径。
     * @return 属性值的 Optional，如果不存在则为 Optional.empty()。
     */
    Optional<Object> getProperty(String path);

    /**
     * 设置指定路径的属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的值。
     */
    void setProperty(String path, Object value);

    /**
     * 检查指定路径的属性是否存在。
     *
     * @param path 属性路径。
     * @return 如果属性存在则返回 true，否则返回 false。
     */
    boolean hasProperty(String path);

    /**
     * 删除指定路径的属性。
     *
     * @param path 属性路径。
     */
    void deleteProperty(String path);

    /**
     * 获取指定路径的整数属性值。
     *
     * @param path 属性路径。
     * @return 整数值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<Integer> getPropertyInt(String path);

    /**
     * 设置指定路径的整数属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的整数值。
     */
    void setPropertyInt(String path, int value);

    /**
     * 获取指定路径的长整数属性值。
     *
     * @param path 属性路径。
     * @return 长整数值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<Long> getPropertyLong(String path);

    /**
     * 设置指定路径的长整数属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的长整数值。
     */
    void setPropertyLong(String path, long value);

    /**
     * 获取指定路径的字符串属性值。
     *
     * @param path 属性路径。
     * @return 字符串值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<String> getPropertyString(String path);

    /**
     * 设置指定路径的字符串属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的字符串值。
     */
    void setPropertyString(String path, String value);

    /**
     * 获取指定路径的布尔属性值。
     *
     * @param path 属性路径。
     * @return 布尔值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<Boolean> getPropertyBool(String path);

    /**
     * 设置指定路径的布尔属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的布尔值。
     */
    void setPropertyBool(String path, boolean value);

    /**
     * 获取指定路径的双精度浮点数属性值。
     *
     * @param path 属性路径。
     * @return 双精度浮点数值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<Double> getPropertyDouble(String path);

    /**
     * 设置指定路径的双精度浮点数属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的双精度浮点数值。
     */
    void setPropertyDouble(String path, double value);

    /**
     * 获取指定路径的浮点数属性值。
     *
     * @param path 属性路径。
     * @return 浮点数值的 Optional，如果不存在或类型不匹配则为 Optional.empty()。
     */
    Optional<Float> getPropertyFloat(String path);

    /**
     * 设置指定路径的浮点数属性值。
     *
     * @param path  属性路径。
     * @param value 要设置的浮点数值。
     */
    void setPropertyFloat(String path, float value);

    /**
     * 从 JSON 字符串初始化所有属性。
     *
     * @param jsonStr 包含属性的 JSON 字符串。
     */
    void initPropertyFromJson(String jsonStr);

    /**
     * 获取用于执行虚拟线程任务的 ExecutorService。
     *
     * @return ExecutorService 实例。
     */
    ExecutorService getVirtualThreadExecutor();

    /**
     * 获取 Extension 的名称。
     *
     * @return Extension 的名称。
     */
    String getExtensionName();

    /**
     * 获取所属 App 的 URI。
     *
     * @return App 的 URI。
     */
    String getAppUri();

    /**
     * 获取所属 Graph (Engine) 的 ID。
     *
     * @return Graph (Engine) 的 ID。
     */
    String getGraphId();

    /**
     * 获取当前活跃的虚拟线程数量。
     *
     * @return 活跃虚拟线程数量。
     */
    int getActiveVirtualThreadCount();

    /**
     * 关闭此环境，释放相关资源。
     */
    void close();
}