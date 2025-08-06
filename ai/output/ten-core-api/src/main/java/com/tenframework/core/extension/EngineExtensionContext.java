package com.tenframework.core.extension;

import com.tenframework.core.Location;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Message;
import com.tenframework.core.path.PathOut;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * EngineExtensionContext类
 * ExtensionContext接口的具体实现，作为Extension与Engine交互的桥梁
 * 提供Extension所需的核心功能，并封装了与Engine的通信细节及虚拟线程执行器
 */
@Slf4j
public class EngineExtensionContext implements ExtensionContext {

    private final String extensionName;
    private final String graphId;
    private final String appUri;
    private final Engine engine;
    private final Map<String, Object> properties;
    private final ExecutorService virtualThreadExecutor; // 虚拟线程ExecutorService
    private final Extension extension; // 新增字段，存储关联的Extension实例

    public EngineExtensionContext(String extensionName, String appUri, Engine engine, Map<String, Object> properties,
            Extension extension) { // 增加 Extension 参数
        this.extensionName = extensionName;
        this.appUri = appUri;
        this.engine = engine;
        this.properties = properties;
        this.graphId = UUID.randomUUID().toString(); // 每个ExtensionContext拥有独立的graphId
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.extension = extension; // 初始化 Extension 实例
        log.debug("EngineExtensionContext创建成功: extensionName={}, graphId={}", extensionName, graphId);
    }

    @Override
    public boolean sendMessage(Message message) {
        if (message == null) {
            log.warn("尝试发送空消息: extensionName={}, graphId={}", extensionName, graphId);
            return false;
        }

        // 验证消息完整性
        if (!message.checkIntegrity()) {
            log.warn("消息完整性检查失败: extensionName={}, graphId={}, messageType={}, messageName={}",
                    extensionName, graphId, message.getType(), message.getName());
            return false;
        }

        // 禁止发送CommandResult类型的消息，应该使用sendResult方法
        if (message instanceof CommandResult) {
            log.warn("禁止通过sendMessage发送CommandResult，请使用sendResult方法: extensionName={}, graphId={}",
                    extensionName, graphId);
            return false;
        }

        // 设置消息的源位置（如果未设置）
        if (message.getSourceLocation() == null) {
            Location sourceLocation = new Location("", graphId, extensionName);
            message.setSourceLocation(sourceLocation);
        }

        // 消息发送后会回到Engine的inboundMessageQueue进行处理
        boolean success = engine.submitMessage(message);
        if (!success) {
            log.warn("Extension发送消息失败，Engine队列已满: extensionName={}, graphId={}, messageType={}",
                    extensionName, graphId, message.getType());
        } else {
            log.debug("Extension消息发送成功: extensionName={}, graphId={}, messageType={}, messageName={}",
                    extensionName, graphId, message.getType(), message.getName());
        }
        return success;
    }

    @Override
    public boolean sendResult(CommandResult result) {
        if (result == null) {
            log.warn("尝试发送空命令结果: extensionName={}, graphId={}", extensionName, graphId);
            return false;
        }

        // 验证命令结果完整性
        if (!result.checkIntegrity()) {
            log.warn("命令结果完整性检查失败: extensionName={}, graphId={}, commandId={}",
                    extensionName, graphId, result.getCommandId());
            return false;
        }

        // 验证命令ID不为空
        if (result.getCommandId() == null || result.getCommandId().isEmpty()) {
            log.warn("命令结果缺少有效的commandId: extensionName={}, graphId={}",
                    extensionName, graphId);
            return false;
        }

        // 设置命令结果的源位置（如果未设置）
        if (result.getSourceLocation() == null) {
            Location sourceLocation = new Location("", graphId, extensionName);
            result.setSourceLocation(sourceLocation);
        }

        // 命令结果本质上也是一种消息，回溯到Engine的inboundMessageQueue处理
        boolean success = engine.submitMessage(result);
        if (!success) {
            log.warn("Extension发送命令结果失败，Engine队列已满: extensionName={}, graphId={}, commandId={}",
                    extensionName, graphId, result.getCommandId());
        } else {
            log.debug("Extension命令结果发送成功: extensionName={}, graphId={}, commandId={}, isFinal={}",
                    extensionName, graphId, result.getCommandId(), result.isFinal());
        }
        return success;
    }

    @Override
    public <T> Optional<T> getProperty(String key, Class<T> type) {
        if (key == null || key.isEmpty()) {
            log.warn("尝试获取空或无效的属性键: extensionName={}, graphId={}", extensionName, graphId);
            return Optional.empty();
        }
        Object value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        } else {
            log.warn("属性类型不匹配: extensionName={}, graphId={}, key={}, expectedType={}, actualType={}",
                    extensionName, graphId, key, type.getName(), value.getClass().getName());
            return Optional.empty();
        }
    }

    @Override
    public ExecutorService getVirtualThreadExecutor() {
        return virtualThreadExecutor;
    }

    @Override
    public String getExtensionName() {
        return extensionName;
    }

    @Override
    public String getAppUri() {
        return appUri;
    }

    @Override
    public String getGraphId() {
        return graphId;
    }

    /**
     * 获取Extension内部虚拟线程执行器中当前活跃的任务数量。
     * 这代表了正在执行的非阻塞异步操作的数量。
     *
     * @return 活跃的虚拟线程任务数量
     */
    @Override
    public int getActiveVirtualThreadCount() {
        if (extension instanceof BaseExtension baseExtension) {
            return baseExtension.getActiveTaskCount();
        }
        return 0; // 如果不是 BaseExtension 类型，返回0或抛出异常
    }

    /**
     * 关闭ExtensionContext时，需要关闭虚拟线程池
     */
    public void close() {
        if (!virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdownNow();
            log.info("ExtensionContext虚拟线程池已关闭: extensionName={}, graphId={}", extensionName, graphId);
        }
    }
}