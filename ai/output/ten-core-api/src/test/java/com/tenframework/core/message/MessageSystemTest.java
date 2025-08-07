package com.tenframework.core.message;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息系统测试类
 * 验证使用现代Java特性优化后的消息系统功能
 */
class MessageSystemTest {

    @Test
    @DisplayName("测试Location系统功能")
    void testLocationSystem() {
        // 测试Location创建和验证
        Location location = new Location("app://test", "graph-123", "extension1");
        assertEquals("app://test", location.appUri());
        assertEquals("graph-123", location.graphId());
        assertEquals("extension1", location.extensionName());

        // 测试Location字符串转换
        assertEquals("app://test/graph-123/extension1", location.toString());
        Location parsed = Location.fromString("app://test/graph-123/extension1");
        assertEquals(location, parsed);

        // 测试同图检查
        Location sameGraph = new Location("app://test", "graph-123", "extension2");
        assertTrue(location.isInSameGraph(sameGraph));
    }

    @Test
    @DisplayName("测试Command消息功能")
    void testCommandMessage() {
        // 创建命令
        Command cmd = Command.builder().name("test_command").build();
        cmd.setArg("param1", "value1");
        cmd.setArg("param2", 42);

        // 测试基本属性
        assertEquals(MessageType.COMMAND, cmd.getType());
        assertEquals("test_command", cmd.getName());
        assertNotNull(cmd.getCommandId());
        assertTrue(cmd.checkIntegrity());

        // 测试现代Java特性的参数获取
        Optional<String> param1 = cmd.getArg("param1", String.class);
        assertTrue(param1.isPresent());
        assertEquals("value1", param1.get());

        Optional<Integer> param2 = cmd.getArg("param2", Integer.class);
        assertTrue(param2.isPresent());
        assertEquals(42, param2.get());

        // 测试带默认值的参数获取
        String param3 = cmd.getArg("param3", String.class, "default");
        assertEquals("default", param3);

        // 测试类型转换失败的情况
        Optional<String> wrongType = cmd.getArg("param2", String.class);
        assertTrue(wrongType.isEmpty());
    }

    @Test
    @DisplayName("测试CommandResult消息功能")
    void testCommandResultMessage() {
        // 创建成功结果
        CommandResult success = CommandResult.success(java.util.UUID.randomUUID().getMostSignificantBits());
        success.setResultValue("result", "success");

        assertTrue(success.isSuccess());
        assertFalse(success.isError());
        assertTrue(success.isFinal());

        // 测试现代Java特性的结果获取
        Optional<String> result = success.getResultValue("result", String.class);
        assertTrue(result.isPresent());
        assertEquals("success", result.get());

        // 创建错误结果
        CommandResult error = CommandResult.error(java.util.UUID.randomUUID().getMostSignificantBits(),
                "Something went wrong", 500);
        assertFalse(error.isSuccess());
        assertTrue(error.isError());
        assertEquals("Something went wrong", error.getError());
        assertEquals(500, error.getErrorCode());

        // 创建流式结果
        CommandResult streaming = CommandResult.streaming(java.util.UUID.randomUUID().getMostSignificantBits(),
                Map.of("chunk", "data"));
        assertFalse(streaming.isFinal());
    }

    @Test
    @DisplayName("测试Data消息功能")
    void testDataMessage() {
        // 创建文本数据
        Data textData = Data.text("test_data", "Hello World");
        assertEquals(MessageType.DATA, textData.getType());
        assertEquals("test_data", textData.getName());
        assertEquals("text/plain", textData.getContentType());
        assertTrue(textData.hasData());
        assertFalse(textData.isEmpty());

        // 验证数据内容
        byte[] bytes = textData.getDataBytes();
        assertEquals("Hello World", new String(bytes));

        // 创建JSON数据
        Data jsonData = Data.json("json_data", "{\"key\":\"value\"}");
        assertEquals("application/json", jsonData.getContentType());

        // 创建二进制数据
        byte[] binaryBytes = { 1, 2, 3, 4, 5 };
        Data binaryData = Data.binary("binary_data", binaryBytes);
        assertEquals("application/octet-stream", binaryData.getContentType());
        assertArrayEquals(binaryBytes, binaryData.getDataBytes());
    }

