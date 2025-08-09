package com.tenframework.core.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tenframework.core.engine.CommandSubmitter;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * `EngineAsyncExtensionEnv` 是 `AsyncExtensionEnv` 的具体实现，
 * 负责处理 Extension 与 Engine 之间的异步交互，包括消息发送、命令提交和属性操作。
 * 它将底层的消息和命令提交委托给 Engine 的 MessageSubmitter 和 CommandSubmitter。
 */
@Slf4j
public class EngineAsyncExtensionEnv implements AsyncExtensionEnv {

    private final String extensionName;
    private final String extensionType;
    private final String appUri;
    private final String graphId;
    private final MessageSubmitter messageSubmitter;
    private final CommandSubmitter commandSubmitter;
    private final Map<String, Object> properties; // 扩展的私有属性
    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper(); // 引入 ObjectMapper

    public EngineAsyncExtensionEnv(String extensionName, String extensionType, String appUri, String graphId,
            MessageSubmitter messageSubmitter, CommandSubmitter commandSubmitter,
            Map<String, Object> initialProperties) {
        this.extensionName = extensionName;
        this.extensionType = extensionType;
        this.appUri = appUri;
        this.graphId = graphId;
        this.messageSubmitter = messageSubmitter;
        this.commandSubmitter = commandSubmitter;
        this.properties = initialProperties != null ? new ConcurrentHashMap<>(initialProperties)
                : new ConcurrentHashMap<>();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void sendResult(CommandResult result) {
        if (result.getSrcLoc() == null) {
            result.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        log.debug("EngineAsyncExtensionEnv: Extension {} 发送命令结果: {}", extensionName, result.getId());
        commandSubmitter.submitCommandResult(result);
    }

    // sendData, sendVideoFrame, sendAudioFrame 现在是 AsyncExtensionEnv 的 default 方法

    @Override
    public boolean sendMessage(Message message) {
        if (message.getSrcLoc() == null) {
            message.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        log.debug("EngineAsyncExtensionEnv: Extension {} 发送消息: {}", extensionName, message.getId());
        return messageSubmitter.submitMessage(message);
    }

    @Override
    public CompletableFuture<Object> sendCommand(Command command) {
        if (command.getSrcLoc() == null) {
            command.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        log.debug("EngineAsyncExtensionEnv: Extension {} 发送命令: {}", extensionName, command.getId());
        return commandSubmitter.submitCommand(command);
    }

    @Override
    public Optional<Object> getProperty(String path) {
        return JsonUtils.getValueByPath(properties, path);
    }

    @Override
    public void setProperty(String path, Object value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public boolean hasProperty(String path) {
        return JsonUtils.hasValueByPath(properties, path);
    }

    @Override
    public void deleteProperty(String path) {
        JsonUtils.deleteValueByPath(properties, path);
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return JsonUtils.getValueByPath(properties, path).map(Number.class::cast).map(Number::intValue);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return JsonUtils.getValueByPath(properties, path).map(Number.class::cast).map(Number::longValue);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return JsonUtils.getValueByPath(properties, path).map(String.class::cast);
    }

    @Override
    public void setPropertyString(String path, String value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return JsonUtils.getValueByPath(properties, path).map(Boolean.class::cast);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return JsonUtils.getValueByPath(properties, path).map(Number.class::cast).map(Number::doubleValue);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return JsonUtils.getValueByPath(properties, path).map(Number.class::cast).map(Number::floatValue);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        JsonUtils.setValueByPath(properties, path, value);
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

    @Override
    public int getActiveVirtualThreadCount() {
        // TODO: 实际实现需要通过 ExecutorService 的内部机制获取活跃线程数
        return 0; // 暂时返回0
    }

    @Override
    public void close() {
        // 关闭虚拟线程执行器
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        // 从 JSON 字符串初始化属性
        try {
            Map<String, Object> newProperties = objectMapper.readValue(jsonStr,
                    new TypeReference<Map<String, Object>>() {
                    });
            this.properties.clear(); // 清空原有属性
            this.properties.putAll(newProperties); // 导入新属性
            log.debug("EngineAsyncExtensionEnv: 从 JSON 初始化属性成功: {}", jsonStr);
        } catch (Exception e) {
            log.error("EngineAsyncExtensionEnv: 从 JSON 初始化属性失败: {}", e.getMessage(), e);
        }
    }
}