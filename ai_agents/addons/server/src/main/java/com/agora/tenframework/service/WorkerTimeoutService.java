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
 * Worker Timeout Service - Complete implementation based on Go code
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Service
public class WorkerTimeoutService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerTimeoutService.class);

    @Autowired
    private WorkerService workerService;

    private static final int WORKER_CLEAN_SLEEP_SECONDS = 5;

    /**
     * Timeout workers - equivalent to Go's timeoutWorkers function
     * Runs every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void timeoutWorkers() {
        for (Worker worker : workerService.getAllWorkers()) {
            // Skip workers with infinite timeout - equivalent to Go's
            // WORKER_TIMEOUT_INFINITY check
            if (worker.getQuitTimeoutSeconds() == Constants.WORKER_TIMEOUT_INFINITY) {
                continue;
            }

            long currentTime = System.currentTimeMillis() / 1000;
            long updateTime = worker.getUpdateTs();
            int quitTimeout = worker.getQuitTimeoutSeconds();

            // Check if worker has timed out - equivalent to Go's timeout check
            if (updateTime + quitTimeout < currentTime) {
                try {
                    // Stop worker using the same method as Go's worker.stop
                    workerService.stopWorker(worker.getChannelName(), java.util.UUID.randomUUID().toString());
                    logger.info("Timeout worker stop success - channelName: {}, worker: {}, nowTs: {}",
                            worker.getChannelName(), worker, currentTime);
                } catch (Exception e) {
                    logger.error("Timeout worker stop failed - err: {}, channelName: {}",
                            e.getMessage(), worker.getChannelName());
                    continue; // equivalent to Go's continue
                }
            }
        }

        logger.debug("Worker timeout check - sleep: {}", WORKER_CLEAN_SLEEP_SECONDS);
    }

    /**
     * Kill process - equivalent to Go's killProcess function
     * Go uses syscall.Kill(pid, syscall.SIGKILL) to kill the process
     */
    private void killProcess(int pid) {
        try {
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", "kill -KILL " + pid });
            process.waitFor();
            logger.info("Sent KILL signal to process - pid: {}", pid);
        } catch (Exception e) {
            logger.error("Error killing process - pid: {}", pid, e);
        }
    }
}