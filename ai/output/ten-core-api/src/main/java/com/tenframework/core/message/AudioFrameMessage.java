package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 音频帧消息，对齐C/Python中的TEN_MSG_TYPE_AUDIO_FRAME。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AudioFrameMessage extends Message {

    @JsonProperty("frame_data")
    private byte[] frameData;

    @JsonProperty("sample_rate")
    private int sampleRate;

    @JsonProperty("channels")
    private int channels;

    @JsonProperty("timestamp")
    private long timestamp;

    // 构造函数可以根据C/Python的常见音频帧属性来设计
    public AudioFrameMessage(String id, Location srcLoc, byte[] frameData, int sampleRate, int channels, long timestamp) {
        super.setType(MessageType.AUDIO_FRAME);
        super.setId(id);
        super.setSrcLoc(srcLoc);
        this.frameData = frameData;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.timestamp = timestamp;
    }

    @Override
    public Object toPayload() {
        return frameData;
    }
}