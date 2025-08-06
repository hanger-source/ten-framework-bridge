package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.tenframework.core.Location; // 导入Location类

/**
 * 简单的LLM扩展示例
 * 基于实际LLM扩展代码的模式，展示LLM扩展的实现
 *
 * 开发者只需要：
 * 1. 继承BaseExtension
 * 2. 实现LLM核心逻辑
 * 3. 处理工具调用
 * 4. 实现流式输出
 */
@Slf4j
public class SimpleLLMExtension extends BaseExtension {

    private String apiKey = "";
    private String model = "gpt-3.5-turbo";
    private int maxHistory = 20;
    private boolean isInitialized = false;

    // 会话历史管理
    private final Map<String, List<Map<String, Object>>> sessionHistory = new ConcurrentHashMap<>();

    // 工具管理
    private final Map<String, ToolMetadata> availableTools = new ConcurrentHashMap<>();

    // 辅助方法：获取当前Extension的Location
    private Location getCurrentLocation() {
        return new Location(context.getAppUri(), context.getGraphId(), context.getExtensionName());
    }

    @Override
    protected void handleCommand(Command command, ExtensionContext context) {
        String commandName = command.getName();

        switch (commandName) {
            case "tool_register":
                handleToolRegister(command, context);
                break;
            case "chat_completion_call":
                handleChatCompletionCall(command, context);
                break;
            case "flush":
                handleFlush(command, context);
                break;
            default:
                log.warn("未知LLM命令: {}", commandName);
        }
    }

    @Override
    protected void handleData(Data data, ExtensionContext context) {
        // LLM扩展处理数据消息（如用户输入）
        String dataName = data.getName();
        String dataContent = new String(data.getDataBytes());
        log.info("LLM收到数据: {} = {}", dataName, dataContent);

        // 异步处理数据
        submitTask(() -> {
            try {
                processUserInput(dataContent, context);
            } catch (Exception e) {
                log.error("处理用户输入失败", e);
            }
        });
    }

    @Override
    protected void handleAudioFrame(AudioFrame audioFrame, ExtensionContext context) {
        // LLM扩展通常不直接处理音频帧
        log.debug("LLM收到音频帧: {} ({} bytes)", audioFrame.getName(), audioFrame.getDataSize());
    }