    @Test
    @DisplayName("测试AudioFrame消息功能")
    void testAudioFrameMessage() {
        // 创建音频帧
        byte[] audioData = new byte[1024];
        // 将 byte[] 转换为 ByteBuf
        io.netty.buffer.ByteBuf audioBuf = Unpooled.wrappedBuffer(audioData);
        AudioFrame frame = new AudioFrame("audio_stream", audioBuf, 16000, 1, 16);

        assertEquals(MessageType.AUDIO_FRAME, frame.getType());
        assertEquals(16000, frame.getSampleRate());
        assertEquals(1, frame.getChannels());
        assertEquals(16, frame.getBitsPerSample()); // 恢复 getBitsPerSample
        assertTrue(frame.hasData());
        assertTrue(frame.checkIntegrity());

        // 计算音频属性
        assertTrue(frame.getDurationMs() > 0);
        assertEquals(32000, frame.getBytesPerSecond()); // 16000 * 1 * 2

        // 创建静音帧
        AudioFrame silence = AudioFrame.silence("silence", 100, 8000, 2, 16); // 恢复 bitsPerSample 参数
        assertEquals(8000, silence.getSampleRate());
        assertEquals(2, silence.getChannels());
        assertTrue(silence.getDurationMs() >= 100);
    }

    @Test
    @DisplayName("测试VideoFrame消息功能")
    void testVideoFrameMessage() {
        // 创建视频帧
        byte[] videoData = new byte[2048];
        // 将 byte[] 转换为 ByteBuf
        io.netty.buffer.ByteBuf videoBuf = Unpooled.wrappedBuffer(videoData);
        VideoFrame frame = new VideoFrame("video_stream", videoBuf, 1920, 1080, "YUV420P"); // 恢复 pixelFormat

        assertEquals(MessageType.VIDEO_FRAME, frame.getType());
        assertEquals(1920, frame.getWidth());
        assertEquals(1080, frame.getHeight());
        assertEquals("YUV420P", frame.getPixelFormat()); // 恢复 getPixelFormat
        assertTrue(frame.hasData());
        assertTrue(frame.checkIntegrity());

        // 测试视频属性
        assertEquals("1920x1080", frame.getResolution());
        assertTrue(frame.getAspectRatio() > 0);
        assertTrue(frame.getUncompressedSize() > 0);
        assertTrue(frame.getCompressionRatio() > 1.0);

        // 测试帧类型
        frame.setFrameType("I");
        assertTrue(frame.isKeyFrame());
        assertFalse(frame.isPFrame());
        assertFalse(frame.isBFrame());
    }

    @Test
    @DisplayName("测试消息克隆功能")
    void testMessageCloning() {
        // 测试Command克隆
        Command original = Command.builder().name("test_cmd").build();
        original.setArg("param", "value");
        original.setProperty("prop", "test");

        Command cloned = null;
        try {
            cloned = original.clone();
        } catch (CloneNotSupportedException e) {
            fail("消息克隆失败: " + e.getMessage());
        }
        assertNotNull(cloned, "克隆对象不应为空");
        assertNotEquals(original.getCommandId(), cloned.getCommandId()); // 新的命令ID
        assertEquals(original.getName(), cloned.getName());
        assertEquals(original.getArg("param"), cloned.getArg("param"));
        assertEquals(original.getProperty("prop"), cloned.getProperty("prop"));

        // 修改原始消息不应影响克隆
        original.setArg("param", "changed");
        assertNotEquals(original.getArg("param"), cloned.getArg("param"));
    }

    @Test
    @DisplayName("测试消息工具类功能")
    void testMessageUtils() {
        // 测试字段验证
        assertTrue(MessageUtils.validateStringField("valid", "test"));
        assertFalse(MessageUtils.validateStringField("", "test"));
        assertFalse(MessageUtils.validateStringField(null, "test"));
        assertFalse(MessageUtils.validateStringField("   ", "test"));

        // 测试数值验证
        assertTrue(MessageUtils.validatePositiveNumber(1, "test"));
        assertTrue(MessageUtils.validatePositiveNumber(0.1, "test"));
        assertFalse(MessageUtils.validatePositiveNumber(0, "test"));
        assertFalse(MessageUtils.validatePositiveNumber(-1, "test"));

        // 测试安全类型转换
        Optional<String> result = MessageUtils.safeCast("test", String.class);
        assertTrue(result.isPresent());
        assertEquals("test", result.get());

        Optional<Integer> wrongType = MessageUtils.safeCast("test", Integer.class);
        assertTrue(wrongType.isEmpty());

        // 测试UUID验证
        assertTrue(MessageUtils.isValidUUID("550e8400-e29b-41d4-a716-446655440000"));
        assertFalse(MessageUtils.isValidUUID("invalid-uuid"));
        assertFalse(MessageUtils.isValidUUID(""));
        assertFalse(MessageUtils.isValidUUID(null));
    }
}