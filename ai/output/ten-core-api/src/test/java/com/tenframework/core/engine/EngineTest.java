package com.tenframework.core.engine;

import com.tenframework.core.Location;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

/**
 * Engine核心功能测试
 * 验证单线程事件循环和Agrona队列的基本功能
 */
class EngineTest {

    private Engine engine;
    private final String TEST_ENGINE_ID = "test-engine-001";

    @BeforeEach
    void setUp() {
        engine = new Engine(TEST_ENGINE_ID);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void testEngineCreation() {
        assertEquals(TEST_ENGINE_ID, engine.getEngineId());
        assertEquals(EngineState.CREATED, engine.getState());
        assertFalse(engine.isRunning());
        assertEquals(1, engine.getReferenceCount());
    }

    @Test
    void testEngineStartStop() throws InterruptedException {
        // 测试启动
        engine.start();

        // 等待启动完成
        Thread.sleep(100);

        assertEquals(EngineState.RUNNING, engine.getState());
        assertTrue(engine.isRunning());

        // 测试停止
        engine.stop();

        assertEquals(EngineState.STOPPED, engine.getState());
        assertFalse(engine.isRunning());
    }

    @Test
    void testMessageSubmission() throws InterruptedException {
        engine.start();
        Thread.sleep(100); // 等待启动完成

        // 创建测试消息
        Command command = new Command("test_command");
        command.setSourceLocation(new Location("app://test", "graph-1", "sender"));

        // 提交消息
        boolean success = engine.submitMessage(command);
        assertTrue(success, "消息提交应该成功");

        // 等待消息处理
        Thread.sleep(50);

        engine.stop();
    }

    @Test
    void testReferenceCountManagement() {
        assertEquals(1, engine.getReferenceCount());

        engine.retain();
        assertEquals(2, engine.getReferenceCount());

        engine.release();
        assertEquals(1, engine.getReferenceCount());

        // 释放最后一个引用应该自动停止Engine
        engine.release();
        assertEquals(0, engine.getReferenceCount());
    }

    @Test
    void testQueueCapacityAndSize() {
        // 测试队列容量（应该是2的幂）
        assertTrue(engine.getQueueCapacity() > 0);
        assertTrue((engine.getQueueCapacity() & (engine.getQueueCapacity() - 1)) == 0,
                "队列容量应该是2的幂");

        // 初始队列大小应该为0
        assertEquals(0, engine.getQueueSize());
    }

    @Test
    void testEngineThreadCheck() throws InterruptedException {
        // 在主线程中应该返回false
        assertFalse(engine.isEngineThread());

        engine.start();
        Thread.sleep(100); // 等待启动完成

        // 主线程仍然不是Engine线程
        assertFalse(engine.isEngineThread());

        engine.stop();
    }

    @Test
    void testMessageSubmissionWhenNotRunning() {
        // Engine未启动时提交消息应该失败
        Command command = new Command("test_command");
        boolean success = engine.submitMessage(command);
        assertFalse(success, "Engine未运行时消息提交应该失败");
    }

    @Test
    void testMultipleStartCalls() throws InterruptedException {
        engine.start();
        Thread.sleep(50);

        EngineState firstState = engine.getState();

        // 再次调用start应该被忽略
        engine.start();
        Thread.sleep(50);

        assertEquals(firstState, engine.getState());

        engine.stop();
    }
}