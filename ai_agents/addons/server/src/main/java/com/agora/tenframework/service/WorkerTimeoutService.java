package com.agora.tenframework.service;

import com.agora.tenframework.config.Constants;
import com.agora.tenframework.model.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Worker超时服务类
 *
 * 该类负责监控Worker进程的超时状态，包括：
 *
 * 核心功能：
 * 1. 定时检查Worker超时 - 每5秒检查一次所有Worker进程
 * 2. 自动停止超时Worker - 当Worker超过指定时间未更新时自动停止
 * 3. 无限超时支持 - 支持设置Worker永不超时
 * 4. 进程清理 - 强制终止超时的Worker进程
 *
 * 技术实现：
 * - 使用Spring的@Scheduled注解实现定时任务
 * - 基于时间戳计算Worker运行时长
 * - 调用WorkerService进行进程管理
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的timeoutWorkers函数
 * - 保持相同的超时检查逻辑
 * - 使用Java的定时任务替代Go的goroutine
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Service
public class WorkerTimeoutService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerTimeoutService.class);

    @Autowired
    private WorkerService workerService;

    /**
     * Worker清理睡眠时间（秒）
     * 对应Go版本的workerCleanSleepSeconds常量
     */
    private static final int WORKER_CLEAN_SLEEP_SECONDS = 5;

    /**
     * 超时Worker检查
     *
     * 定时检查所有Worker进程的超时状态
     * 对应Go版本的timeoutWorkers函数
     * 每5秒执行一次
     */
    @Scheduled(fixedRate = 5000)
    public void timeoutWorkers() {
        for (Worker worker : workerService.getAllWorkers()) {
            // 跳过无限超时的Worker - 对应Go版本的WORKER_TIMEOUT_INFINITY检查
            if (worker.getQuitTimeoutSeconds() == Constants.WORKER_TIMEOUT_INFINITY) {
                continue;
            }

            long currentTime = System.currentTimeMillis() / 1000;
            long updateTime = worker.getUpdateTs();
            int quitTimeout = worker.getQuitTimeoutSeconds();

            // 检查Worker是否超时 - 对应Go版本的超时检查逻辑
            if (updateTime + quitTimeout < currentTime) {
                try {
                    // 停止Worker，使用与Go版本worker.stop相同的方法
                    workerService.stopWorker(worker.getChannelName(), java.util.UUID.randomUUID().toString());
                    logger.info("超时Worker停止成功 - channelName: {}, worker: {}, 当前时间: {}",
                            worker.getChannelName(), worker, currentTime);
                } catch (Exception e) {
                    logger.error("超时Worker停止失败 - error: {}, channelName: {}",
                            e.getMessage(), worker.getChannelName());
                    continue; // 对应Go版本的continue
                }
            }
        }

        logger.debug("Worker超时检查完成 - 睡眠时间: {}秒", WORKER_CLEAN_SLEEP_SECONDS);
    }

    /**
     * 终止进程
     *
     * 使用kill命令强制终止指定进程
     * 对应Go版本的killProcess函数
     * Go使用syscall.Kill(pid, syscall.SIGKILL)终止进程
     *
     * @param pid 进程ID
     */
    private void killProcess(int pid) {
        try {
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "kill -KILL " + pid });
            process.waitFor();
            logger.info("发送KILL信号到进程 - pid: {}", pid);
        } catch (Exception e) {
            logger.error("终止进程失败 - pid: {}, error: {}", pid, e.getMessage());
        }
    }
}