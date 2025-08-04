"use client"

import React from 'react';
import { RemoteAudioTrack, RemoteVideoTrack } from "dingrtc";
import { RemoteStreamPlayer } from "./StreamPlayer";
import { AudioVisualizerWrapper } from "./AudioVisualizer";

interface NonAIUserViewProps {
  audioTrack?: RemoteAudioTrack;
  videoTrack?: RemoteVideoTrack;
  userName: string;
}

export default function NonAIUserView({ audioTrack, videoTrack, userName }: NonAIUserViewProps) {
  // 检查是否有视频轨道
  if (videoTrack && videoTrack.isPlaying) {
    return (
      <RemoteStreamPlayer
        videoTrack={videoTrack}
        audioTrack={audioTrack}
        fit="cover"
      />
    );
  }

  // 没有视频轨道，显示音频可视化
  return (
    <AudioVisualizerWrapper audioTrack={audioTrack} type="user" />
  );
}