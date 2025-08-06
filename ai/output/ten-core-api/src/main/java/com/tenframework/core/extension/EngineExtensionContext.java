package com.tenframework.core.extension;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EngineExtensionContext类
 * ExtensionContext接口的具体实现，作为Extension与Engine交互的桥梁
 * 提供Extension所需的核心功能，并封装了与Engine的通信细节及虚拟线程执行器
 */
@Slf4j
public class EngineExtensionContext implements ExtensionContext {

    private final String extensionName;
    private final String graphId;
    private final Engine engine;
    private final Map<String, Object> properties;
    private final ExecutorService virtualThreadExecutor;

    public EngineExtensionContext(String extensionName, String graphId, Engine engine, Map<String, Object> properties) {
        this.extensionName = extensionName;
        this.graphId = graphId;
        this.engine = engine;
        this.properties = Collections.unmodifiableMap(properties != null ? properties : Collections.emptyMap());
        // 为每个ExtensionContext创建一个独立的虚拟线程池，或使用共享池
        // 这里使用Thread-per-task模式的虚拟线程，适用于offload阻塞操作
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        log.debug("EngineExtensionContext创建成功: extensionName={}, graphId={}", extensionName, graphId);
    }

    @Override
    public boolean sendMessage(Message message) {
        if (message == null) {
            log.warn("尝试发送空消息: extensionName={}, graphId={}", extensionName, graphId);
            return false;
        }
        // 消息发送后会回到Engine的inboundMessageQueue进行处理
        boolean success = engine.submitMessage(message);
        if (!success) {
            log.warn("Extension发送消息失败，Engine队列已满: extensionName={}, graphId={}, messageType={}",
                    extensionName, graphId, message.getType());
        }
        return success;
    }

    @Override
    public boolean sendResult(CommandResult result) {
        if (result == null) {
            log.warn("尝试发送空命令结果: extensionName={}, graphId={}", extensionName, graphId);
            return false;
        }
        // 命令结果本质上也是一种消息，回溯到Engine的inboundMessageQueue处理
        boolean success = engine.submitMessage(result);
        if (!success) {
            log.warn("Extension发送命令结果失败，Engine队列已满: extensionName={}, graphId={}, commandId={}",
                    extensionName, graphId, result.getCommandId());
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
    public String getGraphId() {
        return graphId;
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