package com.tenframework.core.extension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具提供者基础抽象类
 * 用于提供各种工具服务，如文件操作、网络请求等
 *
 * 功能：
 * 1. 管理工具元数据
 * 2. 提供工具执行接口
 * 3. 支持工具参数验证
 * 4. 处理工具执行结果
 */
@Slf4j
public abstract class AbstractToolProvider implements Extension {

    private final ObjectMapper objectMapper = new ObjectMapper(); // 用于 JSON 解析
    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;
    protected List<ToolMetadata> availableTools;
    protected AsyncExtensionEnv env;

    @Override
    public void onConfigure(AsyncExtensionEnv env) {
        extensionName = env.getExtensionName();
        this.env = env;
        // 配置属性将在子类中通过getProperty方法获取
        log.info("工具提供者配置阶段: extensionName={}", extensionName);
        onToolProviderConfigure(env);
    }

    @Override
    public void onInit(AsyncExtensionEnv env) {
        log.info("工具提供者初始化阶段: extensionName={}", extensionName);
        availableTools = initializeTools();
        onToolProviderInit(env);
    }

    @Override
    public void onStart(AsyncExtensionEnv env) {
        log.info("工具提供者启动阶段: extensionName={}", extensionName);
        isRunning = true;
        onToolProviderStart(env);
    }

    @Override
    public void onStop(AsyncExtensionEnv env) {
        log.info("工具提供者停止阶段: extensionName={}", extensionName);
        isRunning = false;
        onToolProviderStop(env);
    }

    @Override
    public void onDeinit(AsyncExtensionEnv env) {
        log.info("工具提供者清理阶段: extensionName={}", extensionName);
        onToolProviderDeinit(env);
    }

    @Override
    public void onCommand(Command command, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        log.debug("工具提供者收到命令: extensionName={}, commandName={}",
                extensionName, command.getName());

        // 使用虚拟线程处理工具命令
        CompletableFuture.runAsync(() -> {
            try {
                handleToolCommand(command, env);
            } catch (Exception e) {
                log.error("工具提供者命令处理异常: extensionName={}, commandName={}",
                        extensionName, command.getName(), e);
                sendErrorResult(command, env, "工具执行异常: " + e.getMessage());
            }
        }, env.getVirtualThreadExecutor());
    }

    @Override
    public void onData(DataMessage data, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略数据: extensionName={}, dataId={}",
                extensionName, data.getId());
            return;
        }

        log.debug("工具提供者收到数据: extensionName={}, dataId={}",
            extensionName, data.getId());
        // 异步处理数据
        CompletableFuture.runAsync(() -> {
            try {
                handleToolData(data, env);
            } catch (Exception e) {
                log.error("工具提供者数据处理异常: extensionName={}, dataId={}",
                    extensionName, data.getId(), e);
            }
        }, env.getVirtualThreadExecutor());
    }

    @Override
    public void onAudioFrame(AudioFrameMessage audioFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略音频帧: extensionName={}, frameId={}",
                extensionName, audioFrame.getId());
            return;
        }

        log.debug("工具提供者收到音频帧: extensionName={}, frameId={}",
            extensionName, audioFrame.getId());
        // 异步处理音频帧
        CompletableFuture.runAsync(() -> {
            try {
                handleToolAudioFrame(audioFrame, env);
            } catch (Exception e) {
                log.error("工具提供者音频帧处理异常: extensionName={}, frameId={}",
                    extensionName, audioFrame.getId(), e);
            }
        }, env.getVirtualThreadExecutor());
    }

    @Override
    public void onVideoFrame(VideoFrameMessage videoFrame, AsyncExtensionEnv env) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略视频帧: extensionName={}, frameId={}",
                extensionName, videoFrame.getId());
            return;
        }

        log.debug("工具提供者收到视频帧: extensionName={}, frameId={}",
            extensionName, videoFrame.getId());
        // 异步处理视频帧
        CompletableFuture.runAsync(() -> {
            try {
                handleToolVideoFrame(videoFrame, env);
            } catch (Exception e) {
                log.error("工具提供者视频帧处理异常: extensionName={}, frameId={}",
                    extensionName, videoFrame.getId(), e);
            }
        }, env.getVirtualThreadExecutor());
    }

    /**
     * 工具提供者配置阶段
     */
    protected abstract void onToolProviderConfigure(AsyncExtensionEnv context);

    /**
     * 工具提供者初始化阶段
     */
    protected abstract void onToolProviderInit(AsyncExtensionEnv context);

    /**
     * 工具提供者启动阶段
     */
    protected abstract void onToolProviderStart(AsyncExtensionEnv context);

    /**
     * 工具提供者停止阶段
     */
    protected abstract void onToolProviderStop(AsyncExtensionEnv context);

    /**
     * 工具提供者清理阶段
     */
    protected abstract void onToolProviderDeinit(AsyncExtensionEnv context);

    /**
     * 初始化可用工具列表
     */
    protected abstract List<ToolMetadata> initializeTools();

    /**
     * 处理工具命令
     */
    protected abstract void handleToolCommand(Command command, AsyncExtensionEnv context);

    /**
     * 处理工具数据
     */
    protected abstract void handleToolData(DataMessage data, AsyncExtensionEnv context);

    /**
     * 处理工具音频帧
     */
    protected abstract void handleToolAudioFrame(AudioFrameMessage audioFrame,
            AsyncExtensionEnv context);

    /**
     * 处理工具视频帧
     */
    protected abstract void handleToolVideoFrame(VideoFrameMessage videoFrame,
            AsyncExtensionEnv context);

    /**
     * 获取可用工具列表
     */
    public List<ToolMetadata> getAvailableTools() {
        return availableTools;
    }

    /**
     * 根据工具名称查找工具
     */
    public ToolMetadata findTool(String toolName) {
        return availableTools.stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 验证工具参数
     */
    protected boolean validateToolParameters(ToolMetadata tool, Map<String, Object> parameters) {
        // 检查 required 字段是否存在于 parameters 中
        if (tool.getRequired() != null) {
            for (String requiredParam : tool.getRequired()) {
                if (!parameters.containsKey(requiredParam)) {
                    log.warn("缺少必需的工具参数: tool={}, param={}", tool.getName(), requiredParam);
                    return false;
                }
            }
        }
        // TODO: 更复杂的参数验证，例如类型检查，可以结合 JSON Schema 库实现。
        return true;
    }

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, AsyncExtensionEnv context, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command.getId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(Command command, AsyncExtensionEnv context, Object result) {
        CommandResult successResult = CommandResult.success(command.getId(), result != null ? result.toString() : "");
        context.sendResult(successResult);
    }

    @Override
    public String getAppUri() {
        return env != null ? env.getAppUri() : "unknown";
    }

    /**
     * 工具元数据，用于工具调用功能
     * 与 AbstractLLMExtension 中的定义保持一致
     */
    @Data
    @Builder
    public static class ToolMetadata {
        @JsonProperty("name")
        private String name;
        @JsonProperty("description")
        private String description;
        @JsonProperty("parameters")
        private Map<String, Object> parameters; // 工具参数的JSON Schema
        @JsonProperty("required")
        private List<String> required; // 必填参数列表

        // 默认构造函数用于 Jackson
        public ToolMetadata() {
        }

        // AllArgsConstructor 用于 Builder
        public ToolMetadata(String name, String description, Map<String, Object> parameters, List<String> required) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
            this.required = required;
        }
    }
}