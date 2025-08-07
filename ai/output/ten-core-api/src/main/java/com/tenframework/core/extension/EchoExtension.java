package com.tenframework.core.extension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.VideoFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * EchoExtension - 简单的回显扩展
 * 用于验证端到端消息流和生命周期
 *
 * 功能：
 * 1. 接收Command并返回回显结果
 * 2. 接收Data并回显数据内容
 * 3. 接收音视频帧并记录信息
 * 4. 演示虚拟线程的使用
 * 5. 验证生命周期调用
 */
@Slf4j
public class EchoExtension implements Extension {

    private String extensionName;
    private String appUri; // 新增字段来存储appUri
    private boolean isRunning = false;
    private long messageCount = 0;

    // 构造函数
    public EchoExtension(String extensionName, String appUri) {
        this.extensionName = extensionName;
        this.appUri = appUri; // 初始化appUri
    }

    @Override
    public String getAppUri() {
        return this.appUri;
    }

    @Override
    public void onConfigure(ExtensionContext context) {
        this.extensionName = context.getExtensionName();
        log.info("EchoExtension配置阶段: extensionName={}", extensionName);

        // 获取配置属性示例
        context.getProperty("echo.prefix", String.class)
                .ifPresent(prefix -> log.info("EchoExtension配置前缀: {}", prefix));
    }

    @Override
    public void onInit(ExtensionContext context) {
        log.info("EchoExtension初始化阶段: extensionName={}", extensionName);

        // 模拟一些初始化工作
        try {
            Thread.sleep(100); // 模拟初始化耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onStart(ExtensionContext context) {
        log.info("EchoExtension启动阶段: extensionName={}", extensionName);
        this.isRunning = true;
    }

    @Override
    public void onStop(ExtensionContext context) {
        log.info("EchoExtension停止阶段: extensionName={}", extensionName);
        this.isRunning = false;
    }

    @Override
    public void onDeinit(ExtensionContext context) {
        log.info("EchoExtension清理阶段: extensionName={}", extensionName);
        log.info("EchoExtension统计信息: 处理消息总数={}", messageCount);
    }

    @Override
    public void onCommand(Command command, ExtensionContext context) {
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        messageCount++;
        log.info("EchoExtension收到命令: extensionName={}, commandName={}, commandId={}",
                extensionName, command.getName(), command.getCommandId());

        // 使用虚拟线程处理命令（模拟异步操作）
        ExecutorService executor = context.getVirtualThreadExecutor();
        CompletableFuture.runAsync(() -> {
            try {
                // 模拟一些处理时间
                Thread.sleep(50);

                // 创建回显结果
                CommandResult result = CommandResult.success(command.getCommandId(), Map.of(
                        "original_command", command.getName(),
                        "echo_content", "Echo: " + command.getName(),
                        "processed_by", extensionName,
                        "message_count", messageCount));
                result.setName("echo_result");

                // 发送结果
                boolean success = context.sendResult(result);
                if (success) {
                    log.debug("EchoExtension命令处理完成: extensionName={}, commandName={}",
                            extensionName, command.getName());
                } else {
                    log.error("EchoExtension发送命令结果失败: extensionName={}, commandName={}",
                            extensionName, command.getName());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("EchoExtension命令处理被中断: extensionName={}, commandName={}",
                        extensionName, command.getName());
            } catch (Exception e) {
                log.error("EchoExtension命令处理异常: extensionName={}, commandName={}",
                        extensionName, command.getName(), e);
            }
        }, executor);
    }

    @Override
    public void onData(Data data, ExtensionContext context) {
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略数据: extensionName={}, dataName={}",
                    extensionName, data.getName());
            return;
        }

        messageCount++;
        log.info("EchoExtension收到数据: extensionName={}, dataName={}, dataSize={}",
                extensionName, data.getName(), data.getDataBytes().length);

        // 使用虚拟线程处理数据
        ExecutorService executor = context.getVirtualThreadExecutor();
        CompletableFuture.runAsync(() -> {
            try {
                // 模拟数据处理时间
                Thread.sleep(30);

                // 创建回显数据
                Data echoData = Data.binary("echo_data", data.getDataBytes());
                echoData.setProperties(Map.of(
                        "original_data_name", data.getName(),
                        "processed_by", extensionName,
                        "message_count", messageCount));

                // 设置目标位置（如果有的话）
                if (!data.getDestinationLocations().isEmpty()) {
                    echoData.setDestinationLocations(data.getDestinationLocations());
                }

                // 发送回显数据
                boolean success = context.sendMessage(echoData);
                if (success) {
                    log.debug("EchoExtension数据处理完成: extensionName={}, dataName={}",
                            extensionName, data.getName());
                } else {
                    log.error("EchoExtension发送数据失败: extensionName={}, dataName={}",
                            extensionName, data.getName());
                }

            } catch (Exception e) {
                log.error("EchoExtension数据处理异常: extensionName={}, dataName={}",
                        extensionName, data.getName(), e);
            }
        }, executor);
    }

    @Override
    public void onAudioFrame(AudioFrame audioFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略音频帧: extensionName={}, frameName={}",
                    extensionName, audioFrame.getName());
            return;
        }

        messageCount++;
        log.debug("EchoExtension收到音频帧: extensionName={}, frameName={}, frameSize={}, sampleRate={}, channels={}",
                extensionName, audioFrame.getName(), audioFrame.getDataBytes().length,
                audioFrame.getSampleRate(), audioFrame.getChannels());

        // 音频帧通常不需要回显，只记录信息
        // 这里可以添加音频处理逻辑，如VAD检测等
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame, ExtensionContext context) {
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略视频帧: extensionName={}, frameName={}",
                    extensionName, videoFrame.getName());
            return;
        }

        messageCount++;
        log.debug("EchoExtension收到视频帧: extensionName={}, frameName={}, frameSize={}, width={}, height={}",
                extensionName, videoFrame.getName(), videoFrame.getDataBytes().length,
                videoFrame.getWidth(), videoFrame.getHeight());

        // 视频帧通常不需要回显，只记录信息
        // 这里可以添加视频处理逻辑，如帧率控制等
    }
}