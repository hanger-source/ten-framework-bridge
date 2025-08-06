package com.tenframework.server.message;

import com.tenframework.core.Location;
import com.tenframework.core.message.Command;
import com.tenframework.core.message.Data;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.AudioFrame;
import com.tenframework.core.message.VideoFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                .destinationLocations(java.util.Collections.singletonList(destination))
                .name("testCommand")
                .args(params)
                .build();

        // 编码
        java.util.List<Object> encodedFrames = new java.util.ArrayList<>();
        encoder.encode(null, originalCommand, encodedFrames);

        assertFalse(encodedFrames.isEmpty(), "编码后的帧列表不应为空");
        assertEquals(1, encodedFrames.size(), "编码后应只包含一个帧");
        assertTrue(encodedFrames.get(0) instanceof BinaryWebSocketFrame, "编码后的对象应为BinaryWebSocketFrame类型");

        BinaryWebSocketFrame encodedFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
        ByteBuf encodedByteBuf = encodedFrame.content();

        // 解码
        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, new BinaryWebSocketFrame(encodedByteBuf.retain()), decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof Command, "解码后的消息应为Command类型");

        Command decodedCommand = (Command) decodedMessages.get(0);

        assertEquals(originalCommand.getCommandId(), decodedCommand.getCommandId());
        assertEquals(originalCommand.getParentCommandId(), decodedCommand.getParentCommandId());
        assertEquals(originalCommand.getSourceLocation(), decodedCommand.getSourceLocation());
        assertEquals(originalCommand.getDestinationLocations(), decodedCommand.getDestinationLocations());
        assertEquals(originalCommand.getName(), decodedCommand.getName());
        assertEquals(originalCommand.getArgs(), decodedCommand.getArgs());
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

        // 使用 Data.json 工厂方法创建 Data 消息
        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
        Data originalData = Data.json("testData", jsonPayload);
        originalData.setSourceLocation(source);
        originalData.setDestinationLocations(java.util.Collections.singletonList(destination));

        // 编码
        java.util.List<Object> encodedFrames = new java.util.ArrayList<>();
        encoder.encode(null, originalData, encodedFrames);

        assertFalse(encodedFrames.isEmpty(), "编码后的帧列表不应为空");
        assertEquals(1, encodedFrames.size(), "编码后应只包含一个帧");
        assertTrue(encodedFrames.get(0) instanceof BinaryWebSocketFrame, "编码后的对象应为BinaryWebSocketFrame类型");

        BinaryWebSocketFrame encodedFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
        ByteBuf encodedByteBuf = encodedFrame.content();

        // 解码
        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, new BinaryWebSocketFrame(encodedByteBuf.retain()), decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof Data, "解码后的消息应为Data类型");

        Data decodedData = (Data) decodedMessages.get(0);

        assertEquals(originalData.getSourceLocation(), decodedData.getSourceLocation());
        assertEquals(originalData.getDestinationLocations(), decodedData.getDestinationLocations());
        assertEquals(originalData.getName(), decodedData.getName());

        // 验证 payload 内容，需要从 ByteBuf 中读取并解析
        String decodedJson = decodedData.getData().toString(io.netty.util.CharsetUtil.UTF_8);
        Map<String, Object> decodedPayload = new ObjectMapper().readValue(decodedJson, Map.class);
        assertEquals(payload, decodedPayload);
        assertEquals(MessageType.DATA, decodedData.getType());

        assertTrue(decodedData.checkIntegrity(), "解码后的Data消息完整性检查应通过");
    }

    @Test
    void testAudioFrameEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        byte[] audioBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        ByteBuf originalAudioData = Unpooled.wrappedBuffer(audioBytes);

        // 直接使用构造函数，因为 AudioFrame 没有 @Builder
        AudioFrame originalAudioFrame = new AudioFrame("testAudioFrame", originalAudioData, 44100, 2, 16);
        originalAudioFrame.setSourceLocation(source);
        originalAudioFrame.setDestinationLocations(java.util.Collections.singletonList(destination));

        // 编码
        java.util.List<Object> encodedFrames = new java.util.ArrayList<>();
        encoder.encode(null, originalAudioFrame, encodedFrames);

        assertFalse(encodedFrames.isEmpty(), "编码后的帧列表不应为空");
        assertEquals(1, encodedFrames.size(), "编码后应只包含一个帧");
        assertTrue(encodedFrames.get(0) instanceof BinaryWebSocketFrame, "编码后的对象应为BinaryWebSocketFrame类型");

        BinaryWebSocketFrame encodedFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
        ByteBuf encodedByteBuf = encodedFrame.content();

        // 解码
        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, new BinaryWebSocketFrame(encodedByteBuf.retain()), decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof AudioFrame, "解码后的消息应为AudioFrame类型");

        AudioFrame decodedAudioFrame = (AudioFrame) decodedMessages.get(0);

        assertEquals(originalAudioFrame.getSourceLocation(), decodedAudioFrame.getSourceLocation());
        assertEquals(originalAudioFrame.getDestinationLocations(), decodedAudioFrame.getDestinationLocations());
        assertEquals(originalAudioFrame.getName(), decodedAudioFrame.getName());
        assertEquals(originalAudioFrame.getSampleRate(), decodedAudioFrame.getSampleRate());
        assertEquals(originalAudioFrame.getChannels(), decodedAudioFrame.getChannels());
        assertEquals(originalAudioFrame.getBitsPerSample(), decodedAudioFrame.getBitsPerSample());
        assertEquals(originalAudioFrame.getFormat(), decodedAudioFrame.getFormat());
        assertEquals(MessageType.AUDIO_FRAME, decodedAudioFrame.getType());

        // 验证 ByteBuf 内容
        assertEquals(originalAudioData.readableBytes(), decodedAudioFrame.getData().readableBytes());
        byte[] decodedAudioBytes = new byte[decodedAudioFrame.getData().readableBytes()];
        decodedAudioFrame.getData().readBytes(decodedAudioBytes);
        assertArrayEquals(audioBytes, decodedAudioBytes);

        originalAudioData.release();
        decodedAudioFrame.getData().release();

        assertTrue(decodedAudioFrame.checkIntegrity(), "解码后的AudioFrame消息完整性检查应通过");
    }

    @Test
    void testVideoFrameEncodingAndDecoding() throws Exception {
        Location source = Location.builder().appUri("app1").graphId("graph1").extensionName("ext1").build();
        Location destination = Location.builder().appUri("app2").graphId("graph2").extensionName("ext2").build();

        byte[] videoBytes = new byte[1024];
        for (int i = 0; i < videoBytes.length; i++) {
            videoBytes[i] = (byte) (i % 256);
        }
        ByteBuf originalVideoData = Unpooled.wrappedBuffer(videoBytes);

        // 直接使用构造函数，因为 VideoFrame 没有 @Builder
        VideoFrame originalVideoFrame = new VideoFrame("testVideoFrame", originalVideoData, 1920, 1080, "H264");
        originalVideoFrame.setSourceLocation(source);
        originalVideoFrame.setDestinationLocations(java.util.Collections.singletonList(destination));

        // 编码
        java.util.List<Object> encodedFrames = new java.util.ArrayList<>();
        encoder.encode(null, originalVideoFrame, encodedFrames);

        assertFalse(encodedFrames.isEmpty(), "编码后的帧列表不应为空");
        assertEquals(1, encodedFrames.size(), "编码后应只包含一个帧");
        assertTrue(encodedFrames.get(0) instanceof BinaryWebSocketFrame, "编码后的对象应为BinaryWebSocketFrame类型");

        BinaryWebSocketFrame encodedFrame = (BinaryWebSocketFrame) encodedFrames.get(0);
        ByteBuf encodedByteBuf = encodedFrame.content();

        // 解码
        java.util.List<Object> decodedMessages = new java.util.ArrayList<>();
        decoder.decode(null, new BinaryWebSocketFrame(encodedByteBuf.retain()), decodedMessages);

        assertFalse(decodedMessages.isEmpty(), "解码后的消息列表不应为空");
        assertEquals(1, decodedMessages.size(), "解码后应只包含一条消息");
        assertTrue(decodedMessages.get(0) instanceof VideoFrame, "解码后的消息应为VideoFrame类型");

        VideoFrame decodedVideoFrame = (VideoFrame) decodedMessages.get(0);

        assertEquals(originalVideoFrame.getSourceLocation(), decodedVideoFrame.getSourceLocation());
        assertEquals(originalVideoFrame.getDestinationLocations(), decodedVideoFrame.getDestinationLocations());
        assertEquals(originalVideoFrame.getName(), decodedVideoFrame.getName());
        assertEquals(originalVideoFrame.getWidth(), decodedVideoFrame.getWidth());
        assertEquals(originalVideoFrame.getHeight(), decodedVideoFrame.getHeight());
        assertEquals(originalVideoFrame.getPixelFormat(), decodedVideoFrame.getPixelFormat());
        assertEquals(MessageType.VIDEO_FRAME, decodedVideoFrame.getType());

        // 验证 ByteBuf 内容
        assertEquals(originalVideoData.readableBytes(), decodedVideoFrame.getData().readableBytes());
        byte[] decodedVideoBytes = new byte[decodedVideoFrame.getData().readableBytes()];
        decodedVideoFrame.getData().readBytes(decodedVideoBytes);
        assertArrayEquals(videoBytes, decodedVideoBytes);

        originalVideoData.release();
        decodedVideoFrame.getData().release();

        assertTrue(decodedVideoFrame.checkIntegrity(), "解码后的VideoFrame消息完整性检查应通过");
    }
}