package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 视频帧消息，对齐C/Python中的TEN_MSG_TYPE_VIDEO_FRAME。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class VideoFrameMessage extends Message {

    @JsonProperty("frame_data")
    private byte[] frameData;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("timestamp")
    private long timestamp;

    // 构造函数可以根据C/Python的常见视频帧属性来设计
    public VideoFrameMessage(String id, Location srcLoc, byte[] frameData, int width, int height, long timestamp) {
        super.setType(MessageType.VIDEO_FRAME);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.frameData = frameData;
        this.width = width;
        this.height = height;
        this.timestamp = timestamp;
    }

    @Override
    public Object toPayload() {
        return frameData;
    }
}