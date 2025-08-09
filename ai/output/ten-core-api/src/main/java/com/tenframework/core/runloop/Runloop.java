package com.tenframework.core.runloop;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.SleepingIdleStrategy;

/**
 * Runloop 类负责线程管理和任务调度，对齐 C 语言的 ten_runloop。
 * 它基于 Agrona AgentRunner 实现单线程事件循环，处理内部任务和外部事件源。
 */
@Slf4j
public class Runloop {

    private static final int DEFAULT_INTERNAL_QUEUE_CAPACITY = 1024; // 内部任务队列的默认容量
    // 内部任务队列，用于 postTask 提交的 Runnable - 改用 Agrona 队列
    private final ManyToOneConcurrentArrayQueue<Runnable> internalTaskQueue;
    private AgentRunner agentRunner;
    private RunloopAgent coreAgent;
    private volatile boolean running = false;
    @Getter
    private volatile Thread coreThread;
    // 外部事件源的回调，例如 Engine 的消息处理
    private IntSupplier externalEventDrainSupplier;
    private Runnable externalEventSourceNotifier;

    public Runloop(String name) {
        // 初始化内部任务队列，确保容量是2的幂
        int capacity = Integer.highestOneBit(DEFAULT_INTERNAL_QUEUE_CAPACITY);
        if (capacity < DEFAULT_INTERNAL_QUEUE_CAPACITY) {
            capacity <<= 1;
        }
        internalTaskQueue = new ManyToOneConcurrentArrayQueue<>(capacity);

        // 创建 Runloop 的核心 Agent
        coreAgent = new RunloopAgent(name);
    }

    /**
     * 注册外部事件源，例如 Engine 的消息队列。
     *
     * @param drainSupplier 提供外部事件处理的函数，返回处理的数量
     * @param notifier      用于通知 Runloop 有新事件到来的函数 (可选，用于优化唤醒)
     */
    public void registerExternalEventSource(IntSupplier drainSupplier, Runnable notifier) {
        externalEventDrainSupplier = drainSupplier;
        externalEventSourceNotifier = notifier;
        log.info("Runloop: 外部事件源已注册。");
    }

    public void start() {
        if (running) {
            log.warn("Runloop already started.");
            return;
        }
        running = true;

        IdleStrategy idleStrategy = new SleepingIdleStrategy(TimeUnit.MILLISECONDS.toNanos(1));

        agentRunner = new AgentRunner(idleStrategy,
                (throwable) -> log.error("Runloop AgentRunner 发生未捕获异常", throwable),
                null,
                coreAgent);

        Thread agentThread = new Thread(agentRunner, coreAgent.roleName() + "-Runner");
        agentThread.setDaemon(false);
        agentThread.setUncaughtExceptionHandler((thread, ex) -> log.error("Runloop AgentRunner 线程发生未捕获异常", ex));
        agentThread.start();
        coreThread = agentThread;

        log.info("Runloop started. Thread: {}", coreThread.getName());
    }

    /**
     * 提交一个任务到 Runloop 内部队列中。
     * 这个任务会在 Runloop 的专属线程上执行。
     *
     * @param task 要提交的任务
     */
    public void postTask(Runnable task) {
        if (!running) {
            log.warn("Runloop is not running, task will not be executed.");
            return;
        }
        boolean success = internalTaskQueue.offer(task); // 改用 offer
        if (!success) {
            log.warn("Runloop 内部任务队列已满，任务被丢弃。");
        }
    }

    public void shutdown() {
        if (!running) {
            log.warn("Runloop is not running, no need to shut down.");
            return;
        }
        running = false;
        if (agentRunner != null) {
            agentRunner.close();
            log.info("Runloop AgentRunner initiated shutdown.");
            try {
                if (coreThread != null && coreThread.isAlive()) {
                    coreThread.join(TimeUnit.SECONDS.toMillis(5));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Runloop shutdown interrupted.");
            }
        }
        log.info("Runloop shutdown completed.");
    }

    /**
     * Runloop 的核心 Agent 实现。
     * 负责在一个线程上轮询内部任务和外部事件。
     */
    private class RunloopAgent implements Agent {
        private final String name;

        public RunloopAgent(String name) {
            this.name = name;
        }

        @Override
        public String roleName() {
            return "TEN-Runloop-Agent-%s".formatted(name);
        }

        @Override
        public int doWork() {
            int workDone = 0;

            // 1. 优先处理内部任务队列中的任务 (来自 Runloop.postTask)
            Runnable internalTask = internalTaskQueue.poll();
            if (internalTask != null) {
                try {
                    internalTask.run();
                    workDone++;
                } catch (Exception e) {
                    log.error("RunloopAgent: 执行内部任务发生异常: {}", e.getMessage(), e);
                }
            }

            // 2. 处理外部注册的事件源 (例如 Engine 的入站消息)
            if (externalEventDrainSupplier != null) {
                try {
                    int externalWork = externalEventDrainSupplier.getAsInt();
                    workDone += externalWork;
                } catch (Exception e) {
                    log.error("RunloopAgent: 执行外部事件处理器发生异常: {}", e.getMessage(), e);
                }
            }

            return workDone;
        }

        @Override
        public void onStart() {
            log.info("{} started.", roleName());
        }

        @Override
        public void onClose() {
            log.info("{} closed.", roleName());
            internalTaskQueue.clear();
        }
    }
}