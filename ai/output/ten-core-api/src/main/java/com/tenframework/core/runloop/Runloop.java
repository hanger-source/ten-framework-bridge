package com.tenframework.core.runloop;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntSupplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * Runloop 类负责线程管理和任务调度，对齐 C 语言的 ten_runloop。
 * 基于 Agrona AgentRunner 实现单线程事件循环，处理内部任务和外部事件源。
 *
 * 主要改进点：
 * 1. 批量消费内部任务，避免每次只处理一个任务造成延迟。
 * 2. 使用 BackoffIdleStrategy 作自旋->yield->sleep 折中，降低忙等开销同时保持较低延迟。
 * 3. 在提交任务时唤醒 runloop 线程（使用 LockSupport.unpark），提高响应性。
 * 4. 更安全的 shutdown，doWork respect running 标志以便快速退出。
 */
@Slf4j
public class Runloop {

    private static final int DEFAULT_INTERNAL_QUEUE_CAPACITY = 1024; // 默认容量（2 的幂）
    private static final int DEFAULT_INTERNAL_TASK_BATCH = 64; // 每次批量处理任务上限

    private final ManyToOneConcurrentArrayQueue<Runnable> taskQueue;
    private final RunloopAgent coreAgent;
    // 每次最多处理的内部任务数（批量）
    private final int internalTaskBatchSize;
    private AgentRunner agentRunner;
    private volatile boolean running = false;
    @Getter
    private volatile Thread coreThread;
    // 外部事件源的回调，例如 Engine 的消息处理
    private volatile IntSupplier externalEventDrainSupplier;
    // 可选的外部唤醒器（外部事件来源可以在有新事件时调用该 Runnable 来唤醒 runloop）
    private volatile Runnable externalEventSourceNotifier;

    /**
     * 使用默认配置（ManyToOne 队列，默认容量，batch=64）
     *
     * @param name runloop 名称（用于线程名）
     */
    public Runloop(String name) {
        this(name, DEFAULT_INTERNAL_QUEUE_CAPACITY, DEFAULT_INTERNAL_TASK_BATCH);
    }

    /**
     * 可配置版本
     *
     * @param name                  名称
     * @param requestedCapacity     初始容量（会调整为 >= 并为 2 的幂）
     * @param internalTaskBatchSize 每个 loop 最多处理多少个内部任务
     */
    public Runloop(String name, int requestedCapacity, int internalTaskBatchSize) {
        Objects.requireNonNull(name, "name");

        int capacity = Math.max(1, Integer.highestOneBit(requestedCapacity));
        if (capacity < requestedCapacity) {
            capacity <<= 1;
        }
        taskQueue = new ManyToOneConcurrentArrayQueue<Runnable>(capacity);
        this.internalTaskBatchSize = Math.max(1, internalTaskBatchSize);
        coreAgent = new RunloopAgent(name);
    }

    /**
     * 注册外部事件源，例如 Engine 的消息队列。
     *
     * @param drainSupplier 提供外部事件处理的函数，返回处理的数量
     * @param notifier      用于通知 Runloop 有新事件到来的函数 (可选，用于唤醒)
     */
    public void registerExternalEventSource(IntSupplier drainSupplier, Runnable notifier) {
        externalEventDrainSupplier = drainSupplier;
        externalEventSourceNotifier = notifier;
        log.info("Runloop: 外部事件源已注册。");
    }

    /**
     * 启动 Runloop 线程与 AgentRunner。
     */
    public void start() {
        if (running) {
            log.warn("Runloop already started.");
            return;
        }
        running = true;

        // BackoffIdleStrategy: 自旋 -> yield -> sleep (折中方案)
        IdleStrategy idleStrategy = new BackoffIdleStrategy(
            1, // maxSpins
            1, // maxYields
            TimeUnit.NANOSECONDS.toNanos(50), // minParkPeriodNs (微小睡眠)
            TimeUnit.MICROSECONDS.toNanos(100) // maxParkPeriodNs
        );

        agentRunner = new AgentRunner(
            idleStrategy,
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
     * 提交一个任务到 Runloop 内部队列中（会在 Runloop 专属线程上执行）。
     *
     * @param task 要提交的任务
     * @return 如果成功入队返回 true，队列满或未运行返回 false
     */
    public boolean postTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (!running) {
            log.warn("Runloop is not running, task will not be executed.");
            return false;
        }

        boolean success;
        success = taskQueue.offer(task);

        if (!success) {
            log.warn("Runloop 内部任务队列已满，任务被丢弃。");
            return false;
        }

        // 唤醒核心线程以尽快处理新任务（如果核心线程存在则 unpark）
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
        return true;
    }

    /**
     * 安全关闭 Runloop。
     * 注意：如果内部任务或外部事件处理器长时间阻塞，可能会延长关闭时间。
     */
    public void shutdown() {
        if (!running) {
            log.warn("Runloop is not running, no need to shut down.");
            return;
        }
        running = false;

        try {
            if (agentRunner != null) {
                agentRunner.close(); // 请求关闭 AgentRunner
            }
        } catch (Exception e) {
            log.error("Runloop AgentRunner close error", e);
        }

        // 唤醒以确保正在 park 的线程能尽快退出
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }

        // 等待线程退出（短超时）
        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(TimeUnit.SECONDS.toMillis(3));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Runloop shutdown interrupted.");
        }

        log.info("Runloop shutdown completed.");
    }

    /**
     * 外部唤醒 runloop 的便捷方法（例如外部事件来源有新事件时，可调用本方法或提供 notifier）。
     * 这个方法只是调用 LockSupport.unpark(coreThread)。
     */
    public void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
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

        /**
         * doWork 会被 AgentRunner 调用在循环中。返回值为本次调用处理的“工作量”。
         */
        @Override
        public int doWork() {
            int workDone = 0;

            // 优先批量处理内部任务，避免每次只处理一个任务导致延迟堆积
            int processed = 0;
            Runnable r;
            while (processed < internalTaskBatchSize) {
                ManyToOneConcurrentArrayQueue<Runnable> q = taskQueue;
                r = q.poll();
                if (r == null) {
                    break;
                }
                try {
                    r.run();
                } catch (Throwable e) {
                    log.error("RunloopAgent: 执行内部任务发生异常", e);
                }
                processed++;
                workDone++;
            }

            // 处理外部注册的事件源 (例如 Engine 的入站消息)
            if (externalEventDrainSupplier != null) {
                try {
                    int externalWork = externalEventDrainSupplier.getAsInt();
                    // externalWork 表示处理到的事件数量（建议外部实现返回实际处理数量）
                    if (externalWork > 0) {
                        workDone += externalWork;
                    }
                } catch (Throwable e) {
                    log.error("RunloopAgent: 执行外部事件处理器发生异常", e);
                }
            }

            // 如果没有工作并且 runloop 已经被置为 false，则返回 0 以便 AgentRunner 可以结束（close 时会停止）
            return workDone;
        }

        @Override
        public void onStart() {
            log.info("{} started.", roleName());
        }

        @Override
        public void onClose() {
            log.info("{} closed.", roleName());
            // 清理队列（注意：清理时如果队列很大会阻塞当前线程）
            taskQueue.clear();
        }
    }
}