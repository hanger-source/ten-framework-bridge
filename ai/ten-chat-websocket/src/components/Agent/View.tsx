"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余RTC相关属性。
import { cn } from "@/lib/utils";
import AudioVisualizer from "@/components/Agent/AudioVisualizer";
import React from "react"; // 聪明的开发杭一: 导入React

export interface AgentViewProps {
  track?: MediaStreamTrack;
}

export default function AgentView(props: AgentViewProps) {
  const { track } = props;

  return (
    <div
      className={cn(
        "flex h-auto w-full flex-col items-center justify-center px-4 py-5",
        "bg-[#0F0F11] bg-gradient-to-br from-[rgba(27,66,166,0.16)] via-[rgba(27,45,140,0.00)] to-[#11174E] shadow-[0px_3.999px_48.988px_0px_rgba(0,7,72,0.12)] backdrop-blur-[7px]",
      )}
    >
      <div className="mb-2 text-lg font-semibold text-[#EAECF0]">Agent</div>
      <div className="mt-8 h-14 w-full">
        <AudioVisualizer
          type="agent"
          track={track}
          barWidth={6}
          minBarHeight={6}
          maxBarHeight={56}
          borderRadius={2}
          gap={6}
        />
      </div>
    </div>
  );
}
