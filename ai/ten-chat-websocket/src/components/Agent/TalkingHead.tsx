"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余Agora RTC SDK导入。
import * as PIXI from "pixi.js";
// 聪明的开发杭二: 移除冗余 Agora RTC SDK 导入
import { Live2DModel } from "pixi-live2d-display-lipsyncpatch/cubism4";
import { Application } from "pixi.js";
import React, { useEffect, useRef } from "react";

const MODEL_URL =
  "https://cdn.jsdelivr.net/gh/guansss/pixi-live2d-display/test/assets/haru/haru_greeter_t03.model3.json";

// 超高质量 RMS lipsync 计算函数
function calcMouthOpenByRMS(dataArray: Uint8Array): number {
  let sum = 0;
  let count = 0;

  console.log(`calcMouthOpenByRMS: dataArray length = ${dataArray.length}`); // Add logging for dataArray length

  // 分析更精确的频段范围，专注于语音频率
  const startIndex = Math.floor(dataArray.length * 0.2); // 20% 开始
  const endIndex = Math.floor(dataArray.length * 0.8);   // 80% 结束

  for (let i = startIndex; i < endIndex; i++) {
    const v = (dataArray[i] - 128) / 128;
    sum += v * v;
    count++;
  }

  if (count === 0) return 0;

  const rms = Math.sqrt(sum / count);

  // 简化的映射函数
  const normalizedValue = Math.max(0, (rms - 0.001) * 15);
  const mouthOpen = Math.min(Math.pow(normalizedValue, 0.8), 1);

  console.log(`calcMouthOpenByRMS: RMS=${rms.toFixed(4)}, MouthOpen=${mouthOpen.toFixed(4)}`); // Add logging

  return mouthOpen;
}

// @ts-expect-error // 聪明的开发杭二: 将 '@ts-ignore' 替换为 '@ts-expect-error'
window.PIXI = PIXI;

// 动态加载 Live2D Cubism 运行时
if (typeof window !== 'undefined') {
  if (!(window as any).Live2DCubismCore) {
    const script = document.createElement('script');
    script.src = 'https://cubism.live2d.com/sdk-web/cubismcore/live2dcubismcore.min.js';
    script.onload = () => {
      console.log('Live2D Cubism runtime loaded');
    };
    document.head.appendChild(script);
  }
}

