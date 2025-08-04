package com.agora.tenframework.config;

import com.agora.tenframework.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 应用关闭钩子类
 *
 * 该类负责在应用关闭时进行资源清理，包括：
 *
 * 核心功能：
 * 1. 优雅关闭 - 在应用关闭时自动触发清理操作
 * 2. Worker清理 - 清理所有运行中的Worker进程
 * 3. 资源释放 - 确保所有资源得到正确释放
 * 4. 日志记录 - 记录关闭过程的详细信息
 *
 * 技术实现：
 * - 使用Spring的@PreDestroy注解实现优雅关闭
 * - 调用WorkerService进行进程清理
 * - 确保所有Worker进程被正确终止
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的shutdown hook机制
 * - 使用Spring的@PreDestroy替代Go的defer
 * - 保持相同的清理逻辑和流程
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Component
public class ShutdownHook {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownHook.class);

    @Autowired
    private WorkerService workerService;

    /**
     * 应用关闭时的清理操作
     *
     * 在应用关闭时自动调用，清理所有Worker进程
     * 对应Go版本的shutdown cleanup逻辑
     */
    @PreDestroy
    public void cleanup() {
        logger.info("应用正在关闭，开始清理Worker进程");
        workerService.cleanAllWorkers();
        logger.info("Worker进程清理完成");
    }
}