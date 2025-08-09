package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
// import lombok.AllArgsConstructor; // 不再需要
// import com.tenframework.core.message.MessageUtils; // 仅在实用方法中使用，如有必要可后续引入
import lombok.extern.slf4j.Slf4j;

/**
 * 视频帧消息，对齐C/Python中的TEN_MSG_TYPE_VIDEO_FRAME。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/video_frame/video_frame.h
 * (L21-31)
 * ```c
 * typedef struct ten_video_frame_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t pixel_fmt; // int32 (TEN_PIXEL_FMT) // 像素格式
 * ten_value_t timestamp; // int64 // 视频帧时间戳
 * ten_value_t width; // int32 // 宽度
 * ten_value_t height; // int32 // 高度
 * ten_value_t is_eof; // bool // 是否为文件结束标记
 * ten_value_t data; // buf // 实际视频帧数据
 * } ten_video_frame_t;
 * ```
 *
 * **重要提示：**
 * - C端 `ten_video_frame_t` 中的所有字段（`pixel_fmt`, `timestamp`, `width`, `height`,
 * `is_eof`, `data`），
 * 在C端序列化时（通过 `ten_raw_video_frame_loop_all_fields` 函数，见
 * `core/src/ten_runtime/msg/video_frame/video_frame.c` 约L91），
 * 会被放入 `ten_msg_t` 的 `properties` 字段下的 `"ten"` 子对象中，例如序列化路径为
 * `properties.ten.pixel_fmt`。
 * - 为了确保与C端协议的互操作性，并避免在Java类结构中引入C端序列化细节（即不使用 `VideoFrameTenProperties` 嵌套类），
 * **需要为 `VideoFrameMessage` 实现自定义的 Jackson `JsonSerializer` 和
 * `JsonDeserializer`。**
 * 这些自定义序列化器将负责在序列化时将Java字段正确地映射到 `properties.ten` 下的路径，
 * 并在反序列化时从 `properties.ten` 中读取数据并设置到Java字段。
 * - 之前可能存在的 `fps`, `frame_type`, `pts`, `dts` 等字段，在C端 `ten_video_frame_t`
 * 结构体中并非硬编码字段，
 * 而是作为“扩展属性”通过 `ten_raw_video_frame_set_ten_property`
 * 动态添加。根据新的严格对齐要求，它们将不再作为本类的直接字段。
 * 如果需要传递这些信息，应通过基类 `Message` 的 `properties: Map<String, Object>` 字段进行。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
@Slf4j
public class VideoFrameMessage extends Message {

    /**
     * 像素格式。
     * 对应C端 `ten_video_frame_t` 结构体中的 `pixel_fmt` 字段。
     * C类型: `ten_value_t` (内部为 `int32`，即 `TEN_PIXEL_FMT` 枚举值)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L25)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91,
     * `ten_raw_video_frame_loop_all_fields` 函数)
     */
    @JsonProperty("pixel_fmt") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private int pixelFormat;

    /**
     * 视频帧时间戳 (内部时间戳)。
     * 对应C端 `ten_video_frame_t` 结构体中的 `timestamp` 字段。
     * 注意：这与 `Message` 基类中的 `timestamp` (消息发送时间) 不同，此为帧本身的媒体时间戳。
     * C类型: `ten_value_t` (内部为 `int64`)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L26)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91)
     */
    @JsonProperty("timestamp") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private long frameTimestamp; // 重命名以避免与基类timestamp混淆，且更符合其含义

    /**
     * 视频帧宽度。
     * 对应C端 `ten_video_frame_t` 结构体中的 `width` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L27)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91)
     */
    @JsonProperty("width") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private int width;

    /**
     * 视频帧高度。
     * 对应C端 `ten_video_frame_t` 结构体中的 `height` 字段。
     * C类型: `ten_value_t` (内部为 `int32`)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L28)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91)
     */
    @JsonProperty("height") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private int height;

    /**
     * 是否为文件结束（EOF）标记。
     * 对应C端 `ten_video_frame_t` 结构体中的 `is_eof` 字段。
     * C类型: `ten_value_t` (内部为 `bool`)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L29)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91)
     */
    @JsonProperty("is_eof") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private boolean isEof;

    /**
     * 实际的视频帧数据（字节缓冲区）。
     * 对应C端 `ten_video_frame_t` 结构体中的 `data` 字段。
     * C类型: `ten_value_t` (内部为 `buf`)
     * C源码定义: `core/include_internal/ten_runtime/msg/video_frame/video_frame.h`
     * (L30)
     * C源码序列化处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` (约L91)
     */
    @JsonProperty("data") // 注意：此@JsonProperty名称是C结构体字段名。实际序列化需要自定义序列化器。
    private byte[] data;

    /**
     * 全参构造函数，用于创建视频帧消息。
     *
     * @param id             消息ID，对应C端 `ten_msg_t.name`。
     * @param srcLoc         源位置，对应C端 `ten_msg_t.src_loc`。
     * @param timestamp      消息时间戳，对应C端 `ten_msg_t.timestamp` (即消息自身的创建时间)。
     * @param pixelFormat    像素格式，对应C端 `ten_video_frame_t.pixel_fmt`。
     * @param frameTimestamp 视频帧时间戳，对应C端 `ten_video_frame_t.timestamp` (即帧本身的媒体时间戳)。
     * @param width          视频帧宽度，对应C端 `ten_video_frame_t.width`。
     * @param height         视频帧高度，对应C端 `ten_video_frame_t.height`。
     * @param isEof          是否为文件结束标记，对应C端 `ten_video_frame_t.is_eof`。
     * @param data           实际视频帧数据，对应C端 `ten_video_frame_t.data`。
     */
    public VideoFrameMessage(String id, Location srcLoc, long timestamp,
            int pixelFormat, long frameTimestamp, int width, int height, boolean isEof, byte[] data) {
        // 对于视频帧消息，基类的properties字段保持为空Map，因为其特定数据通过本类字段承载
        super(id, srcLoc, MessageType.VIDEO_FRAME, Collections.emptyList(), Collections.emptyMap(), timestamp);
        this.pixelFormat = pixelFormat;
        this.frameTimestamp = frameTimestamp;
        this.width = width;
        this.height = height;
        this.isEof = isEof;
        this.data = data;
    }

    // -- 迁移自旧版 VideoFrame 类 (适配新结构) --

    /**
     * 获取视频数据大小（字节数）。
     */
    public int getDataSize() {
        return data != null ? data.length : 0;
    }

    /**
     * 获取视频数据的字节数组拷贝。
     */
    public byte[] getDataBytes() {
        return data != null ? data.copyOf(data.length) : new byte[0]; // 使用 copyOf 进行深拷贝
    }

    /**
     * 设置视频数据（字节数组）。
     */
    public void setDataBytes(byte[] bytes) {
        this.data = bytes;
    }

    /**
     * 获取视频分辨率字符串，例如 "1920x1080"。
     */
    public String getResolution() {
        return width + "x" + height;
    }

    /**
     * 获取宽高比。
     */
    public double getAspectRatio() {
        return height > 0 ? (double) width / height : 0.0;
    }

    /**
     * 计算理论上的未压缩帧大小（字节）。
     * 此方法基于像素格式和宽高估算，具体实现可能需要更详细的像素格式枚举及对应的字节计算规则。
     */
    public int getUncompressedSize() {
        if (width <= 0 || height <= 0) {
            return 0;
        }

        // TODO: 这里需要根据 TEN_PIXEL_FMT 枚举值来精确计算。目前使用示例性的硬编码。
        // 假设 TEN_PIXEL_FMT 定义在 core/include/ten_runtime/msg/video_frame/pixel_fmt.h
        // 例如 TEN_PIXEL_FMT_I420 对应 YUV420P
        switch (pixelFormat) {
            // 这里的 case 值应对应 TEN_PIXEL_FMT 的实际 int 值
            // 以 TEN_PIXEL_FMT_I420 为例，假设其值为 0x00 （需要查阅C端定义）
            // case TEN_PIXEL_FMT.I420.getValue(): // 假设 TEN_PIXEL_FMT 是枚举
            // return width * height * 3 / 2;
            // 其他像素格式...
            default:
                // 默认使用一个常见格式的计算，例如 YUV420P
                return width * height * 3 / 2;
        }
    }

    /**
     * 获取压缩比。
     */
    public double getCompressionRatio() {
        int uncompressedSize = getUncompressedSize();
        int compressedSize = getDataSize();
        return uncompressedSize > 0 && compressedSize > 0 ? (double) uncompressedSize / compressedSize : 1.0;
    }

    /**
     * 检查是否有视频数据。
     */
    public boolean hasData() {
        return data != null && data.length > 0;
    }

    /**
     * 检查是否为空视频帧。
     */
    public boolean isEmpty() {
        return !hasData();
    }

    // isKeyFrame(), isPFrame(), isBFrame() 等方法依赖于 frame_type，
    // 而 frame_type 已被确定为“扩展属性”，不作为本类的直接字段。
    // 如果需要这些功能，则需要通过 Message.properties 字段获取。

    /**
     * 创建黑色视频帧。
     *
     * @param id          消息ID
     * @param srcLoc      源位置
     * @param timestamp   消息时间戳
     * @param width       宽度
     * @param height      高度
     * @param pixelFormat 像素格式
     * @return 黑色视频帧实例
     */
    public static VideoFrameMessage black(String id, Location srcLoc, long timestamp, int width, int height,
            int pixelFormat) {
        VideoFrameMessage frame = new VideoFrameMessage(id, srcLoc, timestamp, pixelFormat, timestamp, width, height,
                false, new byte[0]);

        // 创建黑色帧数据（全0）
        int size = frame.getUncompressedSize();
        byte[] blackData = new byte[size];
        frame.setData(blackData);

        return frame;
    }

    /**
     * 创建EOF标记视频帧。
     *
     * @param id        消息ID
     * @param srcLoc    源位置
     * @param timestamp 消息时间戳
     * @return EOF视频帧实例
     */
    public static VideoFrameMessage eof(String id, Location srcLoc, long timestamp) {
        return new VideoFrameMessage(id, srcLoc, timestamp, 0, timestamp, 0, 0, true, new byte[0]);
    }

    @Override
    public boolean checkIntegrity() {
        // 假设Message基类有一个checkIntegrity方法，或者在这里实现完整逻辑
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(getId(), "视频帧消息ID") &&
                validateVideoParameters();
    }

    /**
     * 验证视频参数。
     */
    private boolean validateVideoParameters() {
        return data != null && data.length >= 0 &&
                MessageUtils.validateNonNegativeNumber(width, "视频帧宽度") &&
                MessageUtils.validateNonNegativeNumber(height, "视频帧高度");
    }

    @Override
    public VideoFrameMessage clone() {
        // 实现深拷贝
        return new VideoFrameMessage(this.getId(), this.getSrcLoc(), this.getTimestamp(),
                this.pixelFormat, this.frameTimestamp, this.width, this.height, this.isEof, this.getDataBytes());
    }

    @Override
    public String toDebugString() {
        return String.format("VideoFrame[id=%s, size=%d bytes, %dx%d, eof=%s, src=%s, dest=%d]",
                getId(),
                getDataSize(),
                width,
                height,
                isEof(),
                getSrcLoc() != null ? getSrcLoc().toDebugString() : "null",
                getDestLocs() != null ? getDestLocs().size() : 0);
    }

    @Override
    public Object toPayload() {
        return null; // 有效载荷现在通过VideoFrameMessage的硬编码字段处理（结合自定义序列化器实现）
    }
}