package com.tenframework.server.message;

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

import com.tenframework.core.message.Command;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageUtils;

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
        assertEquals(originalResult.isFinal(), decodedResult.isFinal());
        assertEquals(originalResult.getError(), decodedResult.getError());
        assertEquals(originalResult.getErrorCode(), decodedResult.getErrorCode());
        assertEquals(originalResult.getResult(), decodedResult.getResult()); // Map深拷贝
        assertEquals(originalResult.getProperties(), decodedResult.getProperties()); // Map深拷贝

        // 修改原始消息
        originalResult.setFinal(true);
        originalResult.setError("modified error");
        originalResult.getResult().put("status", "MODIFIED");
        ((List) originalResult.getResult().get("data")).add("item2"); // 修改List内容
        originalResult.getProperties().put("resProp", "modifiedResVal");
        originalResult.getProperties().put("newResProp", 789);

        // 验证克隆消息未改变
        assertFalse(decodedResult.isFinal());
        assertEquals("original error", decodedResult.getError());
        assertEquals("OK", decodedResult.getResultValue("status", String.class).orElse(null));
        List<String> decodedDataList = (List<String>) decodedResult.getResultValue("data", List.class).orElse(null);
        assertNotNull(decodedDataList);
        assertEquals(1, decodedDataList.size());
        assertEquals("item1", decodedDataList.get(0));
        assertEquals("resVal", decodedResult.getProperty("resProp", String.class));
        assertFalse(decodedResult.hasProperty("newResProp"));
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
}