    @Override
    protected void handleVideoFrame(VideoFrame videoFrame, ExtensionContext context) {
        // LLM扩展通常不直接处理视频帧
        log.debug("LLM收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());
    }

    // 可选：自定义配置
    @Override
    protected void onExtensionConfigure(ExtensionContext context) {
        // 从配置中读取LLM参数
        apiKey = getConfig("api_key", String.class, "");
        model = getConfig("model", String.class, "gpt-3.5-turbo");
        maxHistory = getConfig("max_history", Integer.class, 20);
        log.info("LLM扩展配置完成: model={}, maxHistory={}", model, maxHistory);
    }

    // 可选：自定义初始化
    @Override
    protected void onExtensionInit(ExtensionContext context) {
        // 初始化LLM扩展
        isInitialized = true;
        log.info("LLM扩展初始化完成");
    }

    // 可选：自定义启动
    @Override
    protected void onExtensionStart(ExtensionContext context) {
        // 启动LLM扩展
        log.info("LLM扩展启动完成");
    }

    // 可选：自定义停止
    @Override
    protected void onExtensionStop(ExtensionContext context) {
        // 停止LLM扩展
        log.info("LLM扩展停止完成");
    }

    // 可选：自定义清理
    @Override
    protected void onExtensionDeinit(ExtensionContext context) {
        // 清理LLM扩展
        isInitialized = false;
        sessionHistory.clear();
        availableTools.clear();
        log.info("LLM扩展清理完成");
    }

    // 可选：自定义健康检查
    @Override
    protected boolean performHealthCheck() {
        // 检查LLM扩展是否正常
        return isInitialized && !apiKey.isEmpty();
    }

    /**
     * 处理工具注册
     */
    private void handleToolRegister(Command command, ExtensionContext context) {
        try {
            // 解析工具元数据
            ToolMetadata toolMetadata = parseToolMetadata(command);
            availableTools.put(toolMetadata.getName(), toolMetadata);

            // 发送工具注册结果
            CommandResult result = CommandResult.success(command.getCommandId(),
                    Map.of("tool_name", toolMetadata.getName(), "status", "registered"));
            result.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
            sendResult(result);

            log.info("工具注册成功: {}", toolMetadata.getName());
        } catch (Exception e) {
            log.error("工具注册失败", e);
            CommandResult errorResult = CommandResult.error(command.getCommandId(), "工具注册失败: " + e.getMessage());
            errorResult.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
            sendResult(errorResult);
        }
    }

    /**
     * 处理聊天完成调用
     */
    private void handleChatCompletionCall(Command command, ExtensionContext context) {
        try {
            String sessionId = command.getArg("session_id", String.class).orElse("default");
            String userInput = command.getArg("user_input", String.class).orElse("");

            // 异步处理聊天完成
            submitTask(() -> {
                try {
                    processChatCompletion(sessionId, userInput, context);
                } catch (Exception e) {
                    log.error("聊天完成处理失败", e);
                    CommandResult errorResult = CommandResult.error(command.getCommandId(),
                            "聊天完成处理失败: " + e.getMessage());
                    errorResult.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
                    sendResult(errorResult);
                }
            });

        } catch (Exception e) {
            log.error("聊天完成调用失败", e);
            CommandResult errorResult = CommandResult.error(command.getCommandId(), "聊天完成调用失败: " + e.getMessage());
            errorResult.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
            sendResult(errorResult);
        }
    }

    /**
     * 处理刷新命令
     */
    private void handleFlush(Command command, ExtensionContext context) {
        try {
            String sessionId = command.getArg("session_id", String.class).orElse("default");

            // 清理会话历史
            sessionHistory.remove(sessionId);

            // 发送刷新结果
            CommandResult result = CommandResult.success(command.getCommandId(),
                    Map.of("session_id", sessionId, "status", "flushed"));
            result.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
            sendResult(result);

            log.info("会话刷新成功: {}", sessionId);
        } catch (Exception e) {
            log.error("会话刷新失败", e);
            CommandResult errorResult = CommandResult.error(command.getCommandId(), "会话刷新失败: " + e.getMessage());
            errorResult.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
            sendResult(errorResult);
        }
    }

    /**
     * 处理用户输入
     */
    private void processUserInput(String userInput, ExtensionContext context) {
        // 模拟LLM处理用户输入
        String response = generateResponse(userInput);

        // 发送流式文本输出
        sendTextOutput(response, false);
        sendTextOutput("\n", true); // 结束标记
    }

    /**
     * 处理聊天完成
     */
    private void processChatCompletion(String sessionId, String userInput, ExtensionContext context) {
        // 更新会话历史
        updateSessionHistory(sessionId, "user", userInput);

        // 生成响应
        String response = generateResponse(userInput);

        // 更新会话历史
        updateSessionHistory(sessionId, "assistant", response);

        // 发送流式文本输出
        sendTextOutput(response, false);
        sendTextOutput("\n", true); // 结束标记

        // 发送完成结果
        CommandResult finalResult = CommandResult.success("chat_completion",
                Map.of("session_id", sessionId, "response", response));
        finalResult.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
        sendResult(finalResult);
    }

    /**
     * 生成响应（模拟LLM）
     */
    private String generateResponse(String userInput) {
        // 简单的响应生成逻辑
        if (userInput.toLowerCase().contains("hello") || userInput.toLowerCase().contains("你好")) {
            return "你好！我是简单的LLM扩展，很高兴为您服务。";
        } else if (userInput.toLowerCase().contains("weather") || userInput.toLowerCase().contains("天气")) {
            return "我可以帮您查询天气信息。请使用天气工具。";
        } else {
            return "我理解您说：" + userInput + "。这是一个模拟的LLM响应。";
        }
    }

    /**
     * 发送文本输出
     */
    private void sendTextOutput(String text, boolean isFinal) {
        Data textData = Data.text("text_output", text);
        textData.setProperties(Map.of(
                "text", text,
                "end_of_segment", isFinal));
        textData.setSourceLocation(getCurrentLocation()); // 设置sourceLocation
        sendMessage(textData);
    }

    /**
     * 更新会话历史
     */
    private void updateSessionHistory(String sessionId, String role, String content) {
        sessionHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>()) // 使用ArrayList确保可变
                .add(Map.of("role", role, "content", content));

        // 限制历史记录长度
        List<Map<String, Object>> history = sessionHistory.get(sessionId);
        if (history.size() > maxHistory) {
            history = history.subList(history.size() - maxHistory, history.size());
            sessionHistory.put(sessionId, history);
        }
    }

    /**
     * 解析工具元数据
     */
    private ToolMetadata parseToolMetadata(Command command) {
        // 简化的工具元数据解析
        String toolName = command.getArg("name", String.class).orElse("unknown_tool");
        String description = command.getArg("description", String.class).orElse("Unknown tool");

        return ToolMetadata.builder()
                .name(toolName)
                .description(description)
                .version("1.0.0")
                .type("function")
                .parameters(List.of())
                .properties(Map.of())
                .build();
    }
}