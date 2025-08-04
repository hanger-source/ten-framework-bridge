import * as React from "react"
import { useMultibandTrackVolume } from "@/common/hooks"
import { MicrophoneAudioTrack, RemoteAudioTrack } from "dingrtc"

export interface AudioVisualizerProps {
  type: "agent" | "user"
  frequencies: Float32Array[]
  gap: number
  barWidth: number
  minBarHeight: number
  maxBarHeight: number
  borderRadius: number
}

export default function AudioVisualizer(props: AudioVisualizerProps) {
  const {
    frequencies,
    gap,
    barWidth,
    minBarHeight,
    maxBarHeight,
    borderRadius,
    type,
  } = props

  const summedFrequencies = frequencies.map((bandFrequencies) => {
    const sum = bandFrequencies.reduce((a, b) => a + b, 0)
    if (sum <= 0) {
      return 0
    }
    return Math.sqrt(sum / bandFrequencies.length)
  })



  return (
    <div
      className={`flex items-center justify-center`}
      style={{ gap: `${gap}px` }}
    >
      {summedFrequencies.map((frequency, index) => {
        const style = {
          height:
            minBarHeight + frequency * (maxBarHeight - minBarHeight) + "px",
          borderRadius: borderRadius + "px",
          width: barWidth + "px",
          transition:
            "background-color 0.35s ease-out, transform 0.25s ease-out",
          // transform: transform,
          backgroundColor: type === "agent" ? "#0888FF" : "#3B82F6",
          boxShadow: type === "agent" ? "0 0 10px #EAECF0" : "0 0 5px #3B82F6",
        }

        return <span key={index} style={style} />
      })}
    </div>
  )
}

// 包装组件，接受 audioTrack 并生成频率数据
export function AudioVisualizerWrapper({
  audioTrack,
  type = "user"
}: {
  audioTrack?: MicrophoneAudioTrack | RemoteAudioTrack
  type?: "agent" | "user"
}) {
  const frequencies = useMultibandTrackVolume(audioTrack, 8, 100, 600)

  // 检查是否有音频轨道
  const hasAudioTrack = audioTrack && audioTrack.getMediaStreamTrack();

  // 如果没有音频轨道，显示友好的空状态
  if (!hasAudioTrack) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="flex flex-col items-center space-y-3 text-gray-400">
          <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center">
            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
            </svg>
          </div>
          <div className="text-sm font-medium">等待音频输入</div>
          <div className="text-xs text-gray-300">连接用户后显示音频可视化</div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center h-full">
      <AudioVisualizer
        type={type}
        frequencies={frequencies}
        gap={2}
        barWidth={4}
        minBarHeight={2}
        maxBarHeight={50}
        borderRadius={2}
      />
    </div>
  )
}
