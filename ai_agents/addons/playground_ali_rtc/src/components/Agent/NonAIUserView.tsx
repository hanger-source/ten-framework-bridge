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
  console.log("[NonAIUserView] Checking video for user:", userName, {
    hasVideoTrack: !!videoTrack,
    videoTrackType: videoTrack?.constructor.name,
    videoTrackPlaying: videoTrack?.isPlaying,
    videoTrackEnabled: videoTrack?.getMediaStreamTrack()?.enabled
  });

  // 检查是否有视频轨道
  if (videoTrack) {
    console.log("[NonAIUserView] Showing video for user:", userName);
    return (
      <RemoteStreamPlayer
        videoTrack={videoTrack}
        audioTrack={audioTrack}
        fit="cover"
      />
    );
  }

  // 检查是否有音频轨道
  if (audioTrack) {
    console.log("[NonAIUserView] Showing audio visualizer for user:", userName);
    return (
      <AudioVisualizerWrapper audioTrack={audioTrack} type="user" />
    );
  }

  // 有用户但无音视频轨道，显示用户在线状态
  console.log("[NonAIUserView] Showing user online status for user:", userName);
  return (
    <div className="flex items-center justify-center h-full">
      <div className="flex flex-col items-center space-y-3 text-gray-400">
        <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center">
          <svg className="w-8 h-8 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </div>
        <div className="text-sm font-medium">用户在线</div>
        <div className="text-xs text-gray-300">{userName} 已连接但未开启音视频</div>
      </div>
    </div>
  );
}