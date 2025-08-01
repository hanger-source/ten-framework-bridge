package com.agora.tenframework.config;

import com.agora.tenframework.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Shutdown Hook for cleaning up workers
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
     * Clean up all workers when application shuts down
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Application shutting down, cleaning up workers");
        workerService.cleanAllWorkers();
        logger.info("Worker cleanup completed");
    }
}