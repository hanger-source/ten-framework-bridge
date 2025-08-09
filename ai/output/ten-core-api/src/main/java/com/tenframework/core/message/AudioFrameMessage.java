package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tenframework.core.message.serializer.AudioFrameMessageDeserializer;
import com.tenframework.core.message.serializer.AudioFrameMessageSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
// import lombok.AllArgsConstructor; // 不再需要

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
// import com.tenframework.core.message.MessageUtils; // 仅在实用方法中使用，如有必要可后续引入
import lombok.extern.slf4j.Slf4j;

/**
 * 音频帧消息，对齐C/Python中的TEN_MSG_TYPE_AUDIO_FRAME。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h
 * (L19-58)
 * ```c
 * typedef struct ten_audio_frame_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t timestamp; // int64. 音频帧时间戳
 * ten_value_t sample_rate; // int32. 采样率
 * ten_value_t bytes_per_sample; // int32. 每采样字节数
 * ten_value_t samples_per_channel; // int32. 每声道采样数
 * ten_value_t number_of_channel; // int32. 声道数
 * ten_value_t channel_layout; // uint64. 声道布局ID
 * ten_value_t data_fmt; // int32 (TEN_AUDIO_FRAME_DATA_FMT). 数据格式
 * ten_value_t buf; // buf. 实际音频帧数据
 * ten_value_t line_size; // int32. 行大小
 * ten_value_t is_eof; // bool. 是否为文件结束标记
 * } ten_audio_frame_t;
 * ```
 *
 * **重要提示：**
 * - C端 `ten_audio_frame_t` 中的所有字段（`timestamp`, `sample_rate`,
 * `bytes_per_sample`, `samples_per_channel`,
 * `number_of_channel`, `channel_layout`, `data_fmt`, `buf`, `line_size`,
 * `is_eof`），
 * 在C端序列化时（通过 `ten_raw_audio_frame_loop_all_fields` 函数，见
 * `core/src/ten_runtime/msg/audio_frame/audio_frame.c` 约L131），
 * 会被放入 `ten_msg_t` 的 `properties` 字段下的 `"ten"` 子对象中，例如序列化路径为
 * `properties.ten.timestamp`。
 * - 为了确保与C端协议的互操作性，并避免在Java类结构中引入C端序列化细节（即不使用 `AudioFrameTenProperties` 嵌套类），
 * **需要为 `AudioFrameMessage` 实现自定义的 Jackson `JsonSerializer` 和
 * `JsonDeserializer`。**
 * 这些自定义序列化器将负责在序列化时将Java字段正确地映射到 `properties.ten` 下的路径，
 * 并在反序列化时从 `properties.ten` 中读取数据并设置到Java字段。
 * - 之前可能存在的 `bits_per_sample`, `format` 等字段，在C端 `ten_audio_frame_t`
 * 结构体中并非硬编码字段，
 * 而是作为“扩展属性”通过 `ten_raw_audio_frame_set_ten_property`
 * 动态添加。根据新的严格对齐要求，它们将不再作为本类的直接字段。
 * 如果需要传递这些信息，应通过基类 `Message` 的 `properties: Map<String, Object>` 字段进行。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
@Slf4j
@JsonSerialize(using = AudioFrameMessageSerializer.class)
@JsonDeserialize(using = AudioFrameMessageDeserializer.class)
public class AudioFrameMessage extends Message {

    /**
     * 音频帧时间戳 (内部时间戳)。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `timestamp` 字段。
     * 注意：这与 `Message` 基类中的 `timestamp` (消息发送时间) 不同，此为帧本身的媒体时间戳。
     * C类型: `ten_value_t` (内部为 `int64`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L23)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131,
     * `ten_raw_audio_frame_loop_all_fields` 函数)
     */
    @JsonProperty("timestamp") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private long frameTimestamp; // 重命名以避免与基类timestamp混淆，且更符合其含义

    /**
     * 音频采样率（Hz）。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `sample_rate` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L24)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("sample_rate")
    private int sampleRate;

    /**
     * 每采样字节数。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `bytes_per_sample` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L25)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("bytes_per_sample")
    private int bytesPerSample;

    /**
     * 每声道采样数。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `samples_per_channel` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L26)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("samples_per_channel")
    private int samplesPerChannel;

    /**
     * 声道数。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `number_of_channel` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L27)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("number_of_channel")
    private int numberOfChannel;

    /**
     * 声道布局ID (FFmpeg)。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `channel_layout` 字段。
     * C类型: `ten_value_t` (内部为 `uint64`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L37)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("channel_layout")
    private long channelLayout;

    /**
     * 音频数据格式。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `data_fmt` 字段。
     * C类型: `ten_value_t` (内部为 `int32`，即 `TEN_AUDIO_FRAME_DATA_FMT` 枚举值)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L39)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("data_fmt")
    private int dataFormat;

    /**
     * 实际的音频帧数据（字节缓冲区）。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `buf` 字段。
     * C类型: `ten_value_t` (内部为 `buf`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L41)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("buf")
    private byte[] buf;

    /**
     * 音频数据行大小。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `line_size` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L55)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("line_size")
    private int lineSize;

    /**
     * 是否为文件结束（EOF）标记。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `is_eof` 字段。
     * C类型: `ten_value_t` (内部为 `bool`)
     * C源码定义: `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h`
     * (L57)
     * C源码序列化处理: `core/src/ten_runtime/msg/audio_frame/audio_frame.c` (约L131)
     */
    @JsonProperty("is_eof")
    private boolean isEof;

    /**
     * 全参构造函数，用于创建音频帧消息。
     *
     * @param id                消息ID，对应C端 `ten_msg_t.name`。
     * @param srcLoc            源位置，对应C端 `ten_msg_t.src_loc`。
     * @param timestamp         消息时间戳，对应C端 `ten_msg_t.timestamp` (即消息自身的创建时间)。
     * @param frameTimestamp    音频帧时间戳，对应C端 `ten_audio_frame_t.timestamp`
     *                          (即帧本身的媒体时间戳)。
     * @param sampleRate        采样率，对应C端 `ten_audio_frame_t.sample_rate`。
     * @param bytesPerSample    每采样字节数，对应C端 `ten_audio_frame_t.bytes_per_sample`。
     * @param samplesPerChannel 每声道采样数，对应C端 `ten_audio_frame_t.samples_per_channel`。
     * @param numberOfChannel   声道数，对应C端 `ten_audio_frame_t.number_of_channel`。
     * @param channelLayout     声道布局ID，对应C端 `ten_audio_frame_t.channel_layout`。
     * @param dataFormat        数据格式，对应C端 `ten_audio_frame_t.data_fmt`。
     * @param lineSize          行大小，对应C端 `ten_audio_frame_t.line_size`。
     * @param isEof             是否为文件结束标记，对应C端 `ten_audio_frame_t.is_eof`。
     * @param buf               实际音频帧数据，对应C端 `ten_audio_frame_t.buf`。
     */
    public AudioFrameMessage(String id, Location srcLoc, long timestamp,
            long frameTimestamp, int sampleRate, int bytesPerSample, int samplesPerChannel, int numberOfChannel,
            long channelLayout, int dataFormat, byte[] buf, int lineSize, boolean isEof) {
        // 对于音频帧消息，基类的properties字段保持为空Map，因为其特定数据通过本类字段承载
        super(id, srcLoc, MessageType.AUDIO_FRAME, Collections.emptyList(), Collections.emptyMap(), timestamp);
        this.frameTimestamp = frameTimestamp;
        this.sampleRate = sampleRate;
        this.bytesPerSample = bytesPerSample;
        this.samplesPerChannel = samplesPerChannel;
        this.numberOfChannel = numberOfChannel;
        this.channelLayout = channelLayout;
        this.dataFormat = dataFormat;
        this.buf = buf;
        this.lineSize = lineSize;
        this.isEof = isEof;
    }

    // -- 迁移自旧版 AudioFrame 类 (适配新结构) --

    /**
     * 获取音频数据大小（字节数）。
     */
    public int getDataSize() {
        return buf != null ? buf.length : 0;
    }

    /**
     * 获取音频数据的字节数组拷贝。
     */
    public byte[] getDataBytes() {
        return buf != null ? buf.copyOf(buf.length) : new byte[0]; // 使用 copyOf 进行深拷贝
    }

    /**
     * 设置音频数据（字节数组）。
     */
    public void setDataBytes(byte[] bytes) {
        this.buf = bytes;
        // 更新 samplesPerChannel，因为它可能依赖于数据大小和 bytesPerSample
        // TODO: 考虑是否需要更复杂的逻辑来动态计算 samplesPerChannel
        // this.samplesPerChannel = calculateSamplesPerChannel();
    }

    /**
     * 计算每声道采样数。
     * 注意：此方法依赖于 `bytesPerSample` 和 `numberOfChannel`，如果这两个字段未正确设置，结果可能不准确。
     */
    public int calculateSamplesPerChannel() {
        if (buf == null || buf.length == 0 || numberOfChannel <= 0 || bytesPerSample <= 0) {
            return 0;
        }
        int totalBytes = buf.length;
        int totalSamples = totalBytes / bytesPerSample; // 总采样点数
        return totalSamples / numberOfChannel; // 每声道采样数
    }

    /**
     * 获取音频时长（毫秒）。
     */
    public double getDurationMs() {
        int calculatedSamplesPerChannel = calculateSamplesPerChannel(); // 使用计算的每声道采样数
        if (calculatedSamplesPerChannel <= 0 || sampleRate <= 0) {
            return 0.0;
        }
        return (double) calculatedSamplesPerChannel * 1000.0 / sampleRate;
    }

    /**
     * 获取每秒字节数（比特率）。
     */
    public int getBytesPerSecond() {
        if (sampleRate <= 0 || numberOfChannel <= 0 || bytesPerSample <= 0) {
            return 0;
        }
        return sampleRate * numberOfChannel * bytesPerSample;
    }

    /**
     * 检查是否有音频数据。
     */
    public boolean hasData() {
        return buf != null && buf.length > 0;
    }

    /**
     * 检查是否为空音频帧。
     */
    public boolean isEmpty() {
        return !hasData();
    }

    /**
     * 创建静音音频帧。
     *
     * @param id             消息ID
     * @param srcLoc         源位置
     * @param timestamp      消息时间戳
     * @param durationMs     持续时长（毫秒）
     * @param sampleRate     采样率
     * @param channels       声道数
     * @param bytesPerSample 每采样字节数
     * @return 静音音频帧实例
     */
    public static AudioFrameMessage silence(String id, Location srcLoc, long timestamp, int durationMs, int sampleRate,
            int channels, int bytesPerSample) {
        int samplesPerChannel = (durationMs * sampleRate) / 1000;
        int totalSamples = samplesPerChannel * channels;
        // int bytesPerSample = bitsPerSample / 8; // bitsPerSample 已移除，直接使用
        // bytesPerSample
        byte[] silenceData = new byte[totalSamples * bytesPerSample];
        // 默认为0，表示静音

        return new AudioFrameMessage(id, srcLoc, timestamp,
                timestamp, sampleRate, bytesPerSample, samplesPerChannel, channels,
                0L, 0, silenceData, 0, false); // channelLayout, dataFormat, lineSize 默认值
    }

    /**
     * 创建EOF标记音频帧。
     *
     * @param id        消息ID
     * @param srcLoc    源位置
     * @param timestamp 消息时间戳
     * @return EOF音频帧实例
     */
    public static AudioFrameMessage eof(String id, Location srcLoc, long timestamp) {
        return new AudioFrameMessage(id, srcLoc, timestamp,
                timestamp, 0, 0, 0, 0,
                0L, 0, new byte[0], 0, true);
    }

    @Override
    public boolean checkIntegrity() {
        // 假设Message基类有一个checkIntegrity方法，或者在这里实现完整逻辑
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(getId(), "音频帧消息ID") &&
                validateAudioParameters();
    }

    /**
     * 验证音频参数。
     */
    private boolean validateAudioParameters() {
        return buf != null && buf.length >= 0 &&
                MessageUtils.validatePositiveNumber(sampleRate, "音频帧采样率") &&
                MessageUtils.validatePositiveNumber(numberOfChannel, "音频帧声道数") &&
                MessageUtils.validatePositiveNumber(bytesPerSample, "音频帧每采样字节数");
    }

    @Override
    public AudioFrameMessage clone() {
        // 实现深拷贝
        return new AudioFrameMessage(this.getId(), this.getSrcLoc(), this.getTimestamp(),
                this.frameTimestamp, this.sampleRate, this.bytesPerSample,
                this.samplesPerChannel, this.numberOfChannel, this.channelLayout,
                this.dataFormat, this.buf.copyOf(this.buf.length), this.lineSize, this.isEof);
    }

    @Override
    public String toDebugString() {
        return String.format(
                "AudioFrame[id=%s, size=%d bytes, %dHz/%dch/%dbytes-per-sample, duration=%.1fms, eof=%s, src=%s, dest=%d]",
                getId(),
                getDataSize(),
                sampleRate,
                numberOfChannel,
                bytesPerSample,
                getDurationMs(),
                isEof(),
                getSrcLoc() != null ? getSrcLoc().toDebugString() : "null",
                getDestLocs() != null ? getDestLocs().size() : 0);
    }

    @Override
    public Object toPayload() {
        return null; // 有效载荷现在通过AudioFrameMessage的硬编码字段处理（结合自定义序列化器实现）
    }
}