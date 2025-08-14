import React, { useRef, useState, useCallback } from "react";

// 辅助函数：拼接 Uint8Array 数组
function concatenateUint8Arrays(arrays: Uint8Array[]): Uint8Array {
  let totalLength = 0;
  for (const arr of arrays) {
    totalLength += arr.length;
  }
  const result = new Uint8Array(totalLength);
  let offset = 0;
  for (const arr of arrays) {
    result.set(arr, offset);
    offset += arr.length;
  }
  return result;
}

// 辅助函数：向 DataView 写入字符串
function writeString(view: DataView, offset: number, string: string): void {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i));
  }
}

// 辅助函数：编码 WAV 文件
function encodeWAV(samples: Int16Array, sampleRate: number, numChannels: number): ArrayBuffer {
  const bytesPerSample = 2; // 16-bit PCM
  const bitRate = sampleRate * numChannels * bytesPerSample * 8; // Bit rate for header

  const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample);
  const view = new DataView(buffer);

  /* RIFF identifier */
  writeString(view, 0, 'RIFF');
  /* file length */
  view.setUint32(4, 36 + samples.length * bytesPerSample, true);
  /* RIFF type */
  writeString(view, 8, 'WAVE');
  /* format chunk identifier */
  writeString(view, 12, 'fmt ');
  /* format chunk length */
  view.setUint32(16, 16, true);
  /* sample format (raw PCM) */
  view.setUint16(20, 1, true);
  /* channel count */
  view.setUint16(22, numChannels, true);
  /* sample rate */
  view.setUint32(24, sampleRate, true);
  /* byte rate (sampleRate * numChannels * bytesPerSample) */
  view.setUint32(28, sampleRate * numChannels * bytesPerSample, true);
  /* block align (numChannels * bytesPerSample) */
  view.setUint16(32, numChannels * bytesPerSample, true);
  /* bits per sample */
  view.setUint16(34, bytesPerSample * 8, true);
  /* data chunk identifier */
  writeString(view, 36, 'data');
  /* data chunk length */
  view.setUint32(40, samples.length * bytesPerSample, true);

  // Write PCM data
  let offset = 44;
  for (let i = 0; i < samples.length; i++) {
    view.setInt16(offset, samples[i], true);
    offset += bytesPerSample;
  }

  return buffer;
}

interface UseAudioRecorderResult {
  recordedChunksCount: number;
  onAudioDataCaptured: (audioData: Uint8Array) => void; // Callback to receive audio data
  downloadRecordedAudio: () => void;
  clearRecordedAudio: () => void; // New: Function to clear recorded data
}

export function useAudioRecorder(): UseAudioRecorderResult {
  const recordedAudioChunksRef = useRef<Uint8Array[]>([]);
  const [recordedChunksCount, setRecordedChunksCount] = useState(0);

  // Callback to receive audio data from the microphone component
  const onAudioDataCaptured = useCallback((audioData: Uint8Array) => {
    recordedAudioChunksRef.current.push(audioData);
    setRecordedChunksCount(prev => prev + 1);
  }, []);

  // Function to clear recorded data
  const clearRecordedAudio = useCallback(() => {
    recordedAudioChunksRef.current = [];
    setRecordedChunksCount(0);
    console.log("录制的音频数据已清除。");
  }, []);

  // Function to download the recorded audio as WAV
  const downloadRecordedAudio = useCallback(() => {
    if (recordedAudioChunksRef.current.length === 0) {
      console.warn("没有录制的音频数据可以下载。");
      return;
    }

    // Combine all recorded Uint8Arrays into one
    const fullPcmDataUint8 = concatenateUint8Arrays(recordedAudioChunksRef.current);
    const fullPcmDataInt16 = new Int16Array(fullPcmDataUint8.buffer);

    // Assume sampleRate, numChannels from initial microphone setup (e.g., 48000, 1)
    const sampleRate = 16000; 
    const numChannels = 1;

    const wavBuffer = encodeWAV(fullPcmDataInt16, sampleRate, numChannels);
    const blob = new Blob([wavBuffer], { type: 'audio/wav' });
    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = 'recorded_audio.wav';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    clearRecordedAudio(); // Clear data after download
    console.log("录制的音频已下载。");
  }, [clearRecordedAudio]);

  return { recordedChunksCount, onAudioDataCaptured, downloadRecordedAudio, clearRecordedAudio };
}
