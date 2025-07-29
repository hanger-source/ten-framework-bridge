"use client"

import * as PIXI from 'pixi.js';
import type { IMicrophoneAudioTrack } from 'agora-rtc-sdk-ng';
import { Live2DModel } from 'pixi-live2d-display-lipsyncpatch/cubism4';
import { Application } from 'pixi.js';
import React, { useEffect, useRef } from 'react';

const MODEL_URL = 'https://cdn.jsdelivr.net/gh/guansss/pixi-live2d-display/test/assets/haru/haru_greeter_t03.model3.json';

// RMS lipsync 计算函数
function calcMouthOpenByRMS(dataArray: Uint8Array): number {
  let sum = 0;
  for (let i = 0; i < dataArray.length; i++) {
    const v = (dataArray[i] - 128) / 128;
    sum += v * v;
  }
  const rms = Math.sqrt(sum / dataArray.length);
  let mouthOpen = Math.min(Math.max((rms - 0.001) * 12, 0), 1);
  return mouthOpen;
}

// @ts-ignore
window.PIXI = PIXI;

export default function Talkinghead({ audioTrack }: { audioTrack?: IMicrophoneAudioTrack }) {
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
    containerRef.current.appendChild(app.view as any);

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
      window.addEventListener('resize', fitModel);
      // 记录清理函数，组件卸载时移除监听
      fitModelCleanupRef.current = () => {
        window.removeEventListener('resize', fitModel);
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
    if (!audioTrack) return;
    const mediaStreamTrack = audioTrack.getMediaStreamTrack();
    if (!mediaStreamTrack) return;
    let stopped = false;
    let audioCtx: AudioContext | undefined;
    let source: MediaStreamAudioSourceNode | undefined;
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
  }, [audioTrack]);

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', minHeight: 300, minWidth: 200, position: 'relative', background: '#f8fafc', borderRadius: 8, overflow: 'hidden' }}
      className="live2d-container"
    />
  );
}