package com.agora.tenframework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TEN Framework Server Application
 *
 * TEN Framework是一个基于Spring Boot的实时音视频AI代理服务器，主要功能包括：
 *
 * 核心功能：
 * 1. Worker进程管理 - 启动、停止、监控AI代理工作进程
 * 2. 实时音视频集成 - 与Agora RTC/RTM服务集成，支持音视频通话
 * 3. 动态配置管理 - 运行时修改AI代理的配置参数
 * 4. 多租户支持 - 支持多个频道同时运行不同的AI代理
 * 5. 健康监控 - 监控Worker进程状态，自动重启异常进程
 *
 * 技术架构：
 * - Spring Boot 2.x - 提供Web服务和依赖注入
 * - 异步处理 - @EnableAsync支持异步任务处理
 * - 定时任务 - @EnableScheduling支持定时清理和监控
 * - 进程管理 - 使用ProcessBuilder管理子进程
 * - 配置热更新 - 支持运行时更新property.json配置
 *
 * 与Go版本的对应关系：
 * - 此Java版本是Go版本的功能对等实现
 * - 保持了相同的API接口和业务逻辑
 * - 使用Spring Boot替代Go的HTTP服务器
 *
 * @author Agora IO
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync // 启用异步处理，支持异步任务执行
@EnableScheduling // 启用定时任务，用于Worker进程监控和清理
public class TenFrameworkServerApplication {

    /**
     * 应用程序入口点
     *
     * 启动流程：
     * 1. 加载Spring Boot配置
     * 2. 初始化依赖注入容器
     * 3. 启动Web服务器（默认8080端口）
     * 4. 注册关闭钩子，确保优雅关闭
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TenFrameworkServerApplication.class, args);
    }
}