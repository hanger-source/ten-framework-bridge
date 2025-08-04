"use client"

import React from 'react';
import { RemoteAudioTrack, RemoteVideoTrack } from "dingrtc";
import { IAliRtcUser } from "@/manager/rtc/ali-types";
import AIUserView from "./AIUserView";
import NonAIUserView from "./NonAIUserView";
import { AudioVisualizerWrapper } from "./AudioVisualizer";

interface UserViewSelectorProps {
  currentUser?: IAliRtcUser;
  isConnected: boolean;
}

export default function UserViewSelector({ currentUser, isConnected }: UserViewSelectorProps) {
  // 如果没有连接，显示空状态
  if (!isConnected) {
    return <AudioVisualizerWrapper audioTrack={undefined} type="user" />;
  }

  // 如果没有当前用户，显示空状态
  if (!currentUser) {
    return <AudioVisualizerWrapper audioTrack={undefined} type="user" />;
  }

  // 检查是否是AI用户
  if (currentUser.userName && currentUser.userName.startsWith('ai_')) {
    return (
      <AIUserView
        audioTrack={currentUser.audioTrack}
        userName={currentUser.userName}
      />
    );
  }

  // 非AI用户
  return (
    <NonAIUserView
      audioTrack={currentUser.audioTrack}
      videoTrack={currentUser.videoTrack}
      userName={currentUser.userName || ''}
    />
  );
}