package com.tenframework.core.extension;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EchoExtension单元测试
 * 验证Extension生命周期和消息处理功能
 */
@DisplayName("EchoExtension测试")
class EchoExtensionTest {

    private Engine engine;
    private SimpleEchoExtension echoExtension;
    private static final String ENGINE_ID = "test-engine";
    private static final String EXTENSION_NAME = "test-echo";

    @BeforeEach
    void setUp() {
        engine = new Engine(ENGINE_ID);
        echoExtension = new SimpleEchoExtension();
    }

    @Test
    @DisplayName("测试Extension注册和生命周期")
    void testExtensionRegistrationAndLifecycle() {
        // 准备配置属性
        Map<String, Object> properties = Map.of(
                "echo.prefix", "TEST_",
                "test.property", "test_value");

        // 注册Extension
        boolean success = engine.registerExtension(EXTENSION_NAME, echoExtension, properties, "test://app"); // 添加appUri
        assertTrue(success, "Extension注册应该成功");

        // 验证Extension已注册
        assertTrue(engine.getExtension(EXTENSION_NAME).isPresent(), "Extension应该存在");
        assertTrue(engine.getExtensionContext(EXTENSION_NAME).isPresent(), "ExtensionContext应该存在");
        assertEquals(1, engine.getExtensionCount(), "Extension数量应该为1");

        // 注销Extension
        boolean unregisterSuccess = engine.unregisterExtension(EXTENSION_NAME);
        assertTrue(unregisterSuccess, "Extension注销应该成功");

        // 验证Extension已注销
        assertFalse(engine.getExtension(EXTENSION_NAME).isPresent(), "Extension应该不存在");
        assertFalse(engine.getExtensionContext(EXTENSION_NAME).isPresent(), "ExtensionContext应该不存在");
        assertEquals(0, engine.getExtensionCount(), "Extension数量应该为0");
    }

    @Test
    @DisplayName("测试Extension重复注册")
    void testDuplicateExtensionRegistration() {
        // 第一次注册
        boolean firstSuccess = engine.registerExtension(EXTENSION_NAME, echoExtension, Map.of(), "test://app"); // 添加appUri
        assertTrue(firstSuccess, "第一次注册应该成功");

        // 第二次注册相同名称
        SimpleEchoExtension anotherExtension = new SimpleEchoExtension();
        boolean secondSuccess = engine.registerExtension(EXTENSION_NAME, anotherExtension, Map.of(), "test://app"); // 添加appUri
        assertFalse(secondSuccess, "重复注册应该失败");

        // 验证只有第一个Extension存在
        assertEquals(1, engine.getExtensionCount(), "Extension数量应该为1");
    }

    @Test
    @DisplayName("测试Extension注销不存在的Extension")
    void testUnregisterNonExistentExtension() {
        boolean success = engine.unregisterExtension("non-existent");
        assertFalse(success, "注销不存在的Extension应该失败");
        assertEquals(0, engine.getExtensionCount(), "Extension数量应该为0");
    }

    @Test
    @DisplayName("测试Engine启动和停止")
    void testEngineStartAndStop() {
        // 注册Extension
        engine.registerExtension(EXTENSION_NAME, echoExtension, Map.of(), "test://app"); // 添加appUri

        // 启动Engine
        engine.start();
        assertTrue(engine.isRunning(), "Engine应该正在运行");

        // 停止Engine
        engine.stop();
        assertFalse(engine.isRunning(), "Engine应该已停止");

        // 验证Extension已清理
        assertEquals(0, engine.getExtensionCount(), "Engine停止后Extension应该被清理");
    }

    @Test
    @DisplayName("测试消息队列功能")
    void testMessageQueue() {
        engine.start();

        // 测试队列容量
        assertTrue(engine.getQueueCapacity() > 0, "队列容量应该大于0");
        assertEquals(0, engine.getQueueSize(), "初始队列大小应该为0");

        // 提交一些消息
        for (int i = 0; i < 10; i++) {
            // 使用 Data 的工厂方法，例如 Data.text 或 Data.binary
            Data data = Data.text("test-data-" + i, "test content");
            boolean success = engine.submitMessage(data);
            assertTrue(success, "消息提交应该成功");
        }

        // 验证队列大小
        assertEquals(10, engine.getQueueSize(), "队列大小应该为10");

        engine.stop();
    }

    @Test
    @DisplayName("测试ExtensionContext属性访问")
    void testExtensionContextPropertyAccess() {
        Map<String, Object> properties = Map.of(
                "string.property", "test_string",
                "int.property", 42,
                "boolean.property", true);

        engine.registerExtension(EXTENSION_NAME, echoExtension, properties, "test://app"); // 添加appUri

        var contextOpt = engine.getExtensionContext(EXTENSION_NAME);
        assertTrue(contextOpt.isPresent(), "ExtensionContext应该存在");

        var context = contextOpt.get();

        // 测试属性访问
        assertEquals("test_string", context.getProperty("string.property", String.class).orElse(null));
        assertEquals(42, context.getProperty("int.property", Integer.class).orElse(null));
        assertEquals(true, context.getProperty("boolean.property", Boolean.class).orElse(null));

        // 测试不存在的属性
        assertTrue(context.getProperty("non.existent", String.class).isEmpty());

        // 测试类型不匹配
        assertTrue(context.getProperty("string.property", Integer.class).isEmpty());
    }
}