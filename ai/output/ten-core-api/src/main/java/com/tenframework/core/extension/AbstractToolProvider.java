package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    protected String extensionName;
    protected boolean isRunning = false;
    protected Map<String, Object> configuration;
    protected List<ToolMetadata> availableTools;

    @Override
    public void onConfigure(ExtensionContext context) {
        this.extensionName = context.getExtensionName();
        // 配置属性将在子类中通过getProperty方法获取
        log.info("工具提供者配置阶段: extensionName={}", extensionName);
        onToolProviderConfigure(context);
    }

    @Override
    public void onInit(ExtensionContext context) {
        log.info("工具提供者初始化阶段: extensionName={}", extensionName);
        this.availableTools = initializeTools();
        onToolProviderInit(context);
    }

    @Override
    public void onStart(ExtensionContext context) {
        log.info("工具提供者启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
        onToolProviderStart(context);
    }

    @Override
    public void onStop(ExtensionContext context) {
        log.info("工具提供者停止阶段: extensionName={}", extensionName);
        this.isRunning = false;
        onToolProviderStop(context);
    }

    @Override
    public void onDeinit(ExtensionContext context) {
        log.info("工具提供者清理阶段: extensionName={}", extensionName);
        onToolProviderDeinit(context);
    }

    @Override
    public void onCommand(Command command, ExtensionContext context) {
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
                handleToolCommand(command, context);
            } catch (Exception e) {
                log.error("工具提供者命令处理异常: extensionName={}, commandName={}", 
                        extensionName, command.getName(), e);
                sendErrorResult(command, context, "工具执行异常: " + e.getMessage());
            }
        }, context.getVirtualThreadExecutor());
    }

    @Override
    public void onData(com.tenframework.core.message.Data data, ExtensionContext context) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略数据: extensionName={}, dataName={}", 
                    extensionName, data.getName());
            return;
        }

        log.debug("工具提供者收到数据: extensionName={}, dataName={}", 
                extensionName, data.getName());
        handleToolData(data, context);
    }

    @Override
    public void onAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略音频帧: extensionName={}, frameName={}", 
                    extensionName, audioFrame.getName());
            return;
        }

        log.debug("工具提供者收到音频帧: extensionName={}, frameName={}", 
                extensionName, audioFrame.getName());
        handleToolAudioFrame(audioFrame, context);
    }

    @Override
    public void onVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略视频帧: extensionName={}, frameName={}", 
                    extensionName, videoFrame.getName());
            return;
        }

        log.debug("工具提供者收到视频帧: extensionName={}, frameName={}", 
                extensionName, videoFrame.getName());
        handleToolVideoFrame(videoFrame, context);
    }

    /**
     * 工具提供者配置阶段
     */
    protected abstract void onToolProviderConfigure(ExtensionContext context);

    /**
     * 工具提供者初始化阶段
     */
    protected abstract void onToolProviderInit(ExtensionContext context);

    /**
     * 工具提供者启动阶段
     */
    protected abstract void onToolProviderStart(ExtensionContext context);

    /**
     * 工具提供者停止阶段
     */
    protected abstract void onToolProviderStop(ExtensionContext context);

    /**
     * 工具提供者清理阶段
     */
    protected abstract void onToolProviderDeinit(ExtensionContext context);

    /**
     * 初始化可用工具列表
     */
    protected abstract List<ToolMetadata> initializeTools();

    /**
     * 处理工具命令
     */
    protected abstract void handleToolCommand(Command command, ExtensionContext context);

    /**
     * 处理工具数据
     */
    protected abstract void handleToolData(com.tenframework.core.message.Data data, ExtensionContext context);

    /**
     * 处理工具音频帧
     */
    protected abstract void handleToolAudioFrame(com.tenframework.core.message.AudioFrame audioFrame, ExtensionContext context);

    /**
     * 处理工具视频帧
     */
    protected abstract void handleToolVideoFrame(com.tenframework.core.message.VideoFrame videoFrame, ExtensionContext context);

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
        if (tool.getParameters() == null) {
            return true;
        }

        for (ToolMetadata.ToolParameter param : tool.getParameters()) {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                log.warn("缺少必需的工具参数: tool={}, param={}", tool.getName(), param.getName());
                return false;
            }
        }
        return true;
    }

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(Command command, ExtensionContext context, String errorMessage) {
        CommandResult errorResult = CommandResult.error(command.getCommandId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(Command command, ExtensionContext context, Map<String, Object> result) {
        CommandResult successResult = CommandResult.success(command.getCommandId(), result);
        context.sendResult(successResult);
    }
} 