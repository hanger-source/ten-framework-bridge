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
 * 视频帧消息类
 * 代表实时视频数据流
 * 对应C语言中的ten_video_frame_t结构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class VideoFrame extends AbstractMessage {

    @JsonProperty("data")
    private ByteBuf data;

    @JsonProperty("is_eof")
    private boolean isEof = false;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("pixel_format")
    private String pixelFormat;

    @JsonProperty("fps")
    private double fps;

    @JsonProperty("frame_type")
    private String frameType; // I, P, B frame

    @JsonProperty("pts")
    private long pts; // Presentation timestamp

    @JsonProperty("dts")
    private long dts; // Decode timestamp

    /**
     * 默认构造函数
     */
    public VideoFrame() {
        super();
        this.data = Unpooled.EMPTY_BUFFER;
        this.width = 0;
        this.height = 0;
        this.pixelFormat = "YUV420P";
        this.fps = 30.0;
        this.frameType = "I";
        this.pts = -1;
        this.dts = -1;
    }

    /**
     * 创建视频帧的构造函数
     */
    public VideoFrame(String name) {
        this();
        setName(name);
    }

    /**
     * 创建视频帧的构造函数，带视频数据
     */
    public VideoFrame(String name, ByteBuf data, int width, int height, String pixelFormat) {
        this(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
        this.width = width;
        this.height = height;
        this.pixelFormat = pixelFormat;
    }

    /**
     * 创建视频帧的构造函数，带字节数组数据
     */
    public VideoFrame(String name, byte[] data, int width, int height, String pixelFormat) {
        this(name);
        this.data = data != null ? Unpooled.wrappedBuffer(data) : Unpooled.EMPTY_BUFFER;
        this.width = width;
        this.height = height;
        this.pixelFormat = pixelFormat;
    }

    /**
     * JSON反序列化构造函数
     */
    @JsonCreator
    public VideoFrame(
            @JsonProperty("name") String name,
            @JsonProperty("data") ByteBuf data,
            @JsonProperty("is_eof") Boolean isEof,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height,
            @JsonProperty("pixel_format") String pixelFormat,
            @JsonProperty("fps") Double fps,
            @JsonProperty("frame_type") String frameType,
            @JsonProperty("pts") Long pts,
            @JsonProperty("dts") Long dts) {
        super();
        setName(name);
        this.data = data != null ? data : Unpooled.EMPTY_BUFFER;
        this.isEof = isEof != null ? isEof : false;
        this.width = width != null ? width : 0;
        this.height = height != null ? height : 0;
        this.pixelFormat = pixelFormat != null ? pixelFormat : "YUV420P";
        this.fps = fps != null ? fps : 30.0;
        this.frameType = frameType != null ? frameType : "I";
        this.pts = pts != null ? pts : -1;
        this.dts = dts != null ? dts : -1;
    }

    /**
     * 拷贝构造函数
     */
    private VideoFrame(VideoFrame other) {
        super(other);
        // 深拷贝ByteBuf数据
        this.data = other.data != null ? other.data.copy() : Unpooled.EMPTY_BUFFER;
        this.isEof = other.isEof;
        this.width = other.width;
        this.height = other.height;
        this.pixelFormat = other.pixelFormat;
        this.fps = other.fps;
        this.frameType = other.frameType;
        this.pts = other.pts;
        this.dts = other.dts;
    }

    @Override
    public MessageType getType() {
        return MessageType.VIDEO_FRAME;
    }

    /**
     * 获取视频数据大小（字节数）
     */
    public int getDataSize() {
        return data != null ? data.readableBytes() : 0;
    }

    /**
     * 获取视频数据的字节数组拷贝
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
     * 设置视频数据（字节数组）
     */
    public void setDataBytes(byte[] bytes) {
        if (data != null) {
            data.release(); // 释放原有的ByteBuf
        }
        this.data = bytes != null ? Unpooled.wrappedBuffer(bytes) : Unpooled.EMPTY_BUFFER;
    }

    /**
     * 获取视频分辨率字符串
     */
    public String getResolution() {
        return width + "x" + height;
    }

    /**
     * 获取宽高比
     */
    public double getAspectRatio() {
        return height > 0 ? (double) width / height : 0.0;
    }

    /**
     * 计算理论上的未压缩帧大小（字节）
     */
    public int getUncompressedSize() {
        if (width <= 0 || height <= 0) {
            return 0;
        }

        // 根据像素格式计算
        switch (pixelFormat.toUpperCase()) {
            case "YUV420P":
            case "NV12":
                return width * height * 3 / 2; // Y: w*h, U+V: w*h/2
            case "YUV422P":
                return width * height * 2; // Y: w*h, U+V: w*h
            case "YUV444P":
                return width * height * 3; // Y+U+V: w*h*3
            case "RGB24":
            case "BGR24":
                return width * height * 3;
            case "RGBA":
            case "BGRA":
                return width * height * 4;
            case "GRAY8":
                return width * height;
            default:
                return width * height * 3; // 默认RGB24
        }
    }

    /**
     * 获取压缩比
     */
    public double getCompressionRatio() {
        int uncompressedSize = getUncompressedSize();
        int compressedSize = getDataSize();
        return uncompressedSize > 0 && compressedSize > 0 ? (double) uncompressedSize / compressedSize : 1.0;
    }

    /**
     * 检查是否有视频数据
     */
    public boolean hasData() {
        return data != null && data.isReadable();
    }

    /**
     * 检查是否为空视频帧
     */
    public boolean isEmpty() {
        return !hasData();
    }

    /**
     * 检查是否为关键帧
     */
    public boolean isKeyFrame() {
        return "I".equalsIgnoreCase(frameType);
    }

    /**
     * 检查是否为P帧
     */
    public boolean isPFrame() {
        return "P".equalsIgnoreCase(frameType);
    }

    /**
     * 检查是否为B帧
     */
    public boolean isBFrame() {
        return "B".equalsIgnoreCase(frameType);
    }

    /**
     * 创建黑色视频帧
     */
    public static VideoFrame black(String name, int width, int height, String pixelFormat) {
        VideoFrame frame = new VideoFrame(name);
        frame.setWidth(width);
        frame.setHeight(height);
        frame.setPixelFormat(pixelFormat);

        // 创建黑色帧数据（全0）
        int size = frame.getUncompressedSize();
        byte[] blackData = new byte[size];
        frame.setDataBytes(blackData);

        return frame;
    }

    /**
     * 创建EOF标记视频帧
     */
    public static VideoFrame eof(String name) {
        VideoFrame frame = new VideoFrame(name);
        frame.setEof(true);
        return frame;
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(getName(), "视频帧消息名称") &&
                validateVideoParameters();
    }

    /**
     * 验证视频参数
     */
    private boolean validateVideoParameters() {
        return Optional.ofNullable(data).isPresent() &&
                MessageUtils.validateNonNegativeNumber(width, "视频帧宽度") &&
                MessageUtils.validateNonNegativeNumber(height, "视频帧高度") &&
                MessageUtils.validateNonNegativeNumber(fps, "视频帧帧率");
    }

    @Override
    public VideoFrame clone() {
        return new VideoFrame(this);
    }

    @Override
    public String toDebugString() {
        return String.format("VideoFrame[name=%s, size=%d bytes, %s@%.1ffps, %s, pts=%d, eof=%s, src=%s, dest=%s]",
                getName(),
                getDataSize(),
                getResolution(),
                fps,
                frameType,
                pts,
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