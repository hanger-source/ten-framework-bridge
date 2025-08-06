package com.tenframework.core.message;

import com.tenframework.core.Location;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MessageCodecTest {

    private MessageEncoder encoder;
    private MessageDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new MessageEncoder();
        decoder = new MessageDecoder();
    }

    @Test
    void testCommandEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", 123);
        params.put("key3", true);

        Command originalCommand = Command.builder()
                .commandId(UUID.randomUUID().toString())
                .parentCommandId(UUID.randomUUID().toString())
                .sourceLocation(source)
                .destinationLocation(destination)
                .commandName("testCommand")
                .params(params)
                .build();

        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalCommand, encoded);

        // 创建一个新的ByteBuf，因为decode会读取并释放in参数
        ByteBuf decodedBuf = Unpooled.wrappedBuffer(encoded.array()); // 使用encoded.array()来复制一份数据

        // 解码器期望List<Object>作为输出
        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, decodedBuf, decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof Command, "解码后的消息应为Command类型");

        Command decodedCommand = (Command) decodedMessages.get(0);

        assertEquals(originalCommand.getCommandId(), decodedCommand.getCommandId());
        assertEquals(originalCommand.getParentCommandId(), decodedCommand.getParentCommandId());
        assertEquals(originalCommand.getSourceLocation(), decodedCommand.getSourceLocation());
        assertEquals(originalCommand.getDestinationLocation(), decodedCommand.getDestinationLocation());
        assertEquals(originalCommand.getCommandName(), decodedCommand.getCommandName());
        assertEquals(originalCommand.getParams(), decodedCommand.getParams());
        assertEquals(MessageType.COMMAND, decodedCommand.getType());

        // 验证integrity
        assertTrue(decodedCommand.checkIntegrity(), "解码后的Command消息完整性检查应通过");
    }

    @Test
    void testDataEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("dataKey1", "dataValue1");
        payload.put("dataKey2", 456);

        Data originalData = Data.builder()
                .commandId(UUID.randomUUID().toString())
                .parentCommandId(UUID.randomUUID().toString())
                .sourceLocation(source)
                .destinationLocation(destination)
                .name("testData")
                .payload(payload)
                .build();

        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalData, encoded);

        ByteBuf decodedBuf = Unpooled.wrappedBuffer(encoded.array());

        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, decodedBuf, decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof Data, "解码后的消息应为Data类型");

        Data decodedData = (Data) decodedMessages.get(0);

        assertEquals(originalData.getCommandId(), decodedData.getCommandId());
        assertEquals(originalData.getParentCommandId(), decodedData.getParentCommandId());
        assertEquals(originalData.getSourceLocation(), decodedData.getSourceLocation());
        assertEquals(originalData.getDestinationLocation(), decodedData.getDestinationLocation());
        assertEquals(originalData.getName(), decodedData.getName());
        assertEquals(originalData.getPayload(), decodedData.getPayload());
        assertEquals(MessageType.DATA, decodedData.getType());

        assertTrue(decodedData.checkIntegrity(), "解码后的Data消息完整性检查应通过");
    }

    @Test
    void testAudioFrameEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        byte[] audioBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        ByteBuf originalAudioData = Unpooled.wrappedBuffer(audioBytes);

        AudioFrame originalAudioFrame = AudioFrame.builder()
                .commandId(UUID.randomUUID().toString())
                .parentCommandId(UUID.randomUUID().toString())
                .sourceLocation(source)
                .destinationLocation(destination)
                .name("testAudioFrame")
                .data(originalAudioData)
                .sampleRate(44100)
                .channels(2)
                .sampleFormat("S16LE")
                .build();

        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalAudioFrame, encoded);

        ByteBuf decodedBuf = Unpooled.wrappedBuffer(encoded.array());

        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, decodedBuf, decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof AudioFrame, "解码后的消息应为AudioFrame类型");

        AudioFrame decodedAudioFrame = (AudioFrame) decodedMessages.get(0);

        assertEquals(originalAudioFrame.getCommandId(), decodedAudioFrame.getCommandId());
        assertEquals(originalAudioFrame.getParentCommandId(), decodedAudioFrame.getParentCommandId());
        assertEquals(originalAudioFrame.getSourceLocation(), decodedAudioFrame.getSourceLocation());
        assertEquals(originalAudioFrame.getDestinationLocation(), decodedAudioFrame.getDestinationLocation());
        assertEquals(originalAudioFrame.getName(), decodedAudioFrame.getName());
        assertEquals(originalAudioFrame.getSampleRate(), decodedAudioFrame.getSampleRate());
        assertEquals(originalAudioFrame.getChannels(), decodedAudioFrame.getChannels());
        assertEquals(originalAudioFrame.getSampleFormat(), decodedAudioFrame.getSampleFormat());
        assertEquals(MessageType.AUDIO_FRAME, decodedAudioFrame.getType());

        // 验证 ByteBuf 内容
        assertEquals(originalAudioData.readableBytes(), decodedAudioFrame.getData().readableBytes());
        byte[] decodedAudioBytes = new byte[decodedAudioFrame.getData().readableBytes()];
        decodedAudioFrame.getData().readBytes(decodedAudioBytes);
        assertArrayEquals(audioBytes, decodedAudioBytes);

        originalAudioData.release(); // 释放原始 ByteBuf
        decodedAudioFrame.getData().release(); // 释放解码后的 ByteBuf

        assertTrue(decodedAudioFrame.checkIntegrity(), "解码后的AudioFrame消息完整性检查应通过");
    }

    @Test
    void testVideoFrameEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        byte[] videoBytes = new byte[1024]; // 模拟1KB视频数据
        for (int i = 0; i < videoBytes.length; i++) {
            videoBytes[i] = (byte) (i % 256);
        }
        ByteBuf originalVideoData = Unpooled.wrappedBuffer(videoBytes);

        VideoFrame originalVideoFrame = VideoFrame.builder()
                .commandId(UUID.randomUUID().toString())
                .parentCommandId(UUID.randomUUID().toString())
                .sourceLocation(source)
                .destinationLocation(destination)
                .name("testVideoFrame")
                .data(originalVideoData)
                .width(1920)
                .height(1080)
                .frameFormat("H264")
                .build();

        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalVideoFrame, encoded);

        ByteBuf decodedBuf = Unpooled.wrappedBuffer(encoded.array());

        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, decodedBuf, decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof VideoFrame, "解码后的消息应为VideoFrame类型");

        VideoFrame decodedVideoFrame = (VideoFrame) decodedMessages.get(0);

        assertEquals(originalVideoFrame.getCommandId(), decodedVideoFrame.getCommandId());
        assertEquals(originalVideoFrame.getParentCommandId(), decodedVideoFrame.getParentCommandId());
        assertEquals(originalVideoFrame.getSourceLocation(), decodedVideoFrame.getSourceLocation());
        assertEquals(originalVideoFrame.getDestinationLocation(), decodedVideoFrame.getDestinationLocation());
        assertEquals(originalVideoFrame.getName(), decodedVideoFrame.getName());
        assertEquals(originalVideoFrame.getWidth(), decodedVideoFrame.getWidth());
        assertEquals(originalVideoFrame.getHeight(), decodedVideoFrame.getHeight());
        assertEquals(originalVideoFrame.getFrameFormat(), decodedVideoFrame.getFrameFormat());
        assertEquals(MessageType.VIDEO_FRAME, decodedVideoFrame.getType());

        // 验证 ByteBuf 内容
        assertEquals(originalVideoData.readableBytes(), decodedVideoFrame.getData().readableBytes());
        byte[] decodedVideoBytes = new byte[decodedVideoFrame.getData().readableBytes()];
        decodedVideoFrame.getData().readBytes(decodedVideoBytes);
        assertArrayEquals(videoBytes, decodedVideoBytes);

        originalVideoData.release(); // 释放原始 ByteBuf
        decodedVideoFrame.getData().release(); // 释放解码后的 ByteBuf

        assertTrue(decodedVideoFrame.checkIntegrity(), "解码后的VideoFrame消息完整性检查应通过");
    }
}