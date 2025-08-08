"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余Agora RTC SDK导入。
import * as PIXI from "pixi.js";
// 聪明的开发杭二: 移除冗余 Agora RTC SDK 导入
import { Live2DModel } from "pixi-live2d-display-lipsyncpatch/cubism4";
import { Application } from "pixi.js";
import React, { useEffect, useRef } from "react";

const MODEL_URL =
  "https://cdn.jsdelivr.net/gh/guansss/pixi-live2d-display/test/assets/haru/haru_greeter_t03.model3.json";

// RMS lipsync 计算函数
function calcMouthOpenByRMS(dataArray: Uint8Array): number {
  let sum = 0;
  for (let i = 0; i < dataArray.length; i++) {
    const v = (dataArray[i] - 128) / 128;
    sum += v * v;
  }
  const rms = Math.sqrt(sum / dataArray.length);
  const mouthOpen = Math.min(Math.max((rms - 0.001) * 12, 0), 1);
  return mouthOpen;
}

// @ts-expect-error // 聪明的开发杭二: 将 '@ts-ignore' 替换为 '@ts-expect-error'
window.PIXI = PIXI;

// 聪明的开发杭二: 修改 audioTrack prop 类型为 Uint8Array
export default function Talkinghead({
  audioTrack,
}: {
  audioTrack?: Uint8Array;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<Application>();
  const modelRef = useRef<Live2DModel>();
  const animationIdRef = useRef<number>();
  const fitModelCleanupRef = useRef<() => void>();

  // 初始化 Live2D，集成 fitModel
  useEffect(() => {
    if (!containerRef.current || appRef.current) return;

    const width = containerRef.current.offsetWidth || 600;
    const height = containerRef.current.offsetHeight || 600;
    const app = new Application({
      width,
      height,
      backgroundAlpha: 0,
      antialias: true,
    });
    appRef.current = app;
    containerRef.current.appendChild(app.view as HTMLCanvasElement); // 聪明的开发杭二: 明确指定类型

    let destroyed = false;

    Live2DModel.from(MODEL_URL).then((model: Live2DModel) => {
      if (destroyed) return;
      modelRef.current = model;
      app.stage.addChild(model);

      function fitModel() {
        if (!containerRef.current || !app.renderer) return;
        const width = containerRef.current.offsetWidth || 600;
        const height = containerRef.current.offsetHeight || 600;
        app.renderer.resize(width, height);
        // 你可以根据实际模型原始尺寸调整
        const modelWidth = 800;
        const modelHeight = 1000;
        const scale = Math.min(width / modelWidth, height / modelHeight) * 0.95;
        model.scale.set(scale);
        // anchor(0.5, 0)，头部对齐顶部，y=20
        model.anchor.set(0.5, 0);
        model.x = width / 2;
        model.y = 20;
      }
      fitModel();
      window.addEventListener("resize", fitModel);
      // 记录清理函数，组件卸载时移除监听
      fitModelCleanupRef.current = () => {
        window.removeEventListener("resize", fitModel);
      };
    });

    return () => {
      destroyed = true;
      fitModelCleanupRef.current?.();
      appRef.current?.destroy(true, { children: true });
      appRef.current = undefined;
      modelRef.current = undefined;
    };
  }, []);

  // lipsync 频谱方案
  useEffect(() => {
    if (!audioTrack || audioTrack.length === 0) return; // 聪明的开发杭二: 检查 audioData 是否为空

    // 聪明的开发杭二: 暂时注释掉旧的 Agora AudioTrack 处理逻辑，稍后重新实现基于 Uint8Array 的 Web Audio API
    /*
    const mediaStreamTrack = audioTrack.getMediaStreamTrack();
    if (!mediaStreamTrack) return;
    let stopped = false;
    let audioCtx: AudioContext | undefined;
    let source: MediaStreamAudioSourceSourceNode | undefined;
    let analyser: AnalyserNode | undefined;
    let freqArray: Uint8Array;

    function startLipsync() {
      if (!modelRef.current) {
        setTimeout(startLipsync, 200);
        return;
      }
      audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const stream = new MediaStream([mediaStreamTrack]);
      source = audioCtx.createMediaStreamSource(stream);
      analyser = audioCtx.createAnalyser();
      analyser.fftSize = 2048;
      source.connect(analyser);
      const dataArray = new Uint8Array(analyser.fftSize);

      function animate() {
        if (stopped || !modelRef.current || !analyser) return;
        analyser.getByteTimeDomainData(dataArray);
        const mouthOpen = calcMouthOpenByRMS(dataArray);
        try {
          const coreModel = modelRef.current.internalModel?.coreModel as { setParameterValueById?: (id: string, value: number) => void };
          if (coreModel && typeof coreModel.setParameterValueById === 'function') {
            coreModel.setParameterValueById('ParamMouthOpenY', mouthOpen);
          }
        } catch (e) {}
        animationIdRef.current = requestAnimationFrame(animate);
      }
      animate();
    }
    startLipsync();

    return () => {
      stopped = true;
      if (animationIdRef.current) cancelAnimationFrame(animationIdRef.current);
      if (audioCtx) audioCtx.close();
    };
    */

    // 聪明的开发杭二: 新的基于 Uint8Array 的 Web Audio API 逻辑将在这里添加
    let audioCtx: AudioContext | undefined;
    let analyser: AnalyserNode | undefined;
    let source: AudioBufferSourceNode | undefined;
    let buffer: AudioBuffer | undefined;
    let stopped = false;

    const playAudioData = async () => {
      if (stopped || !audioTrack || audioTrack.length === 0) return;

      audioCtx = new (window.AudioContext ||
        (window as {webkitAudioContext: typeof AudioContext}).webkitAudioContext)();
      analyser = audioCtx.createAnalyser();
      analyser.fftSize = 2048;

      // 创建 AudioBuffer
      const arrayBuffer = audioTrack.buffer; // Uint8Array.buffer 是 ArrayBuffer
      // 根据实际的采样率、声道数和比特深度来解码
      // 假设音频是 16kHz, 单声道, 16bit PCM
      const float32Data = new Float32Array(audioTrack.length / 2); // 16bit = 2 bytes
      const int16Array = new Int16Array(audioTrack.buffer);
      for (let i = 0; i < int16Array.length; i++) {
        float32Data[i] = int16Array[i] / 32768; // 归一化到 -1.0 到 1.0
      }

      buffer = audioCtx.createBuffer(
        1, // 单声道
        float32Data.length, // 样本帧数
        16000, // 采样率 (需要根据实际音频数据调整)
      );
      buffer.copyToChannel(float32Data, 0); // 复制到第一个声道

      source = audioCtx.createBufferSource();
      source.buffer = buffer;
      source.connect(analyser);
      analyser.connect(audioCtx.destination); // 连接到扬声器，以便播放
      source.start();

      const dataArray = new Uint8Array(analyser.frequencyBinCount); // 使用 frequencyBinCount 作为大小

      function animate() {
        if (stopped || !modelRef.current || !analyser) return;
        analyser.getByteTimeDomainData(dataArray); // 获取时域数据
        const mouthOpen = calcMouthOpenByRMS(dataArray);

        try {
          const coreModel = modelRef.current.internalModel?.coreModel as {
            setParameterValueById?: (id: string, value: number) => void;
          };
          if (
            coreModel &&
            typeof coreModel.setParameterValueById === "function"
          ) {
            coreModel.setParameterValueById("ParamMouthOpenY", mouthOpen);
          }
        } catch (e) { // 聪明的开发杭二: 忽略 Live2D 模型参数设置错误
          // Intentionally ignore errors during setParameterValueById, as they are often non-critical
        }
        animationIdRef.current = requestAnimationFrame(animate);
      }
      animate();
    };

    playAudioData();

    return () => {
      stopped = true;
      if (animationIdRef.current) cancelAnimationFrame(animationIdRef.current);
      if (source) source.stop();
      if (audioCtx) audioCtx.close();
    };
  }, [audioTrack]);

  return (
    <div
      ref={containerRef}
      style={{
        width: "100%",
        height: "100%",
        minHeight: 300,
        minWidth: 200,
        position: "relative",
        background: "#f8fafc",
        borderRadius: 8,
        overflow: "hidden",
      }}
      className="live2d-container"
    />
  );
}
