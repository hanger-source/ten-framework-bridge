import React, { useRef, useEffect, useState, useCallback } from 'react';
import { webSocketManager } from '@/manager/websocket/websocket';
import { AudioFrame, MessageType, Message } from '@/types/websocket';

interface AudioStreamPlayerProps {
  // 可以根据需要添加 props，例如音量控制等
}

const AudioStreamPlayer: React.FC<AudioStreamPlayerProps> = () => {
  const audioContextRef = useRef<AudioContext | null>(null);
  const audioQueueRef = useRef<AudioBuffer[]>([]);
  const isPlayingRef = useRef<boolean>(false);
  const nextStartTimeRef = useRef<number>(0);
  const [isPlaying, setIsPlaying] = useState(false);

  const initAudioContext = useCallback(() => {
    if (!audioContextRef.current) {
      audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      audioContextRef.current.resume(); // 确保音频上下文是运行状态
      console.log('AudioContext initialized and resumed.');
    }
    return audioContextRef.current;
  }, []);

  const processAudioBuffer = useCallback(async (audioData: Uint8Array, sampleRate: number, numberOfChannels: number) => {
    const audioContext = initAudioContext();
    if (!audioContext) return;

    // 将 Uint8Array (PCM) 转换为 Float32Array
    // 假设是 16-bit little-endian PCM
    const dataView = new DataView(audioData.buffer, audioData.byteOffset, audioData.byteLength);
    const float32Array = new Float32Array(audioData.byteLength / 2); // 16-bit PCM = 2 bytes per sample
    for (let i = 0; i < float32Array.length; i++) {
      const int16 = dataView.getInt16(i * 2, true); // true for little-endian
      float32Array[i] = int16 / 32768; // Normalize to -1 to 1
    }

    const audioBuffer = audioContext.createBuffer(numberOfChannels, float32Array.length / numberOfChannels, sampleRate);
    for (let channel = 0; channel < numberOfChannels; channel++) {
      const nowBuffering = audioBuffer.getChannelData(channel);
      // 交错数据需要处理，这里简化为单声道或直接复制
      if (numberOfChannels === 1) {
        nowBuffering.set(float32Array);
      } else {
        // 对于多声道，需要根据实际数据格式进行交错处理
        // 这是一个简化的示例，假设数据是交错的
        for (let i = 0; i < nowBuffering.length; i++) {
          nowBuffering[i] = float32Array[i * numberOfChannels + channel];
        }
      }
    }

    audioQueueRef.current.push(audioBuffer);
    if (!isPlayingRef.current) {
      playNextBuffer();
    }
  }, [initAudioContext]);

  const playNextBuffer = useCallback(() => {
    if (audioQueueRef.current.length === 0) {
      isPlayingRef.current = false;
      setIsPlaying(false);
      return;
    }

    const audioContext = audioContextRef.current;
    if (!audioContext) return;

    const audioBuffer = audioQueueRef.current.shift();
    if (audioBuffer) {
      const source = audioContext.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(audioContext.destination);

      const currentTime = audioContext.currentTime;
      if (nextStartTimeRef.current < currentTime) {
        nextStartTimeRef.current = currentTime;
      }

      source.start(nextStartTimeRef.current);
      nextStartTimeRef.current += audioBuffer.duration;
      isPlayingRef.current = true;
      setIsPlaying(true);

      source.onended = () => {
        // 当一个 buffer 播放完毕后，尝试播放下一个
        playNextBuffer();
      };
    }
  }, []);

  useEffect(() => {
    const handleAudioFrame = (rawMessage: Message) => {
      const message = rawMessage as AudioFrame; // Explicitly cast to AudioFrame
      console.log('AudioStreamPlayer: Received audio frame (full message)', message);
      if (message.buf && typeof message.sample_rate === 'number' && typeof message.number_of_channel === 'number') { // Check number_of_channel
        // 假设 bits_per_sample 总是 16
        // 过滤掉空的音频帧
        if (message.buf.byteLength > 0) {
          processAudioBuffer(message.buf, message.sample_rate, message.number_of_channel); // Pass number_of_channel
        } else {
          console.log('AudioStreamPlayer: Received empty audio frame, skipping playback.');
        }
      } else {
        console.warn('AudioStreamPlayer: Incomplete audio frame received', message);
      }
    };

    const unsubscribe = webSocketManager.onMessage(MessageType.AUDIO_FRAME, handleAudioFrame);

    return () => {
      unsubscribe();
      // 清理音频上下文
      if (audioContextRef.current) {
        audioContextRef.current.close().catch(console.error);
        audioContextRef.current = null;
      }
    };
  }, [processAudioBuffer]);

  return (
    <div className="audio-stream-player">
      {/* 您可以在这里添加一些 UI 来指示播放状态，例如一个简单的文本或动画 */}
      {isPlaying ? <p className="text-sm text-green-600 animate-pulse">AI 正在说话...</p> : <p className="text-sm text-gray-500">AI 待命中...</p>}
    </div>
  );
};

export default AudioStreamPlayer;