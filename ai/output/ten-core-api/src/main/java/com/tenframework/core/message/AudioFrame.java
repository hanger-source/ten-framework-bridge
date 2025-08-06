package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 音频帧消息类
 * 代表实时音频数据流
 * 对应C语言中的ten_audio_frame_t结构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class AudioFrame extends AbstractMessage {

    @JsonProperty("data")
    private ByteBuf data;

    @JsonProperty("is_eof")
    private boolean isEof = false;

    @JsonProperty("sample_rate")
    private int sampleRate;

    @JsonProperty("channels")
    private int channels;

    @JsonProperty("bits_per_sample")
    private int bitsPerSample;

    @JsonProperty("format")
    private String format;

    @JsonProperty("samples_per_channel")
    private int samplesPerChannel;

    /**
     * 默认构造函数
     */
    public AudioFrame() {
        super();
        this.data = Unpooled.EMPTY_BUFFER;
        this.sampleRate = 16000; // 默认16kHz
        this.channels = 1; // 默认单声道
        this.bitsPerSample = 16; // 默认16位
        this.format = "PCM";
    }

    /**
     * 创建音频帧的构造函数
     */
    public AudioFrame(String name) {
        this();
        setName(name);
    }

    /**
     * 创建音频帧的构造函数，带音频数据
     */
    public AudioFrame(String name, ByteBuf data, int sampleRate, int channels, int bitsPerSample) {
        this(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.samplesPerChannel = calculateSamplesPerChannel();
    }

    /**
     * 创建音频帧的构造函数，带字节数组数据
     */
    public AudioFrame(String name, byte[] data, int sampleRate, int channels, int bitsPerSample) {
        this(name);
        this.data = data != null ? Unpooled.wrappedBuffer(data) : Unpooled.EMPTY_BUFFER;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.samplesPerChannel = calculateSamplesPerChannel();
    }

    /**
     * JSON反序列化构造函数
     */
    @JsonCreator
    public AudioFrame(
            @JsonProperty("name") String name,
            @JsonProperty("data") ByteBuf data,
            @JsonProperty("is_eof") Boolean isEof,
            @JsonProperty("sample_rate") Integer sampleRate,
            @JsonProperty("channels") Integer channels,
            @JsonProperty("bits_per_sample") Integer bitsPerSample,
            @JsonProperty("format") String format,
            @JsonProperty("samples_per_channel") Integer samplesPerChannel) {
        super();
        setName(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
        this.isEof = isEof != null ? isEof : false;
        this.sampleRate = sampleRate != null ? sampleRate : 16000;
        this.channels = channels != null ? channels : 1;
        this.bitsPerSample = bitsPerSample != null ? bitsPerSample : 16;
        this.format = format != null ? format : "PCM";
        this.samplesPerChannel = samplesPerChannel != null ? samplesPerChannel : calculateSamplesPerChannel();
    }

    /**
     * 拷贝构造函数
     */
    private AudioFrame(AudioFrame other) {
        super(other);
        // 深拷贝ByteBuf数据
        this.data = other.data != null ? other.data.copy() : Unpooled.EMPTY_BUFFER;
        this.isEof = other.isEof;
        this.sampleRate = other.sampleRate;
        this.channels = other.channels;
        this.bitsPerSample = other.bitsPerSample;
        this.format = other.format;
        this.samplesPerChannel = other.samplesPerChannel;
    }

    @Override
    public MessageType getType() {
        return MessageType.AUDIO_FRAME;
    }

    /**
     * 获取音频数据大小（字节数）
     */
    public int getDataSize() {
        return data != null ? data.readableBytes() : 0;
    }

    /**
     * 获取音频数据的字节数组拷贝
     */
    public byte[] getDataBytes() {
        if (data == null || !data.isReadable()) {
            return new byte[0];
        }

        byte[] bytes = new byte[data.readableBytes()];
        data.getBytes(data.readerIndex(), bytes);
        return bytes;
    }

    /**
     * 设置音频数据（字节数组）
     */
    public void setDataBytes(byte[] bytes) {
        if (data != null) {
            data.release(); // 释放原有的ByteBuf
        }
        this.data = bytes != null ? Unpooled.wrappedBuffer(bytes) : Unpooled.EMPTY_BUFFER;
        this.samplesPerChannel = calculateSamplesPerChannel();
    }

    /**
     * 计算每声道采样数
     */
    private int calculateSamplesPerChannel() {
        if (data == null || !data.isReadable() || channels <= 0 || bitsPerSample <= 0) {
            return 0;
        }

        int bytesPerSample = bitsPerSample / 8;
        int totalSamples = data.readableBytes() / bytesPerSample;
        return totalSamples / channels;
    }

    /**
     * 获取音频时长（毫秒）
     */
    public double getDurationMs() {
        if (samplesPerChannel <= 0 || sampleRate <= 0) {
            return 0.0;
        }
        return (double) samplesPerChannel * 1000.0 / sampleRate;
    }

    /**
     * 获取每秒字节数（比特率）
     */
    public int getBytesPerSecond() {
        return sampleRate * channels * (bitsPerSample / 8);
    }

    /**
     * 检查是否有音频数据
     */
    public boolean hasData() {
        return data != null && data.isReadable();
    }

    /**
     * 检查是否为空音频帧
     */
    public boolean isEmpty() {
        return !hasData();
    }

    /**
     * 创建静音音频帧
     */
    public static AudioFrame silence(String name, int durationMs, int sampleRate, int channels, int bitsPerSample) {
        int samplesPerChannel = (durationMs * sampleRate) / 1000;
        int totalSamples = samplesPerChannel * channels;
        int bytesPerSample = bitsPerSample / 8;
        byte[] silenceData = new byte[totalSamples * bytesPerSample];
        // 默认为0，表示静音

        return new AudioFrame(name, silenceData, sampleRate, channels, bitsPerSample);
    }

    /**
     * 创建EOF标记音频帧
     */
    public static AudioFrame eof(String name) {
        AudioFrame frame = new AudioFrame(name);
        frame.setEof(true);
        return frame;
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(getName(), "音频帧消息名称") &&
                validateAudioParameters();
    }

    /**
     * 验证音频参数
     */
    private boolean validateAudioParameters() {
        return Optional.ofNullable(data).isPresent() &&
                MessageUtils.validatePositiveNumber(sampleRate, "音频帧采样率") &&
                MessageUtils.validatePositiveNumber(channels, "音频帧声道数") &&
                validateBitsPerSample();
    }

    /**
     * 验证位深度（必须大于0且为8的倍数）
     */
    private boolean validateBitsPerSample() {
        return MessageUtils.validatePositiveNumber(bitsPerSample, "音频帧位深度") &&
                (bitsPerSample % 8 == 0 || logInvalidBitsPerSample());
    }

    private boolean logInvalidBitsPerSample() {
        log.warn("音频帧位深度必须是8的倍数: {}", bitsPerSample);
        return false;
    }

    @Override
    public AudioFrame clone() {
        return new AudioFrame(this);
    }

    @Override
    public String toDebugString() {
        return String.format(
                "AudioFrame[name=%s, size=%d bytes, %dHz/%dch/%dbit, duration=%.1fms, eof=%s, src=%s, dest=%s]",
                getName(),
                getDataSize(),
                sampleRate,
                channels,
                bitsPerSample,
                getDurationMs(),
                isEof,
                getSourceLocation(),
                getDestinationLocations().size());
    }

    /**
     * 资源清理 - 释放ByteBuf
     */
    public void release() {
        if (data != null && data.refCnt() > 0) {
            data.release();
        }
    }

    /**
     * 实现AutoCloseable接口，支持try-with-resources
     */
    public void close() {
        release();
    }
}