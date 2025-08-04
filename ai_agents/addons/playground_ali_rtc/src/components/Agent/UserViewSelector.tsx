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
  // 如果没有连接，显示等待连接状态
  if (!isConnected) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="flex flex-col items-center space-y-3 text-gray-400">
          <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center">
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
          </div>
          <div className="text-sm font-medium">等待连接</div>
          <div className="text-xs text-gray-300">请点击"加入"按钮连接到频道</div>
        </div>
      </div>
    );
  }

  // 如果没有当前用户，显示等待其他用户状态
  if (!currentUser) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="flex flex-col items-center space-y-3 text-gray-400">
          <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center">
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </div>
          <div className="text-sm font-medium">等待其他用户</div>
          <div className="text-xs text-gray-300">频道中暂无其他用户</div>
        </div>
      </div>
    );
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