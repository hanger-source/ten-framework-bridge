package com.tenframework.core.runloop;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * Runloop 类负责线程管理和任务调度，对齐 C 语言的 ten_runloop。
 * 基于 Agrona AgentRunner 实现单线程事件循环，处理内部任务和外部 Agent 列表。
 *
 * 特性：
 *  - 批量消费内部任务（可配置批量大小）
 *  - 支持注册多个外部 Agent（在单线程内部按顺序调用它们的 doWork()）
 *  - 使用 BackoffIdleStrategy（折中自旋 -> yield -> sleep）
 *  - 提交任务后唤醒 runloop 线程以提高响应性
 *  - 生命周期 onStart / onClose 会转发到注册的外部 Agent
 */
@Slf4j
public class Runloop {

    private static final int DEFAULT_INTERNAL_QUEUE_CAPACITY = 1024;
    private static final int DEFAULT_INTERNAL_TASK_BATCH = 64;

    private final ManyToOneConcurrentArrayQueue<Runnable> taskQueue;
    private final RunloopAgent coreAgent;
    private final int internalTaskBatchSize;
    // 注册的外部 Agents（线程安全，读多写少场景适合 CopyOnWriteArrayList）
    private final List<Agent> externalAgents = new CopyOnWriteArrayList<>();
    private AgentRunner agentRunner;
    private volatile boolean running = false;
    @Getter
    private volatile Thread coreThread;
    // 可选的外部唤醒器（外部可在有新事件时调用该 Runnable）
    private volatile Runnable externalEventSourceNotifier;

    /**
     * 使用默认配置
     *
     * @param name runloop 名称（用于线程名）
     */
    public Runloop(String name) {
        this(name, DEFAULT_INTERNAL_QUEUE_CAPACITY, DEFAULT_INTERNAL_TASK_BATCH);
    }

    /**
     * 可配置构造
     *
     * @param name                  名称
     * @param requestedCapacity     初始容量（会向上调整为 2 的幂）
     * @param internalTaskBatchSize 每轮最多处理多少个内部任务
     */
    public Runloop(String name, int requestedCapacity, int internalTaskBatchSize) {
        Objects.requireNonNull(name, "name");

        int capacity = Math.max(1, Integer.highestOneBit(requestedCapacity));
        if (capacity < requestedCapacity) {
            capacity <<= 1;
        }
        taskQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
        this.internalTaskBatchSize = Math.max(1, internalTaskBatchSize);
        coreAgent = new RunloopAgent(name);
    }

