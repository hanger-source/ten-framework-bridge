package com.tenframework.core.extension;

import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrame;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * AsyncExtensionEnv接口定义了Extension与Engine交互的抽象层。
 * 作为Extension与Engine交互的桥梁，提供Extension所需的核心功能
 * 对应C语言中ten_env_t的部分功能，并与Python TenEnv的语义对齐
 */
public interface AsyncExtensionEnv {

    /**
     * Extension向Engine发送命令结果
     *
     * @param result 要发送的命令结果对象
     */
    void sendResult(CommandResult result);

    /**
     * Extension向Engine发送数据消息
     *
     * @param data 要发送的数据对象
     */
    void sendData(Data data);

    /**
     * Extension向Engine发送视频帧消息
     *
     * @param videoFrame 要发送的视频帧对象
     */
    void sendVideoFrame(VideoFrame videoFrame);

    /**
     * Extension向Engine发送音频帧消息
     *
     * @param audioFrame 要发送的音频帧对象
     */
    void sendAudioFrame(AudioFrame audioFrame);

    /**
     * Extension向Engine发送一个命令，并异步获取其执行结果。
     * 此方法与Python ten_env.send_cmd的语义对齐，允许Extension发起命令并
     * 通过CompletableFuture异步获取命令的执行结果（即CommandResult中包含的业务数据）。
     *
     * @param command 要发送的命令对象。
     * @return 一个CompletableFuture，当命令执行完成并返回结果时，将包含实际的业务数据。
     */
    CompletableFuture<Object> sendCommand(Command command);

    // region Property Operations (与Python
    // ten_env.py中的get_property_*和set_property_*对齐)

    /**
     * 检查给定路径的属性是否存在。
     *
     * @param path 属性路径。
     * @return 如果属性存在则返回true，否则返回false。
     */
    boolean isPropertyExist(String path);

    /**
     * 获取指定路径的属性值并尝试转换为JSON字符串。
     *
     * @param path 属性路径。
     * @return 属性值的JSON字符串表示，如果属性不存在或无法转换为JSON则返回Optional.empty()。
     */
    Optional<String> getPropertyToJson(String path);

    /**
     * 从JSON字符串设置指定路径的属性值。
     *
     * @param path    属性路径。
     * @param jsonStr 要设置的属性值的JSON字符串表示。
     */
    void setPropertyFromJson(String path, String jsonStr);

    /**
     * 获取指定路径的整数属性值。
     *
     * @param path 属性路径。
     * @return 整数属性值，如果属性不存在或类型不匹配则返回Optional.empty()。
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
     * 获取指定路径的字符串属性值。
     *
     * @param path 属性路径。
     * @return 字符串属性值，如果属性不存在或类型不匹配则返回Optional.empty()。
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
     * @return 布尔属性值，如果属性不存在或类型不匹配则返回Optional.empty()。
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
     * 获取指定路径的浮点数属性值。
     *
     * @param path 属性路径。
     * @return 浮点数属性值，如果属性不存在或类型不匹配则返回Optional.empty()。
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
     * 从JSON字符串初始化ExtensionEnv的属性。
     *
     * @param jsonStr 包含属性的JSON字符串。
     */
    void initPropertyFromJson(String jsonStr);

    // endregion Property Operations

    /**
     * 获取ExtensionEnv所关联的虚拟线程ExecutorService。
     * Extension可以使用此ExecutorService在虚拟线程中执行异步任务，
     * 避免阻塞Engine的主线程。
     *
     * @return 虚拟线程ExecutorService实例
     */
    ExecutorService getVirtualThreadExecutor();

    /**
     * 获取当前Extension的名称。
     *
     * @return Extension的名称
     */
    String getExtensionName();

    /**
     * 获取当前Extension所属的App URI。
     *
     * @return App URI
     */
    String getAppUri();

    /**
     * 获取当前Extension所属的Graph ID。
     *
     * @return Graph ID
     */
    String getGraphId();

    /**
     * 获取Extension内部虚拟线程执行器中当前活跃的任务数量。
     * 这代表了正在执行的非阻塞异步操作的数量。
     *
     * @return 活跃的虚拟线程任务数量
     */
    int getActiveVirtualThreadCount();
}