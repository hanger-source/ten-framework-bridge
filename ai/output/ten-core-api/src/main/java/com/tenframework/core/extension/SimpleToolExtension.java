package com.tenframework.core.extension;

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;
import com.tenframework.core.extension.AsyncExtensionEnv;

import java.util.List;
import java.util.Map;

/**
 * 简单的工具扩展示例
 * 基于实际扩展代码的模式，展示工具扩展的实现
 *
 * 开发者只需要：
 * 1. 继承BaseExtension
 * 2. 实现核心业务逻辑
 * 3. 定义工具元数据
 * 4. 实现工具执行逻辑
 */
@Slf4j
public class SimpleToolExtension extends BaseExtension {

    private String apiKey = "";
    private boolean isInitialized = false;

    // 工具元数据定义
    private static final String TOOL_NAME = "simple_calculator";
    private static final String TOOL_DESCRIPTION = "A simple calculator tool for basic arithmetic operations";

    @Override
    protected void handleCommand(Command command, AsyncExtensionEnv context) {
        String commandName = command.getName();

        switch (commandName) {
            case "tool_register":
                handleToolRegister(command, context);
                break;
            case "tool_call":
                handleToolCall(command, context);
                break;
            default:
                log.warn("未知命令: {}", commandName);
        }
    }

    @Override
    protected void handleData(Data data, AsyncExtensionEnv context) {
        // 工具扩展通常不处理数据消息
        log.debug("工具扩展收到数据: {}", data.getName());
    }

    @Override
    protected void handleAudioFrame(AudioFrame audioFrame, AsyncExtensionEnv context) {
        // 工具扩展通常不处理音频帧
        log.debug("工具扩展收到音频帧: {}", audioFrame.getName());
    }

    @Override
    protected void handleVideoFrame(VideoFrame videoFrame, AsyncExtensionEnv context) {
        // 工具扩展通常不处理视频帧
        log.debug("工具扩展收到视频帧: {}", videoFrame.getName());
    }

    // 可选：自定义配置
    @Override
    protected void onExtensionConfigure(AsyncExtensionEnv context) {
        // 从配置中读取API密钥
        apiKey = getConfig("api_key", String.class, "");
        log.info("工具扩展配置完成: apiKey={}", apiKey.isEmpty() ? "未设置" : "已设置");
    }

    // 可选：自定义初始化
    @Override
    protected void onExtensionInit(AsyncExtensionEnv context) {
        // 初始化工具扩展
        isInitialized = true;
        log.info("工具扩展初始化完成");
    }

    // 可选：自定义启动
    @Override
    protected void onExtensionStart(AsyncExtensionEnv context) {
        // 启动工具扩展
        log.info("工具扩展启动完成");
    }

    // 可选：自定义停止
    @Override
    protected void onExtensionStop(AsyncExtensionEnv context) {
        // 停止工具扩展
        log.info("工具扩展停止完成");
    }

    // 可选：自定义清理
    @Override
    protected void onExtensionDeinit(AsyncExtensionEnv context) {
        // 清理工具扩展
        isInitialized = false;
        log.info("工具扩展清理完成");
    }

    // 可选：自定义健康检查
    @Override
    protected boolean performHealthCheck() {
        // 检查工具扩展是否正常
        return isInitialized && !apiKey.isEmpty();
    }

    /**
     * 处理工具注册
     */
    private void handleToolRegister(Command command, AsyncExtensionEnv context) {
        try {
            // 创建工具元数据
            ToolMetadata toolMetadata = createToolMetadata();

            // 发送工具注册结果
            CommandResult result = CommandResult.success(command.getCommandId(),
                    Map.of("tool_name", toolMetadata.getName(), "status", "registered"));
            sendResult(result); // 移除.join()

            log.info("工具注册成功: {}", toolMetadata.getName());
        } catch (Exception e) {
            log.error("工具注册失败", e);
            sendResult(CommandResult.error(command.getCommandId(), "工具注册失败: " + e.getMessage())); // 移除.join()
        }
    }

    /**
     * 处理工具调用
     */
    private void handleToolCall(Command command, AsyncExtensionEnv context) {
        try {
            String toolName = command.getArg("name", String.class).orElse("");
            Map<String, Object> args = command.getArgs();

            if (TOOL_NAME.equals(toolName)) {
                // 执行计算器工具
                Object result = executeCalculatorTool(args);

                // 发送工具执行结果
                CommandResult cmdResult = CommandResult.success(command.getCommandId(),
                        Map.of("tool_name", toolName, "result", result));
                sendResult(cmdResult); // 移除.join()

                log.info("工具调用成功: {} = {}", toolName, result);
            } else {
                log.warn("未知工具: {}", toolName);
                sendResult(CommandResult.error(command.getCommandId(), "未知工具: " + toolName)); // 移除.join()
            }
        } catch (Exception e) {
            log.error("工具调用失败", e);
            sendResult(CommandResult.error(command.getCommandId(), "工具调用失败: " + e.getMessage())); // 移除.join()
        }
    }

    /**
     * 创建工具元数据
     */
    private ToolMetadata createToolMetadata() {
        return ToolMetadata.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .version("1.0.0")
                .type("calculator")
                .parameters(List.of(
                        ToolMetadata.ToolParameter.builder()
                                .name("operation")
                                .type("string")
                                .description("The arithmetic operation: add, subtract, multiply, divide")
                                .required(true)
                                .build(),
                        ToolMetadata.ToolParameter.builder()
                                .name("a")
                                .type("number")
                                .description("First number")
                                .required(true)
                                .build(),
                        ToolMetadata.ToolParameter.builder()
                                .name("b")
                                .type("number")
                                .description("Second number")
                                .required(true)
                                .build()))
                .properties(Map.of("category", "math", "complexity", "simple"))
                .build();
    }

    /**
     * 执行计算器工具
     */
    private Object executeCalculatorTool(Map<String, Object> args) {
        String operation = (String) args.get("operation");
        Number a = (Number) args.get("a");
        Number b = (Number) args.get("b");

        if (operation == null || a == null || b == null) {
            throw new IllegalArgumentException("缺少必需参数");
        }

        double aValue = a.doubleValue();
        double bValue = b.doubleValue();
        double result;

        switch (operation.toLowerCase()) {
            case "add":
                result = aValue + bValue;
                break;
            case "subtract":
                result = aValue - bValue;
                break;
            case "multiply":
                result = aValue * bValue;
                break;
            case "divide":
                if (bValue == 0) {
                    throw new ArithmeticException("除数不能为零");
                }
                result = aValue / bValue;
                break;
            default:
                throw new IllegalArgumentException("不支持的操作: " + operation);
        }

        return Map.of(
                "operation", operation,
                "a", aValue,
                "b", bValue,
                "result", result);
    }
}