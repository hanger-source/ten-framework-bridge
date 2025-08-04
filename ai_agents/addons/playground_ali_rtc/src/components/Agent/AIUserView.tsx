"use client"

import React, { useEffect, useState } from 'react';
import { Loader2 } from "lucide-react";
import { RemoteAudioTrack } from "dingrtc";
import Talkinghead from "./TalkingHead";

interface AIUserViewProps {
  audioTrack?: RemoteAudioTrack;
  userName: string;
}

export default function AIUserView({ audioTrack, userName }: AIUserViewProps) {
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 800);

    return () => clearTimeout(timer);
  }, [userName]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center w-full h-full bg-gray-50 rounded-lg">
        <div className="flex flex-col items-center space-y-4 animate-in fade-in duration-300">
          <div className="relative">
            <Loader2 className="h-10 w-10 animate-spin text-blue-500 transition-all duration-300 ease-in-out" />
            <div className="absolute inset-0 rounded-full border-2 border-blue-200 animate-pulse"></div>
          </div>
          <div className="text-sm text-gray-600 animate-in slide-in-from-bottom-2 duration-500 delay-200">
            AI助手正在加载中...
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-in fade-in duration-500 w-full h-full">
      <Talkinghead audioTrack={audioTrack} />
    </div>
  );
}