// 聪明的开发杭二: 修改 audioTrack prop 类型为 Uint8Array
export default function Talkinghead({
  audioTrack,
}: {
  audioTrack?: Uint8Array;
}) {
  // console.log("TalkingHead: Component rendered with audioTrack prop:", audioTrack ? "defined" : "undefined", "length:", audioTrack?.length); // 组件顶层日志
  const containerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<Application>();
  const modelRef = useRef<Live2DModel>();
  const animationIdRef = useRef<number>();
  const fitModelCleanupRef = useRef<() => void>();

  // 聪明的开发杭二: 声明 AudioContext 和 AnalyserNode 的引用
  const audioCtxRef = useRef<AudioContext>();
  const analyserRef = useRef<AnalyserNode>();
  const sourceRef = useRef<AudioBufferSourceNode>(); // Also for source

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

        // 等待 Live2D Cubism 运行时加载完成
    const loadModel = () => {
      if ((window as any).Live2DCubismCore) {
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
        }).catch((error: unknown) => {
          console.error('Failed to load Live2D model:', error);
          if (containerRef.current) {
            containerRef.current.innerHTML = '<div style="display: flex; align-items: center; justify-content: center; height: 100%; color: #666; font-size: 14px;">Live2D 模型加载失败</div>';
          }
        });
      } else {
        // 如果运行时还没加载完成，等待一段时间后重试
        setTimeout(loadModel, 100);
      }
    };

    loadModel();

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
    // console.log("TalkingHead useEffect: AudioTrack state on update:", audioTrack ? "defined" : "undefined", "length:", audioTrack?.length); // useEffect 入口日志
    if (!audioTrack || audioTrack.length === 0) {
      // console.log("TalkingHead: AudioTrack is null or empty, skipping lipsync update.");
      // If audioTrack becomes empty, ensure animation is stopped and audio context is closed
      if (animationIdRef.current) {
        cancelAnimationFrame(animationIdRef.current);
        animationIdRef.current = undefined;
      }
      if (sourceRef.current) {
        try {
          sourceRef.current.stop();
          sourceRef.current.disconnect();
        } catch (e) {
          console.warn("TalkingHead: Error stopping or disconnecting source on empty audioTrack:", e);
        }
        sourceRef.current = undefined;
      }
      if (analyserRef.current) {
        analyserRef.current.disconnect();
        analyserRef.current = undefined;
      }
      if (audioCtxRef.current) {
        audioCtxRef.current.close().catch(e => console.error("Error closing audio context:", e));
        audioCtxRef.current = undefined;
      }
      return;
    }
    // console.log(`TalkingHead: AudioTrack updated with ${audioTrack.length} bytes.`);

    // 初始化 AudioContext 和 AnalyserNode (只创建一次)
    if (!audioCtxRef.current) {
      audioCtxRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      analyserRef.current = audioCtxRef.current.createAnalyser();
      analyserRef.current.fftSize = 2048;
    }

    const currentAudioCtx = audioCtxRef.current;
    const currentAnalyser = analyserRef.current;

    // 添加日志来确认 modelRef.current 和 currentAnalyser 的状态
    // console.log("TalkingHead: Before playAudioData - modelRef.current:", !!modelRef.current, "analyserRef.current:", !!analyserRef.current);

    const playAudioData = async () => {
      // console.log("TalkingHead: playAudioData called. audioTrack (length):", audioTrack.length); // playAudioData 入口日志
      if (!audioTrack || audioTrack.length === 0) {
          // console.log("TalkingHead: audioTrack is null or empty in playAudioData.");
          return;
      }
      // 添加对 audioTrack 长度是否为偶数的检查
      if (audioTrack.length % 2 !== 0) {
          console.warn("TalkingHead: Received audioTrack with odd byte length. Skipping this frame to prevent Int16Array error.", audioTrack.length);
          return;
      }
      // console.log("TalkingHead: audioTrack (first 20 bytes):", audioTrack.slice(0, 20)); // Log first 20 bytes

      // 如果有之前的 source 节点，停止并断开连接
      if (sourceRef.current) {
        try {
          sourceRef.current.stop();
        } catch (e) {
          console.warn("TalkingHead: Attempted to stop an already stopped source or source without buffer.", e);
        }
        sourceRef.current.disconnect();
        sourceRef.current = undefined;
      }

      // 创建 AudioBuffer
      const float32Data = new Float32Array(audioTrack.length / 2);
      const byteLength = audioTrack.buffer.byteLength;
      const int16Array = new Int16Array(audioTrack.buffer, 0, Math.floor(byteLength / 2)); // 确保是偶数长度
      for (let i = 0; i < int16Array.length; i++) {
        float32Data[i] = int16Array[i] / 32768;
      }

      const buffer = currentAudioCtx.createBuffer(
        1,
        float32Data.length,
        48000,
      );
      buffer.copyToChannel(float32Data, 0);

      const newSource = currentAudioCtx.createBufferSource();
      newSource.buffer = buffer;
      if (currentAnalyser) { // 添加空值检查
        newSource.connect(currentAnalyser);
      }
      newSource.start(0); // Start immediately

      sourceRef.current = newSource;

      // Ensure dataArray is correctly sized and accessible
      const dataArray = new Uint8Array(currentAnalyser!.frequencyBinCount); // 添加非空断言

      function animate() {
        // console.log("TalkingHead: animate function called."); // animate 函数入口日志
        // 添加日志来确认 modelRef.current 和 currentAnalyser 的状态，以及 dataArray 的长度
        // console.log("TalkingHead: animate running - modelRef.current:", !!modelRef.current, "currentAnalyser:", !!currentAnalyser, "dataArray.length:", dataArray.length);

        if (!modelRef.current || !currentAnalyser) {
            // console.log("TalkingHead: animate stopped due to missing model or analyser", { model: modelRef.current, analyser: currentAnalyser });
            return;
        }
        currentAnalyser.getByteTimeDomainData(dataArray);
        // console.log(`calcMouthOpenByRMS: Called with dataArray length = ${dataArray.length}`); // calcMouthOpenByRMS 调用前日志
        const mouthOpen = calcMouthOpenByRMS(dataArray);
        // console.log("TalkingHead: animate - mouthOpen value:", mouthOpen); // mouthOpen 值日志

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
        } catch (e) {
          // Intentionally ignore errors during setParameterValueById
        }
        animationIdRef.current = requestAnimationFrame(animate);
      }
      
      // Only start animation if it's not already running
      if (!animationIdRef.current) {
        animate();
      }
    };

    playAudioData();

    return () => {
      // console.log("TalkingHead: Cleaning up useEffect for audioTrack.");
      if (animationIdRef.current) {
        cancelAnimationFrame(animationIdRef.current);
        animationIdRef.current = undefined;
      }
      if (sourceRef.current) {
        try {
          sourceRef.current.stop();
          sourceRef.current.disconnect();
        } catch (e) {
          console.warn("TalkingHead: Error stopping or disconnecting source on cleanup:", e);
        }
        sourceRef.current = undefined;
      }
      if (analyserRef.current) {
        analyserRef.current.disconnect();
        analyserRef.current = undefined;
      }
      if (audioCtxRef.current) {
        audioCtxRef.current.close().catch(e => console.error("Error closing audio context:", e));
        audioCtxRef.current = undefined;
      }
    };
  }, [audioTrack]); // audioTrack as dependency

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
