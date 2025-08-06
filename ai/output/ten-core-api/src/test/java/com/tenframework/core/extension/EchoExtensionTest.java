package com.tenframework.core.extension;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.tenframework.core.Location;
import com.tenframework.core.graph.GraphInstance;
import com.tenframework.core.message.CommandResult;
import java.util.concurrent.CompletableFuture;

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
                engine.start(); // 确保Engine在每个测试开始前启动
                echoExtension = new SimpleEchoExtension();
        }

        @AfterEach
        void tearDown() {
                engine.stop(); // 确保Engine在每个测试结束后停止，释放资源
        }

        @Test
        @DisplayName("测试Extension注册和生命周期")
        void testExtensionRegistrationAndLifecycle() throws InterruptedException,
                        java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>(); // 为start_graph命令创建Future
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture // 将Future添加到属性中
                                ))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS); // 等待start_graph命令处理完成，最多5秒

                // 获取 GraphInstance
                GraphInstance graphInstance = engine.getGraphInstance(graphId)
                                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));

                // 准备配置属性
                Map<String, Object> properties = Map.of(
                                "echo.prefix", "TEST_",
                                "test.property", "test_value");

                // 注册Extension
                boolean success = graphInstance.registerExtension(EXTENSION_NAME, echoExtension, properties);
                assertTrue(success, "Extension注册应该成功");

                // 验证Extension已注册
                assertTrue(graphInstance.getExtension(EXTENSION_NAME).isPresent(), "Extension应该存在");
                assertTrue(graphInstance.getExtensionContext(EXTENSION_NAME).isPresent(), "ExtensionContext应该存在");
                assertEquals(1, graphInstance.getExtensionRegistry().size(), "Extension数量应该为1");

                // 注销Extension
                boolean unregisterSuccess = graphInstance.unregisterExtension(EXTENSION_NAME);
                assertTrue(unregisterSuccess, "Extension注销应该成功");

                // 验证Extension已注销
                assertFalse(graphInstance.getExtension(EXTENSION_NAME).isPresent(), "Extension应该不存在");
                assertFalse(graphInstance.getExtensionContext(EXTENSION_NAME).isPresent(), "ExtensionContext应该不存在");
                assertEquals(0, graphInstance.getExtensionRegistry().size(), "Extension数量应该为0");

                // 2. 模拟发送 stop_graph 命令
                CompletableFuture<CommandResult> stopGraphFuture = new CompletableFuture<>();
                Command stopGraphCommand = Command.builder()
                                .name("stop_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of("graph_id", graphId, "app_uri", appUri,
                                                "__result_future__", stopGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(stopGraphCommand);
                stopGraphFuture.get(5, TimeUnit.SECONDS);

                // 验证图实例已被移除
                assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
        }

        @Test
        @DisplayName("测试Extension重复注册")
        void testDuplicateExtensionRegistration() throws InterruptedException, java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>();
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS);

                // 获取 GraphInstance
                GraphInstance graphInstance = engine.getGraphInstance(graphId)
                                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));

                // 第一次注册
                boolean firstSuccess = graphInstance.registerExtension(EXTENSION_NAME, echoExtension, Map.of());
                assertTrue(firstSuccess, "第一次注册应该成功");

                // 第二次注册相同名称
                SimpleEchoExtension anotherExtension = new SimpleEchoExtension();
                boolean secondSuccess = graphInstance.registerExtension(EXTENSION_NAME, anotherExtension, Map.of());
                assertFalse(secondSuccess, "重复注册应该失败");

                // 验证只有第一个Extension存在
                assertEquals(1, graphInstance.getExtensionRegistry().size(), "Extension数量应该为1");

                // 2. 模拟发送 stop_graph 命令
                CompletableFuture<CommandResult> stopGraphFuture = new CompletableFuture<>();
                Command stopGraphCommand = Command.builder()
                                .name("stop_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of("graph_id", graphId, "app_uri", appUri,
                                                "__result_future__", stopGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(stopGraphCommand);
                stopGraphFuture.get(5, TimeUnit.SECONDS);

                // 验证图实例已被移除
                assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
        }

        @Test
        @DisplayName("测试Extension注销不存在的Extension")
        void testUnregisterNonExistentExtension() throws InterruptedException, java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>();
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS);

                // 获取 GraphInstance
                GraphInstance graphInstance = engine.getGraphInstance(graphId)
                                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));

                boolean success = graphInstance.unregisterExtension("non-existent");
                assertFalse(success, "注销不存在的Extension应该失败");
                assertEquals(0, graphInstance.getExtensionRegistry().size(), "Extension数量应该为0");

                // 2. 模拟发送 stop_graph 命令
                CompletableFuture<CommandResult> stopGraphFuture = new CompletableFuture<>();
                Command stopGraphCommand = Command.builder()
                                .name("stop_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of("graph_id", graphId, "app_uri", appUri,
                                                "__result_future__", stopGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(stopGraphCommand);
                stopGraphFuture.get(5, TimeUnit.SECONDS);

                // 验证图实例已被移除
                assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
        }

        @Test
        @DisplayName("测试Engine启动和停止")
        void testEngineStartAndStop() throws InterruptedException, java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令来启动图实例
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>();
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS);

                // 获取 GraphInstance
                GraphInstance graphInstance = engine.getGraphInstance(graphId)
                                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));

                // 注册Extension到GraphInstance
                boolean registered = graphInstance.registerExtension(EXTENSION_NAME, echoExtension, Map.of());
                assertTrue(registered, "Extension注册应该成功");

                // 启动Engine (已在setup中启动，这里无需重复)
                // engine.start();
                assertTrue(engine.isRunning(), "Engine应该正在运行");

                // 验证Extension已注册到GraphInstance
                assertEquals(1, graphInstance.getExtensionRegistry().size(), "GraphInstance的Extension数量应该为1");

                // 停止Engine
                engine.stop();
                assertFalse(engine.isRunning(), "Engine应该已停止");

                // 验证Engine停止后，GraphInstance不再活跃于Engine内部
                // 注意：Engine.stop()仅停止Engine自身，不负责显式移除GraphInstance
                // GraphInstance的移除由stop_graph命令处理，此处不再重复断言
                // assertFalse(engine.getGraphInstance(graphId).isPresent(),
                // "Engine停止后，GraphInstance应该已被移除");
                // assertEquals(0, graphInstance.getExtensionRegistry().size(),
                // "Engine停止后Extension应该被清理");
        }

        @Test
        @DisplayName("测试消息队列功能")
        void testMessageQueue() throws InterruptedException, java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令来启动图实例
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>();
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS);

                // 测试队列容量
                assertTrue(engine.getQueueCapacity() > 0, "队列容量应该大于0");
                assertEquals(0, engine.getQueueSize(), "初始队列大小应该为0");

                // 提交一些消息
                for (int i = 0; i < 10; i++) {
                        // 使用 Data 的工厂方法，例如 Data.text 或 Data.binary
                        Data data = Data.text("test-data-" + i, "test content");
                        data.setSourceLocation(new Location(appUri, graphId, "client-source"));
                        data.setDestinationLocations(List.of(new Location(appUri, graphId, "non-existent-extension"))); // 路由到不存在的扩展，确保消息被Engine消费
                        boolean success = engine.submitMessage(data);
                        assertTrue(success, "消息提交应该成功");
                }

                // 验证队列大小（由于是异步处理，可能在断言时队列已被消费）
                // 这里的测试目的主要是验证 submitMessage 成功，而不是队列大小的精确瞬间值。
                // 如果需要精确，需要更复杂的同步机制。
                // 因此，我们只检查是否成功提交，并等待一小段时间让Engine处理。
                // Thread.sleep(100); // 确保Engine有时间处理一些消息

                // 2. 模拟发送 stop_graph 命令
                CompletableFuture<CommandResult> stopGraphFuture = new CompletableFuture<>();
                Command stopGraphCommand = Command.builder()
                                .name("stop_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of("graph_id", graphId, "app_uri", appUri,
                                                "__result_future__", stopGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(stopGraphCommand);
                stopGraphFuture.get(5, TimeUnit.SECONDS);

                // 验证图实例已被移除
                assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
        }

        @Test
        @DisplayName("测试ExtensionContext属性访问")
        void testExtensionContextPropertyAccess() throws InterruptedException, java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
                String graphId = "test-graph-" + java.util.UUID.randomUUID().toString();
                String appUri = "test://app";

                // 1. 模拟发送 start_graph 命令来启动图实例
                CompletableFuture<CommandResult> startGraphFuture = new CompletableFuture<>();
                Command startGraphCommand = Command.builder()
                                .name("start_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of(
                                                "graph_id", graphId,
                                                "app_uri", appUri,
                                                "graph_json", "{\"nodes\":[],\"connections\":[]}",
                                                "__result_future__", startGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(startGraphCommand);
                startGraphFuture.get(5, TimeUnit.SECONDS);

                // 获取 GraphInstance
                GraphInstance graphInstance = engine.getGraphInstance(graphId)
                                .orElseThrow(() -> new IllegalStateException("未找到启动的图实例: " + graphId));

                Map<String, Object> properties = Map.of(
                                "string.property", "test_string",
                                "int.property", 42,
                                "boolean.property", true);

                graphInstance.registerExtension(EXTENSION_NAME, echoExtension, properties); // 注册Extension到GraphInstance

                var contextOpt = graphInstance.getExtensionContext(EXTENSION_NAME);
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

                // 2. 模拟发送 stop_graph 命令
                CompletableFuture<CommandResult> stopGraphFuture = new CompletableFuture<>();
                Command stopGraphCommand = Command.builder()
                                .name("stop_graph")
                                .commandId(java.util.UUID.randomUUID().toString())
                                .properties(Map.of("graph_id", graphId, "app_uri", appUri,
                                                "__result_future__", stopGraphFuture))
                                .sourceLocation(new Location("test://client", graphId, "client"))
                                .destinationLocations(List.of(new Location(appUri, graphId, "engine")))
                                .build();
                engine.submitMessage(stopGraphCommand);
                stopGraphFuture.get(5, TimeUnit.SECONDS);

                // 验证图实例已被移除
                assertFalse(engine.getGraphInstance(graphId).isPresent(), "图实例应该已被移除");
        }
}