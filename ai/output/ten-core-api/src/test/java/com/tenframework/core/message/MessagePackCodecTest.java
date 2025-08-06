package com.tenframework.core.message;

import com.tenframework.core.Location;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MsgPack编解码器单元测试
 * 验证MessageEncoder和MessageDecoder能够正确地对所有Message子类型进行往返序列化和反序列化
 * 特别是涉及ByteBuf的AudioFrame/VideoFrame
 */
public class MessagePackCodecTest {

    private EmbeddedChannel channel;
    private MessageEncoder encoder;
    private MessageDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new MessageEncoder();
        decoder = new MessageDecoder();
        // EmbeddedChannel用于测试ChannelHandler
        // 编码器在前，解码器在后，模拟真实管道
        channel = new EmbeddedChannel(encoder, decoder);
    }

    @Test
    void testCommandCodec() {
        Command originalCommand = Command.builder().name("test_command")
                .commandId("cmd-123")
                .parentCommandId("parent-456")
                .sourceLocation(new Location("app-1", "graph-1", "ext-src"))
                .destinationLocations(Collections.singletonList(new Location("app-2", "graph-2", "ext-dest")))
                .args(new HashMap<>() {
                    { // 使用args代替params
                        put("param1", "value1");
                        put("param2", 123);
                    }
                })
                .properties(new HashMap<>() {
                    { // 设置properties
                        put("prop1", true);
                    }
                })
                .build();

        // 编码
        channel.writeOutbound(originalCommand);
        BinaryWebSocketFrame encodedFrame = channel.readOutbound(); // 修改为 BinaryWebSocketFrame
        assertNotNull(encodedFrame);
        assertTrue(encodedFrame.content().isReadable()); // 检查内容的可读性

        // 解码
        channel.writeInbound(encodedFrame); // 直接传递 BinaryWebSocketFrame
        Message decodedMessage = channel.readInbound();
        assertNotNull(decodedMessage);
        assertTrue(decodedMessage instanceof Command);

        Command decodedCommand = (Command) decodedMessage;

        assertEquals(originalCommand.getCommandId(), decodedCommand.getCommandId());
        assertEquals(originalCommand.getParentCommandId(), decodedCommand.getParentCommandId());
        assertEquals(originalCommand.getName(), decodedCommand.getName());
        assertEquals(originalCommand.getSourceLocation(), decodedCommand.getSourceLocation());
        assertEquals(originalCommand.getDestinationLocations(), decodedCommand.getDestinationLocations());
        assertEquals(originalCommand.getArgs(), decodedCommand.getArgs()); // 使用getArgs代替getParams
        assertEquals(originalCommand.getProperties(), decodedCommand.getProperties());
        assertEquals(originalCommand.getTimestamp(), decodedCommand.getTimestamp());
        assertTrue(decodedCommand.checkIntegrity());
    }

    @Test
    void testDataCodec() {
        byte[] testDataBytes = "Hello, MsgPack!".getBytes();
        // 使用 Data.binary 工厂方法创建 Data 消息
        Data originalData = Data.binary("test_data", testDataBytes);
        originalData.setEof(false);
        originalData.setContentType("text/plain");
        originalData.setEncoding("UTF-8");
        originalData.setProperty("key", "val");

        channel.writeOutbound(originalData);
        BinaryWebSocketFrame encodedFrame = channel.readOutbound();
        assertNotNull(encodedFrame);
        assertTrue(encodedFrame.content().isReadable());

        channel.writeInbound(encodedFrame);
        Message decodedMessage = channel.readInbound();
        assertNotNull(decodedMessage);
        assertTrue(decodedMessage instanceof Data);

        Data decodedData = (Data) decodedMessage;

        assertEquals(originalData.getName(), decodedData.getName());
        assertEquals(originalData.isEof(), decodedData.isEof());
        assertEquals(originalData.getContentType(), decodedData.getContentType());
        assertEquals(originalData.getEncoding(), decodedData.getEncoding());
        assertEquals(originalData.getProperties(), decodedData.getProperties());
        assertArrayEquals(testDataBytes, decodedData.getDataBytes());
        assertTrue(decodedData.checkIntegrity());

        // 原始ByteBuf在被写入outbound后，其引用计数可能由Netty管理，这里直接释放以确保清理
        originalData.release(); // 显式释放原始数据的ByteBuf

        // 解码后的ByteBuf引用计数为1是正常的，因为它是一个新的副本
        assertEquals(1, decodedData.getData().refCnt());
        decodedData.release(); // 显式释放解码后数据的ByteBuf
    }

    @Test
    void testAudioFrameCodec() {
        byte[] audioBytes = new byte[16000 * 2 * 2]; // 1秒 16kHz 16bit stereo
        new Random().nextBytes(audioBytes);

        AudioFrame originalFrame = new AudioFrame("audio_test", Unpooled.wrappedBuffer(audioBytes), 16000, 2, 16);
        originalFrame.setEof(false);
        originalFrame.setFormat("PCM");
        originalFrame.setSamplesPerChannel(16000);

        channel.writeOutbound(originalFrame);
        BinaryWebSocketFrame encodedFrame = channel.readOutbound();
        assertNotNull(encodedFrame);
        assertTrue(encodedFrame.content().isReadable());

        channel.writeInbound(encodedFrame);
        Message decodedMessage = channel.readInbound();
        assertNotNull(decodedMessage);
        assertTrue(decodedMessage instanceof AudioFrame);

        AudioFrame decodedFrame = (AudioFrame) decodedMessage;

        assertEquals(originalFrame.getName(), decodedFrame.getName());
        assertEquals(originalFrame.isEof(), decodedFrame.isEof());
        assertEquals(originalFrame.getSampleRate(), decodedFrame.getSampleRate());
        assertEquals(originalFrame.getChannels(), decodedFrame.getChannels());
        assertEquals(originalFrame.getBitsPerSample(), decodedFrame.getBitsPerSample());
        assertEquals(originalFrame.getFormat(), decodedFrame.getFormat());
        assertEquals(originalFrame.getSamplesPerChannel(), decodedFrame.getSamplesPerChannel());
        assertArrayEquals(audioBytes, decodedFrame.getDataBytes());
        assertTrue(decodedFrame.checkIntegrity());

        originalFrame.release(); // 显式释放原始音频帧的ByteBuf

        assertEquals(1, decodedFrame.getData().refCnt()); // 验证新创建的ByteBuf引用计数为1
        decodedFrame.release(); // 显式释放解码后音频帧的ByteBuf
    }

    @Test
    void testVideoFrameCodec() {
        byte[] videoBytes = new byte[1920 * 1080 * 3]; // 1080p RGB24
        new Random().nextBytes(videoBytes);

        VideoFrame originalFrame = new VideoFrame("video_test", Unpooled.wrappedBuffer(videoBytes), 1920, 1080,
                "RGB24");
        originalFrame.setEof(false);
        originalFrame.setFps(30.0);
        originalFrame.setFrameType("I");
        originalFrame.setPts(100L);
        originalFrame.setDts(90L);

        channel.writeOutbound(originalFrame);
        BinaryWebSocketFrame encodedFrame = channel.readOutbound();
        assertNotNull(encodedFrame);
        assertTrue(encodedFrame.content().isReadable());

        channel.writeInbound(encodedFrame);
        Message decodedMessage = channel.readInbound();
        assertNotNull(decodedMessage);
        assertTrue(decodedMessage instanceof VideoFrame);

        VideoFrame decodedFrame = (VideoFrame) decodedMessage;

        assertEquals(originalFrame.getName(), decodedFrame.getName());
        assertEquals(originalFrame.isEof(), decodedFrame.isEof());
        assertEquals(originalFrame.getWidth(), decodedFrame.getWidth());
        assertEquals(originalFrame.getHeight(), decodedFrame.getHeight());
        assertEquals(originalFrame.getPixelFormat(), decodedFrame.getPixelFormat());
        assertEquals(originalFrame.getFps(), decodedFrame.getFps());
        assertEquals(originalFrame.getFrameType(), decodedFrame.getFrameType());
        assertEquals(originalFrame.getPts(), decodedFrame.getPts());
        assertEquals(originalFrame.getDts(), decodedFrame.getDts());
        assertArrayEquals(videoBytes, decodedFrame.getDataBytes());
        assertTrue(decodedFrame.checkIntegrity());

        originalFrame.release(); // 显式释放原始视频帧的ByteBuf

        assertEquals(1, decodedFrame.getData().refCnt()); // 验证新创建的ByteBuf引用计数为1
        decodedFrame.release(); // 显式释放解码后视频帧的ByteBuf
    }

    @Test
    void testCommandResultCodec() {
        CommandResult originalResult = CommandResult.success("cmd-result-id", new HashMap<>() {
            {
                put("status", "ok");
                put("value", 100);
            }
        });
        originalResult.setFinal(true);
        originalResult.setError(null);
        originalResult.setErrorCode(null);

        channel.writeOutbound(originalResult);
        BinaryWebSocketFrame encodedFrame = channel.readOutbound();
        assertNotNull(encodedFrame);
        assertTrue(encodedFrame.content().isReadable());

        channel.writeInbound(encodedFrame);
        Message decodedMessage = channel.readInbound();
        assertNotNull(decodedMessage);
        assertTrue(decodedMessage instanceof CommandResult);

        CommandResult decodedResult = (CommandResult) decodedMessage;

        assertEquals(originalResult.getCommandId(), decodedResult.getCommandId());
        assertEquals(originalResult.getResult(), decodedResult.getResult());
        assertEquals(originalResult.isFinal(), decodedResult.isFinal());
        assertEquals(originalResult.getError(), decodedResult.getError());
        assertEquals(originalResult.getErrorCode(), decodedResult.getErrorCode());
        assertTrue(decodedResult.checkIntegrity());
    }

    @Test
    void testCommandCloneIndependence() throws CloneNotSupportedException {
        Command original = Command.builder().name("original_cmd").build();
        original.setCommandId("cmd-original");
        original.setParentCommandId("parent-original");
        original.setSourceLocation(new Location("app-orig", "graph-orig", "ext-orig"));
        original.setDestinationLocations(Collections.singletonList(new Location("app-dest", "graph-dest", "ext-dest")));
        original.setArg("arg1", "value1");
        original.setProperty("prop1", "propVal1");

        Command cloned = (Command) original.clone();

        // 验证克隆后的消息与原始消息在初始内容上相同
        assertEquals(original.getName(), cloned.getName());
        assertEquals(original.getParentCommandId(), cloned.getParentCommandId());
        // 注意：commandId在克隆时会重新生成
        assertNotEquals(original.getCommandId(), cloned.getCommandId());
        assertEquals(original.getSourceLocation(), cloned.getSourceLocation());
        assertEquals(original.getDestinationLocations(), cloned.getDestinationLocations());
        assertEquals(original.getArgs(), cloned.getArgs());
        assertEquals(original.getProperties(), cloned.getProperties());

        // 修改原始消息，验证克隆消息是否不受影响
        original.setName("modified_cmd");
        original.setParentCommandId("modified-parent");
        original.setSourceLocation(new Location("app-mod", "graph-mod", "ext-mod"));
        original.getDestinationLocations().add(new Location("app-new", "graph-new", "ext-new"));
        original.setArg("arg1", "newValue1");
        original.setArg("arg2", 456);
        original.setProperty("prop1", "newPropVal1");
        original.setProperty("prop2", false);

        // 验证克隆消息的字段未改变
        assertEquals("original_cmd", cloned.getName());
        assertEquals("parent-original", cloned.getParentCommandId());
        assertEquals(new Location("app-orig", "graph-orig", "ext-orig"), cloned.getSourceLocation());
        assertEquals(1, cloned.getDestinationLocations().size());
        assertEquals(new Location("app-dest", "graph-dest", "ext-dest"), cloned.getDestinationLocations().get(0));
        assertEquals("value1", cloned.getArg("arg1", String.class).orElse(null));
        assertFalse(cloned.hasArg("arg2"));
        assertEquals("propVal1", cloned.getProperty("prop1", String.class));
        assertFalse(cloned.hasProperty("prop2"));
    }

    @Test
    void testDataCloneIndependence() throws CloneNotSupportedException {
        byte[] originalBytes = { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
        Data original = Data.binary("original_data", originalBytes);
        original.setContentType("original/type");
        original.setProperty("origProp", 123);

        Data cloned = (Data) original.clone();

        // 验证克隆后的消息与原始消息在初始内容上相同
        assertEquals(original.getName(), cloned.getName());
        assertArrayEquals(originalBytes, cloned.getDataBytes());
        assertEquals(original.getContentType(), cloned.getContentType());
        assertEquals(original.getProperties(), cloned.getProperties());
        assertEquals(original.getData().refCnt(), cloned.getData().refCnt()); // 引用计数应该相同 (都为1)

        // 修改原始消息的数据和属性
        byte[] modifiedBytes = { (byte) 5, (byte) 6, (byte) 7, (byte) 8 };
        original.setDataBytes(modifiedBytes);
        original.setContentType("modified/type");
        original.setProperty("origProp", 456);
        original.setProperty("newProp", true);

        // 验证克隆消息的数据和属性未改变，且ByteBuf引用独立
        assertEquals("original_data", cloned.getName());
        assertArrayEquals(originalBytes, cloned.getDataBytes()); // 核心：验证ByteBuf深拷贝
        assertEquals("original/type", cloned.getContentType());
        assertEquals(123, cloned.getProperty("origProp", Integer.class));
        assertFalse(cloned.hasProperty("newProp"));
        assertEquals(1, cloned.getData().refCnt()); // 验证克隆数据的引用计数仍然为1
        assertEquals(1, original.getData().refCnt()); // 验证原始数据的引用计数仍然为1
    }

    @Test
    void testAudioFrameCloneIndependence() throws CloneNotSupportedException {
        byte[] originalAudioBytes = { (byte) 10, (byte) 20, (byte) 30, (byte) 40 };
        AudioFrame original = new AudioFrame("original_audio", Unpooled.wrappedBuffer(originalAudioBytes), 44100, 2,
                16);
        original.setEof(true);
        original.setFormat("WAV");
        original.setProperty("audioProp", "test");

        AudioFrame cloned = (AudioFrame) original.clone();

        assertEquals(original.getName(), cloned.getName());
        assertArrayEquals(originalAudioBytes, cloned.getDataBytes());
        assertEquals(original.getSampleRate(), cloned.getSampleRate());
        assertEquals(original.getChannels(), cloned.getChannels());
        assertEquals(original.getBitsPerSample(), cloned.getBitsPerSample());
        assertEquals(original.isEof(), cloned.isEof());
        assertEquals(original.getFormat(), cloned.getFormat());
        assertEquals(original.getProperties(), cloned.getProperties());

        // 修改原始消息
        byte[] modifiedAudioBytes = { (byte) 50, (byte) 60, (byte) 70, (byte) 80 };
        original.setDataBytes(modifiedAudioBytes);
        original.setEof(false);
        original.setFormat("MP3");
        original.setProperty("audioProp", "modified");
        original.setProperty("newAudioProp", 999);

        // 验证克隆消息未改变
        assertEquals("original_audio", cloned.getName());
        assertArrayEquals(originalAudioBytes, cloned.getDataBytes());
        assertTrue(cloned.isEof());
        assertEquals("WAV", cloned.getFormat());
        assertEquals("test", cloned.getProperty("audioProp", String.class));
        assertFalse(cloned.hasProperty("newAudioProp"));
    }

    @Test
    void testVideoFrameCloneIndependence() throws CloneNotSupportedException {
        byte[] originalVideoBytes = { (byte) 100, (byte) 110, (byte) 120, (byte) 130 };
        VideoFrame original = new VideoFrame("original_video", Unpooled.wrappedBuffer(originalVideoBytes), 640, 480,
                "YUV");
        original.setFps(25.0);
        original.setFrameType("P");
        original.setProperty("videoProp", true);

        VideoFrame cloned = (VideoFrame) original.clone();

        assertEquals(original.getName(), cloned.getName());
        assertArrayEquals(originalVideoBytes, cloned.getDataBytes());
        assertEquals(original.getWidth(), cloned.getWidth());
        assertEquals(original.getHeight(), cloned.getHeight());
        assertEquals(original.getPixelFormat(), cloned.getPixelFormat());
        assertEquals(original.getFps(), cloned.getFps());
        assertEquals(original.getFrameType(), cloned.getFrameType());
        assertEquals(original.getProperties(), cloned.getProperties());

        // 修改原始消息
        byte[] modifiedVideoBytes = { (byte) 140, (byte) 150, (byte) 160, (byte) 170 };
        original.setDataBytes(modifiedVideoBytes);
        original.setFps(60.0);
        original.setFrameType("I");
        original.setProperty("videoProp", false);
        original.setProperty("newVideoProp", "abc");

        // 验证克隆消息未改变
        assertEquals("original_video", cloned.getName());
        assertArrayEquals(originalVideoBytes, cloned.getDataBytes());
        assertEquals(25.0, cloned.getFps());
        assertEquals("P", cloned.getFrameType());
        assertEquals(true, cloned.getProperty("videoProp", Boolean.class));
        assertFalse(cloned.hasProperty("newVideoProp"));
    }

    @Test
    void testCommandResultCloneIndependence() throws CloneNotSupportedException {
        Map<String, Object> originalResultMap = new HashMap<>();
        originalResultMap.put("status", "OK");
        originalResultMap.put("data", new ArrayList<>(Collections.singletonList("item1")));

        CommandResult original = CommandResult.success("original-result-cmd", originalResultMap);
        original.setFinal(false);
        original.setError("original error");
        original.setProperty("resProp", "resVal");

        CommandResult cloned = (CommandResult) original.clone();

        assertEquals(original.getCommandId(), cloned.getCommandId());
        assertEquals(original.isFinal(), cloned.isFinal());
        assertEquals(original.getError(), cloned.getError());
        assertEquals(original.getErrorCode(), cloned.getErrorCode());
        assertEquals(original.getResult(), cloned.getResult()); // Map深拷贝
        assertEquals(original.getProperties(), cloned.getProperties()); // Map深拷贝

        // 修改原始消息
        original.setFinal(true);
        original.setError("modified error");
        original.getResult().put("status", "MODIFIED");
        ((List) original.getResult().get("data")).add("item2"); // 修改List内容
        original.getProperties().put("resProp", "modifiedResVal");
        original.getProperties().put("newResProp", 789);

        // 验证克隆消息未改变
        assertFalse(cloned.isFinal());
        assertEquals("original error", cloned.getError());
        assertEquals("OK", cloned.getResultValue("status", String.class).orElse(null));
        List<String> clonedDataList = (List<String>) cloned.getResultValue("data", List.class).orElse(null);
        assertNotNull(clonedDataList);
        assertEquals(1, clonedDataList.size());
        assertEquals("item1", clonedDataList.get(0));
        assertEquals("resVal", cloned.getProperty("resProp", String.class));
        assertFalse(cloned.hasProperty("newResProp"));
    }

    @Test
    void testMixedMessages() {
        // 测试一次性发送多个消息，验证解码器是否能正确处理
        Command cmd = Command.builder().name("cmd_mix").build();
        Data data = Data.text("data_mix", "mixed data");

        channel.writeOutbound(cmd);
        channel.writeOutbound(data);

        BinaryWebSocketFrame encodedCmd = channel.readOutbound(); // 确保这里是 BinaryWebSocketFrame
        BinaryWebSocketFrame encodedData = channel.readOutbound(); // 确保这里是 BinaryWebSocketFrame

        assertNotNull(encodedCmd);
        assertNotNull(encodedData);

        channel.writeInbound(encodedCmd); // 确保这里是 BinaryWebSocketFrame
        channel.writeInbound(encodedData); // 确保这里是 BinaryWebSocketFrame

        Message decodedCmd = channel.readInbound();
        Message decodedData = channel.readInbound();

        assertNotNull(decodedCmd);
        assertNotNull(decodedData);

        assertTrue(decodedCmd instanceof Command);
        assertTrue(decodedData instanceof Data);

        assertEquals(cmd.getName(), ((Command) decodedCmd).getName());
        assertEquals(data.getName(), ((Data) decodedData).getName());
        assertArrayEquals("mixed data".getBytes(), ((Data) decodedData).getDataBytes());
    }

    @Test
    void testEmptyMessageIntegrityFailure() {
        // 创建一个不完整的消息，验证完整性检查失败后是否不被编码
        Command invalidCommand = Command.builder().name(null).build(); // commandId 和 name 缺失

        assertFalse(invalidCommand.checkIntegrity());

        // 期望编码器因完整性检查失败而抛出EncoderException
        // MessageEncoder.encode在这种情况下不会往out里add任何东西，导致EmbeddedChannel报错
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> {
            channel.writeOutbound(invalidCommand);
        }, "编码器在消息完整性检查失败时应抛出EncoderException");

        // 确保没有输出消息留在通道中
        assertNull(channel.readOutbound());
    }

    @Test
    void testInvalidMsgPackDataDecode() {
        // 发送一些非MsgPack格式的字节，验证解码器是否能处理异常
        ByteBuf invalidDataContent = Unpooled.wrappedBuffer(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        BinaryWebSocketFrame invalidData = new BinaryWebSocketFrame(invalidDataContent);

        channel.writeInbound(invalidData);
        Message decodedMessage = channel.readInbound();
        assertNull(decodedMessage); // 期望解码失败，不输出任何消息
    }

    @Test
    void testIncompleteMsgPackDataDecode() {
        // 发送不完整的MsgPack数据，验证解码器是否等待更多数据
        // 一个EXT类型至少需要1个字节的类型码 + 长度，这里只发送一个字节，不够一个完整的消息
        ByteBuf incompleteDataContent = Unpooled.wrappedBuffer(new byte[] { (byte) 0xC7 }); // EXT 8 type code
        BinaryWebSocketFrame incompleteData = new BinaryWebSocketFrame(incompleteDataContent);

        channel.writeInbound(incompleteData);
        Message decodedMessage = channel.readInbound();
        assertNull(decodedMessage); // 期望等待更多数据，不会立即解码

        // 继续发送更多数据，但仍然不完整
        ByteBuf furtherIncompleteContent = Unpooled
                .wrappedBuffer(new byte[] { MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG, 0x01 }); // type
        BinaryWebSocketFrame furtherIncomplete = new BinaryWebSocketFrame(furtherIncompleteContent);
        // code
        // +
        // length
        // (1
        // byte)
        channel.writeInbound(furtherIncomplete);
        decodedMessage = channel.readInbound();
        assertNull(decodedMessage); // 仍然不足以解码一个Message

        // 发送一个完整的但内部JSON格式错误的EXT消息
        ByteBuf malformedInnerJsonContent = Unpooled
                .wrappedBuffer(new byte[] { MessageUtils.TEN_MSGPACK_EXT_TYPE_MSG, 0x05, '{', 'a', 'b', 'c', '}' });
        BinaryWebSocketFrame malformedInnerJson = new BinaryWebSocketFrame(malformedInnerJsonContent);
        channel.writeInbound(malformedInnerJson);
        decodedMessage = channel.readInbound();
        assertNull(decodedMessage); // 期望内部JSON解码失败，不输出任何消息
    }

    // 辅助方法：确保所有ByteBuf在测试结束时被释放
    // EmbeddedChannel通常会自动管理，但为了健壮性，可以在这里添加检查
    // @AfterEach
    // void tearDown() {
    // assertTrue(channel.finish());
    // assertTrue(channel.inboundMessages().isEmpty());
    // assertTrue(channel.outboundMessages().isEmpty());
    // }

}