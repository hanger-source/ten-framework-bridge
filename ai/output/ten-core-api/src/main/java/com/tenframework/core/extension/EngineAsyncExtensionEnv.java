package com.tenframework.core.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tenframework.core.engine.CommandSubmitter;
import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * `EngineAsyncExtensionEnv` 是 `AsyncExtensionEnv` 接口的具体实现，提供了 Extension 与
 * Engine 交互的运行时环境。
 * 它负责将 Extension 的请求（如发送消息、命令）委托给 Engine，并管理 Extension 的属性和虚拟线程执行器。
 */
@Slf4j
public class EngineAsyncExtensionEnv implements AsyncExtensionEnv {

    private final String extensionName;
    private final String graphId;
    private final String appUri;
    private final MessageSubmitter messageSubmitter; // 委托给 Engine 发送消息
    private final CommandSubmitter commandSubmitter; // 委托给 Engine 提交命令
    private final Map<String, Object> properties; // 存储 Extension 的属性
    private final ExecutorService virtualThreadExecutor; // 用于 Extension 内部的虚拟线程任务
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EngineAsyncExtensionEnv(String extensionName, String graphId, String appUri,
            MessageSubmitter messageSubmitter, CommandSubmitter commandSubmitter, Map<String, Object> properties) {
        this.extensionName = extensionName;
        this.graphId = graphId;
        this.appUri = appUri;
        this.messageSubmitter = messageSubmitter;
        this.commandSubmitter = commandSubmitter;
        this.properties = new ConcurrentHashMap<>(properties);
        // 使用虚拟线程 ExecutorService
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void sendResult(CommandResult result) {
        // 确保结果消息有正确的源和目的地
        if (result.getSrcLoc() == null) {
            result.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        if (result.getDestLocs() == null || result.getDestLocs().isEmpty()) {
            // 如果没有指定目的地，默认回传给原始命令的发送方或 App
            // 这里简化处理，可以考虑根据原始命令的返回 Location 或通过 CommandSubmitter 回传
            log.warn("Extension {}: sendResult 未指定 destLocs, 结果可能不会被路由。", extensionName);
        }
        if (commandSubmitter != null) {
            commandSubmitter.submitCommandResult(result);
        } else {
            log.warn("Extension {}: CommandSubmitter 为空，无法发送命令结果: {}", extensionName, result.getId());
        }
    }

    @Override
    public CompletableFuture<Object> sendCommand(Command command) {
        // 确保命令消息有正确的源
        if (command.getSrcLoc() == null) {
            command.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        if (commandSubmitter != null) {
            return commandSubmitter.submitCommand(command);
        } else {
            log.error("Extension {}: CommandSubmitter 为空，无法发送命令: {}", extensionName, command.getId());
            return CompletableFuture.completedFuture(new RuntimeException("CommandSubmitter not available."));
        }
    }

    @Override
    public void sendData(DataMessage data) {
        // 确保数据消息有正确的源
        if (data.getSrcLoc() == null) {
            data.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        if (messageSubmitter != null) {
            messageSubmitter.submitMessage(data);
        } else {
            log.warn("Extension {}: MessageSubmitter 为空，无法发送数据消息: {}", extensionName, data.getId());
        }
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        // 确保视频帧消息有正确的源
        if (videoFrame.getSrcLoc() == null) {
            videoFrame.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        if (messageSubmitter != null) {
            messageSubmitter.submitMessage(videoFrame);
        } else {
            log.warn("Extension {}: MessageSubmitter 为空，无法发送视频帧消息: {}", extensionName, videoFrame.getId());
        }
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        // 确保音频帧消息有正确的源
        if (audioFrame.getSrcLoc() == null) {
            audioFrame.setSrcLoc(new Location().setAppUri(appUri).setGraphId(graphId).setNodeId(extensionName));
        }
        if (messageSubmitter != null) {
            messageSubmitter.submitMessage(audioFrame);
        } else {
            log.warn("Extension {}: MessageSubmitter 为空，无法发送音频帧消息: {}", extensionName, audioFrame.getId());
        }
    }

    // region Property Operations

    @Override
    public boolean isPropertyExist(String path) {
        return JsonUtils.getValueByPath(properties, path).isPresent();
    }

    @Override
    public Optional<String> getPropertyToJson(String path) {
        return JsonUtils.getValueByPath(properties, path).map(val -> {
            try {
                return objectMapper.writeValueAsString(val);
            } catch (JsonProcessingException e) {
                log.error("Error converting property to JSON at path {}: {}", path, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public void setPropertyFromJson(String path, String jsonStr) {
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            JsonUtils.setValueByPath(properties, path, node);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON for property at path {}: {}", path, e.getMessage());
        }
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return JsonUtils.getValueByPath(properties, path)
                .filter(Number.class::isInstance)
                .map(Number.class::intValue);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return JsonUtils.getValueByPath(properties, path)
                .filter(String.class::isInstance)
                .map(String.class::valueOf);
    }

    @Override
    public void setPropertyString(String path, String value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return JsonUtils.getValueByPath(properties, path)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::booleanValue);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return JsonUtils.getValueByPath(properties, path)
                .filter(Number.class::isInstance)
                .map(Number.class::floatValue);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        JsonUtils.setValueByPath(properties, path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        try {
            Map<String, Object> newProperties = objectMapper.readValue(jsonStr,
                    new TypeReference<Map<String, Object>>() {
                    });
            this.properties.putAll(newProperties);
        } catch (JsonProcessingException e) {
            log.error("Error initializing properties from JSON: {}", e.getMessage());
        }
    }

    // endregion Property Operations

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
}