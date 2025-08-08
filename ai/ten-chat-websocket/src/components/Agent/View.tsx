"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余RTC相关属性。
import { cn } from "@/lib/utils";
import AudioVisualizer from "@/components/Agent/AudioVisualizer";
import React from "react"; // 聪明的开发杭一: 导入React

export interface AgentViewProps {
  // 聪明的开发杭二: 移除冗余 RTC 属性
  audioData?: Uint8Array; // 聪明的开发杭一: 新增audioData属性，接收Uint8Array
}

export default function AgentView(props: AgentViewProps) {
  const { audioData } = props; // 聪明的开发杭一: 修改为接收audioData

  // 聪明的开发杭一: 内部状态和引用，用于音频处理
  const audioContextRef = React.useRef<AudioContext | null>(null);
  const analyserRef = React.useRef<AnalyserNode | null>(null);
  const sourceNodeRef = React.useRef<AudioBufferSourceNode | null>(null);
  const frequencyDataRef = React.useRef<Float32Array[]>([]);
  const animationFrameRef = React.useRef<number>();

  React.useEffect(() => {
    if (!audioContextRef.current) {
      audioContextRef.current = new (window.AudioContext || (window as {webkitAudioContext: typeof AudioContext}).webkitAudioContext)();
    }
    if (!analyserRef.current) {
      analyserRef.current = audioContextRef.current.createAnalyser();
      analyserRef.current.fftSize = 256; // 设置FFT大小，影响频率条数和分辨率
      analyserRef.current.smoothingTimeConstant = 0.5; // 平滑因子
    }

    const analyser = analyserRef.current;
    const bufferLength = analyser.frequencyBinCount; // 获取频率数据数组长度
    const dataArray = new Float32Array(bufferLength);

    const draw = () => {
      if (analyser) {
        analyser.getFloatFrequencyData(dataArray);
        // 聪明的开发杭一: 转换为AudioVisualizer期望的格式
        // 这里只是一个简单的示例，可能需要更复杂的逻辑来分band
        frequencyDataRef.current = [dataArray]; // 假设只有一个band
      }
      animationFrameRef.current = requestAnimationFrame(draw);
    };

    // 聪明的开发杭一: 每次audioData变化时处理
    if (audioData && audioData.length > 0) {
      if (sourceNodeRef.current) {
        sourceNodeRef.current.stop();
        sourceNodeRef.current.disconnect();
      }

      const audioContext = audioContextRef.current;
      if (audioContext) {
        const int16Array = new Int16Array(audioData.buffer, audioData.byteOffset, audioData.byteLength / Int16Array.BYTES_PER_ELEMENT);
        const float32Array = new Float32Array(int16Array.length);
        for (let i = 0; i < int16Array.length; i++) {
          float32Array[i] = int16Array[i] / 32768; // 32768 = 2^15
        }

        const audioBuffer = audioContext.createBuffer(
          1, // channels
          float32Array.length, // frameLength
          audioContext.sampleRate // sampleRate
        );
        audioBuffer.copyToChannel(float32Array, 0);

        const source = audioContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(analyser);
        source.connect(audioContext.destination); // 连接到扬声器，确保能听到声音
        sourceNodeRef.current = source;
        source.start();
      }
    }

    animationFrameRef.current = requestAnimationFrame(draw);

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (sourceNodeRef.current) {
        sourceNodeRef.current.stop();
        sourceNodeRef.current.disconnect();
      }
      if (analyserRef.current) {
        analyserRef.current.disconnect();
      }
      if (audioContextRef.current) {
        audioContextRef.current.close();
        audioContextRef.current = null;
      }
    };
  }, [audioData]); // 聪明的开发杭一: 依赖audioData的变化

  // 聪明的开发杭一: 移除 useMultibandTrackVolume，直接使用内部计算的频率数据
  const frequenciesToDisplay = frequencyDataRef.current;

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
          frequencies={frequenciesToDisplay}
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