    /**
     * 注册一个外部 Agent。外部 Agent 的 doWork/onStart/onClose 将在 Runloop 的线程中被调用。
     *
     * @param agent 非空的 Agent
     */
    public void registerExternalAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent");
        externalAgents.add(agent);
        log.info("Runloop: 注册外部 Agent -> {}", agent.roleName());
    }

    /**
     * 注销一个外部 Agent（如果已注册）。
     *
     * @param agent 要注销的 Agent
     * @return 如果存在并移除返回 true，否则 false
     */
    public boolean unregisterExternalAgent(Agent agent) {
        boolean removed = externalAgents.remove(agent);
        if (removed) {
            log.info("Runloop: 注销外部 Agent -> {}", agent.roleName());
        }
        return removed;
    }

    /**
     * 设置外部唤醒器（可选）。当外部有新事件时，可调用该 notifier 或直接调用 runloop.wakeup()。
     *
     * @param notifier Runnable，允许为空
     */
    public void setExternalEventSourceNotifier(Runnable notifier) {
        externalEventSourceNotifier = notifier;
    }

    /**
     * 启动 Runloop（起一个线程运行 AgentRunner）。
     */
    public void start() {
        if (running) {
            log.warn("Runloop already started.");
            return;
        }
        running = true;

        IdleStrategy idleStrategy = new BackoffIdleStrategy(
            1, // maxSpins
            1, // maxYields
            TimeUnit.NANOSECONDS.toNanos(50), // minParkPeriodNs
            TimeUnit.MICROSECONDS.toNanos(100) // maxParkPeriodNs
        );

        agentRunner = new AgentRunner(
            idleStrategy,
            (throwable) -> log.error("Runloop AgentRunner 未捕获异常", throwable),
            null,
            coreAgent
        );

        Thread agentThread = new Thread(agentRunner, "%s-RunLoop".formatted(coreAgent.roleName()));
        agentThread.setDaemon(false);
        agentThread.setUncaughtExceptionHandler((thread, ex) -> log.error("Runloop AgentRunner 线程未捕获异常", ex));
        agentThread.start();
        coreThread = agentThread;

        log.info("Runloop started. Thread: {}", coreThread.getName());
    }

    /**
     * 提交任务到内部队列（会在 Runloop 专属线程上执行）。
     *
     * @param task 非空 Runnable
     * @return true 表示入队成功，false 表示队列满或尚未启动
     */
    public boolean postTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (!running) {
            log.warn("Runloop is not running, task will not be executed.");
            return false;
        }

        boolean success = taskQueue.offer(task);
        if (!success) {
            log.warn("Runloop 内部任务队列已满，任务被丢弃。");
            return false;
        }

        // 唤醒 runloop 线程以尽快处理任务
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
        return true;
    }

    /**
     * 唤醒 runloop（外部也可以直接调用此方法）。
     */
    public void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * 安全关闭 Runloop。会调用 AgentRunner.close() 并等待线程退出（短超时）。
     */
    public void shutdown() {
        if (!running) {
            log.warn("Runloop is not running, no need to shut down.");
            return;
        }
        running = false;

        try {
            if (agentRunner != null) {
                agentRunner.close();
            }
        } catch (Exception e) {
            log.error("Runloop AgentRunner close 异常", e);
        }

        // 唤醒以确保正在 park 的线程能尽快退出
        wakeup();

        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(TimeUnit.SECONDS.toMillis(3));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Runloop shutdown 被中断");
        }

        log.info("Runloop shutdown completed.");
    }

    /**
     * 内部 Agent，负责合并内部队列任务与所有外部 Agent 的 doWork 调用。
     */
    private class RunloopAgent implements Agent {
        private final String name;

        RunloopAgent(String name) {
            this.name = name;
        }

        @Override
        public String roleName() {
            return "TEN-Runloop-Agent-%s".formatted(name);
        }

        @Override
        public int doWork() {
            int workDone = 0;

            // 1) 批量处理内部任务
            int processed = 0;
            Runnable r;
            while (processed < internalTaskBatchSize) {
                r = taskQueue.poll();
                if (r == null) {
                    break;
                }
                try {
                    r.run();
                } catch (Throwable e) {
                    log.error("RunloopAgent: 执行内部任务异常", e);
                }
                processed++;
                workDone++;
            }

            // 2) 调用所有外部 Agents 的 doWork()
            if (!externalAgents.isEmpty()) {
                for (Agent agent : externalAgents) {
                    try {
                        int w = agent.doWork();
                        if (w > 0) {
                            workDone += w;
                        }
                    } catch (Throwable e) {
                        try {
                            log.error("RunloopAgent: 外部 Agent {} 执行异常", agent.roleName(), e);
                        } catch (Throwable ignore) {
                            // 防止 agent.roleName() 本身抛异常影响主循环
                            log.error("RunloopAgent: 外部 Agent 执行异常 (无法获取 roleName)", e);
                        }
                    }
                }
            }

            return workDone;
        }

        @Override
        public void onStart() {
            log.info("{} started.", roleName());
            // 转发 onStart 到外部 Agents，保护性捕获异常
            for (Agent agent : externalAgents) {
                try {
                    agent.onStart();
                } catch (Throwable e) {
                    try {
                        log.error("RunloopAgent: 外部 Agent {} onStart 异常", agent.roleName(), e);
                    } catch (Throwable ignore) {
                        log.error("RunloopAgent: 外部 Agent onStart 异常 (无法获取 roleName)", e);
                    }
                }
            }
        }

        @Override
        public void onClose() {
            log.info("{} closed.", roleName());
            // 清理内部队列（注意：如果队列非常大，这里会花时间）
            taskQueue.clear();

            // 转发 onClose 到外部 Agents
            for (Agent agent : externalAgents) {
                try {
                    agent.onClose();
                } catch (Throwable e) {
                    try {
                        log.error("RunloopAgent: 外部 Agent {} onClose 异常", agent.roleName(), e);
                    } catch (Throwable ignore) {
                        log.error("RunloopAgent: 外部 Agent onClose 异常 (无法获取 roleName)", e);
                    }
                }
            }
        }
    }
}