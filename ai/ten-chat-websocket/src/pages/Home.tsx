import React from "react";
import { useAppSelector, EMobileActiveTab } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import MicrophoneBlock from "@/components/Agent/Microphone";
import { Button } from "@/components/ui/button";
import { MicIconByStatus } from "@/components/Icon";
import ChatCard from "@/components/Chat/ChatCard";
import ConnectionTest from "@/components/Chat/ConnectionTest";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import * as PIXI from 'pixi.js';
// @ts-ignore
import { Live2DModel } from 'pixi-live2d-display-lipsyncpatch/cubism4';
import { Application } from 'pixi.js';

const MODEL_URL = 'https://cdn.jsdelivr.net/gh/guansss/pixi-live2d-display/test/assets/haru/haru_greeter_t03.model3.json';

// 性能监控工具
const performanceMonitor = {
  lastLogTime: 0,
  frameCount: 0,
  logInterval: 5000, // 每5秒记录一次

  logPerformance() {
    this.frameCount++;
    const now = Date.now();
    if (now - this.lastLogTime > this.logInterval) {
      const fps = Math.round((this.frameCount * 1000) / (now - this.lastLogTime));
      console.log(`性能监控: ${fps} FPS, 音频处理频率: 62.5Hz`);
      this.frameCount = 0;
      this.lastLogTime = now;
    }
  }
};

// 防抖函数
function debounce<T extends (...args: any[]) => any>(func: T, wait: number): T {
  let timeout: NodeJS.Timeout;
  return ((...args: any[]) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), wait);
  }) as T;
}

// 超高质量 RMS lipsync 计算函数
function calcMouthOpenByRMS(dataArray: Uint8Array): number {
  let sum = 0;
  let count = 0;

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

  return mouthOpen;
}

// @ts-ignore
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

// 真实的 TalkingHead 组件 - 参考 playground 实现
function TalkingHead({ audioTrack }: { audioTrack?: Uint8Array }) {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const appRef = React.useRef<Application>();
  const modelRef = React.useRef<Live2DModel>();
  const animationIdRef = React.useRef<number>();
  const fitModelCleanupRef = React.useRef<() => void>();

  // 初始化 Live2D，集成 fitModel
  React.useEffect(() => {
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
  React.useEffect(() => {
    if (!audioTrack) return;
    let stopped = false;
    let audioCtx: AudioContext | undefined;
    let source: MediaStreamAudioSourceNode | undefined;
    let analyser: AnalyserNode | undefined;
    let freqArray: Uint8Array;
    let lastMouthOpen = 0;

    function startLipsync() {
      if (!modelRef.current) {
        setTimeout(startLipsync, 200);
        return;
      }

      // 使用优化的音频设置
      audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)({
        sampleRate: 48000, // 降低采样率
        latencyHint: 'interactive',
      });

      // 创建模拟的音频流用于 lipsync
      const stream = new MediaStream();
      source = audioCtx.createMediaStreamSource(stream);
      analyser = audioCtx.createAnalyser();

      // 使用更小的 FFT 大小以减少计算量
      analyser.fftSize = 2048; // 从 8192 降低到 2048
      analyser.smoothingTimeConstant = 0.3; // 增加平滑系数
      analyser.minDecibels = -90;
      analyser.maxDecibels = -10;

      source.connect(analyser);
      const dataArray = new Uint8Array(analyser.fftSize);

      function animate() {
        if (stopped || !modelRef.current || !analyser) return;

        performanceMonitor.logPerformance();

        analyser.getByteTimeDomainData(dataArray);
        const mouthOpen = calcMouthOpenByRMS(dataArray);

        // 只有当变化足够大时才更新，减少不必要的计算
        if (Math.abs(mouthOpen - lastMouthOpen) > 0.01) {
          try {
            const coreModel = modelRef.current.internalModel?.coreModel as { setParameterValueById?: (id: string, value: number) => void };
            if (coreModel && typeof coreModel.setParameterValueById === 'function') {
              const smoothedMouthOpen = Math.min(Math.max(mouthOpen, 0), 1);
              coreModel.setParameterValueById('ParamMouthOpenY', smoothedMouthOpen);
              lastMouthOpen = mouthOpen;
            }
          } catch (e) {
            // 忽略 Live2D 参数设置错误
          }
        }

        animationIdRef.current = requestAnimationFrame(animate);
      }
      animate();
    }
    startLipsync();

    return () => {
      stopped = true;
      if (animationIdRef.current) cancelAnimationFrame(animationIdRef.current);
      if (audioCtx) {
        audioCtx.close().catch(console.warn);
      }
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

// 音频可视化组件 - 参考 playground 实现
function AudioVisualizer(props: {
  type: "agent" | "user"
  frequencies: Float32Array[]
  gap: number
  barWidth: number
  minBarHeight: number
  maxBarHeight: number
  borderRadius: number
}) {
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
          backgroundColor: type === "agent" ? "#0888FF" : "#3B82F6",
          boxShadow: type === "agent" ? "0 0 10px #EAECF0" : "0 0 5px #3B82F6",
        }

        return <span key={index} style={style} />
      })}
    </div>
  )
}

// 多频段音量 hook - 优化性能
function useMultibandTrackVolume(
  track?: MediaStreamTrack,
  bands: number = 20,
  loPass: number = 100,
  hiPass: number = 600
) {
  const [frequencyBands, setFrequencyBands] = React.useState<Float32Array[]>([]);
  const lastBandsRef = React.useRef<Float32Array[]>([]);

  React.useEffect(() => {
    if (!track) {
      return setFrequencyBands(new Array(bands).fill(new Float32Array(0)));
    }

    // 使用优化的音频设置
    const ctx = new AudioContext({
      sampleRate: 48000, // 降低采样率
      latencyHint: 'interactive',
    });

    const mediaStream = new MediaStream([track]);
    const source = ctx.createMediaStreamSource(mediaStream);
    const analyser = ctx.createAnalyser();

    // 使用更小的 FFT 大小以减少计算量
    analyser.fftSize = 2048; // 从 8192 降低到 2048
    analyser.smoothingTimeConstant = 0.3; // 增加平滑系数
    analyser.minDecibels = -90;
    analyser.maxDecibels = -10;

    source.connect(analyser);

    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Float32Array(bufferLength);

    const updateVolume = debounce(() => {
      analyser.getFloatFrequencyData(dataArray);
      let frequencies: Float32Array = new Float32Array(dataArray.length);
      for (let i = 0; i < dataArray.length; i++) {
        frequencies[i] = dataArray[i];
      }
      frequencies = frequencies.slice(loPass, hiPass);

      // 简化的频率标准化算法
      const normalizedFrequencies = frequencies.map(f => {
        const normalized = Math.max(0, (f + 90) / 80);
        return Math.pow(normalized, 1.2);
      });

      const chunkSize = Math.ceil(normalizedFrequencies.length / bands);
      const chunks: Float32Array[] = [];
      for (let i = 0; i < bands; i++) {
        chunks.push(
          normalizedFrequencies.slice(i * chunkSize, (i + 1) * chunkSize)
        );
      }

      // 只有当变化足够大时才更新状态
      const hasSignificantChange = chunks.some((chunk, index) => {
        const lastChunk = lastBandsRef.current[index];
        if (!lastChunk || chunk.length !== lastChunk.length) return true;
        return chunk.some((value, i) => Math.abs(value - lastChunk[i]) > 0.1);
      });

      if (hasSignificantChange) {
        lastBandsRef.current = chunks;
        setFrequencyBands(chunks);
      }
    }, 8); // 8ms 防抖

    // 降低更新频率以减少CPU使用
    const interval = setInterval(updateVolume, 16); // 从 4ms 提升到 16ms (62.5Hz)

    return () => {
      source.disconnect();
      clearInterval(interval);
      ctx.close().catch(console.warn);
    };
  }, [track, loPass, hiPass, bands]);

  return frequencyBands;
}

// 设备选择组件
function MicrophoneDeviceSelect() {
  const [devices, setDevices] = React.useState<Array<{label: string, value: string, deviceId: string}>>([]);
  const [selectedDevice, setSelectedDevice] = React.useState("default");

  React.useEffect(() => {
    // 获取麦克风设备列表
    navigator.mediaDevices.enumerateDevices()
      .then(devices => {
        const audioDevices = devices
          .filter(device => device.kind === 'audioinput')
          .map(device => ({
            label: device.label || `麦克风 ${device.deviceId.slice(0, 8)}`,
            value: device.deviceId || `device-${Math.random()}`, // 确保 value 不为空
            deviceId: device.deviceId
          }));

        if (audioDevices.length > 0) {
          setDevices(audioDevices);
          setSelectedDevice(audioDevices[0].value);
        }
      })
      .catch(error => {
        console.error('获取设备列表失败:', error);
      });
  }, []);

  const handleDeviceChange = (deviceId: string) => {
    setSelectedDevice(deviceId);
    console.log('切换到设备:', deviceId);
  };

  return (
    <Select value={selectedDevice} onValueChange={handleDeviceChange}>
      <SelectTrigger className="w-[180px]">
        <SelectValue placeholder="选择麦克风" />
      </SelectTrigger>
      <SelectContent>
        {devices.map((device) => (
          <SelectItem key={device.value} value={device.value}>
            {device.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function Home() {
  try {
    const mobileActiveTab = useAppSelector(
      (state) => state.global.mobileActiveTab,
    );
    const [mediaStreamTrack, setMediaStreamTrack] = React.useState<MediaStreamTrack | null>(null);
    const [micPermission, setMicPermission] = React.useState<'granted' | 'denied' | 'pending'>('pending');
    const [audioMute, setAudioMute] = React.useState(false);

    // 自动获取麦克风 - 优化音频质量设置
    React.useEffect(() => {
      const requestMicrophone = async () => {
        try {
          setMicPermission('pending');

          // 获取支持的音频约束
          const capabilities = await navigator.mediaDevices.getSupportedConstraints();
          console.log('支持的音频约束:', capabilities);

          // 超高质量音频设置
          const stream = await navigator.mediaDevices.getUserMedia({
            audio: {
              // 优化音频质量设置
              sampleRate: 48000, // 降低采样率
              channelCount: 1, // 单声道，减少数据量
              echoCancellation: false, // 关闭回声消除，保持原始音质
              noiseSuppression: false, // 关闭噪声抑制，保持原始音质
              autoGainControl: false, // 关闭自动增益控制，保持原始音量
            }
          });

          const audioTrack = stream.getAudioTracks()[0];

          // 获取并打印音频轨道的能力
          if (audioTrack.getCapabilities) {
            const trackCapabilities = audioTrack.getCapabilities();
            console.log('音频轨道能力:', trackCapabilities);
          }

          // 尝试设置音频轨道的约束
          if (audioTrack.applyConstraints) {
            try {
              await audioTrack.applyConstraints({
                sampleRate: 48000,
                channelCount: 1,
                echoCancellation: false,
                noiseSuppression: false,
                autoGainControl: false,
              });
              console.log('音频约束应用成功');
            } catch (constraintError) {
              console.warn('音频约束应用失败:', constraintError);
            }
          }

          setMediaStreamTrack(audioTrack);
          setMicPermission('granted');
        } catch (error) {
          console.error('无法访问麦克风:', error);
          setMicPermission('denied');
        }
      };

      requestMicrophone();
    }, []);

    // 根据静音状态决定是否传递 track
    const activeTrack = audioMute ? undefined : (mediaStreamTrack || undefined);
    const subscribedVolumes = useMultibandTrackVolume(activeTrack, 20);

    return (
      <div className="relative mx-auto flex flex-1 min-h-screen flex-col md:h-screen bg-gray-50">
        <Header className="h-[60px]" />
        <Action />
        <div className="mx-2 mb-2 flex h-full max-h-[calc(100vh-108px-24px)] flex-col md:flex-row md:gap-2 flex-1">
          {/* RTC 区域 - 使用固定宽度，移除 flex-1 限制 */}
          <div className={cn(
            "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:w-[400px] md:rounded-lg",
            {
              ["hidden md:block"]: mobileActiveTab === EMobileActiveTab.CHAT,
            },
          )}>
            <div className="flex h-full flex-col min-h-0 bg-gray-50 w-full">
              {/* TalkingHead 区域 - 占据大部分空间 */}
              <div className="flex-1 min-h-0 z-10">
                <div
                  style={{ height: '100%', minHeight: 500 }}
                  className="bg-white rounded-lg shadow-lg border border-gray-200"
                >
                  <TalkingHead audioTrack={audioMute ? undefined : undefined} />
                </div>
              </div>

              {/* 麦克风控制区域 - 放在 TalkingHead 下面，固定高度 */}
              <div className="mt-2 p-3 bg-white rounded-lg shadow-sm border border-gray-200">
                <div className="space-y-3">
                  {/* 麦克风控制 - 一行显示所有元素 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="text-sm font-medium">麦克风</div>
                      <MicrophoneDeviceSelect />
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="icon"
                        className="border-secondary bg-transparent"
                        onClick={() => setAudioMute(!audioMute)}
                      >
                        <MicIconByStatus className="h-5 w-5" active={!audioMute} />
                      </Button>
                    </div>
                  </div>

                  {/* 音频可视化区域 */}
                  <div>
                    <div className="text-sm font-medium text-gray-700 mb-2">
                      音频可视化 {audioMute ? '(已静音)' : '(录音中)'}
                    </div>
                    <div className="flex h-10 flex-col items-center justify-center gap-2 self-stretch rounded-md border border-gray-200 bg-gray-50 p-2">
                      {micPermission === 'granted' && !audioMute ? (
                        <AudioVisualizer
                          type="user"
                          barWidth={3}
                          minBarHeight={2}
                          maxBarHeight={16}
                          frequencies={subscribedVolumes}
                          borderRadius={2}
                          gap={3}
                        />
                      ) : micPermission === 'denied' ? (
                        <div className="text-center text-gray-500">
                          <p className="text-xs">麦克风权限被拒绝</p>
                        </div>
                      ) : audioMute ? (
                        <div className="text-center text-gray-500">
                          <p className="text-xs">麦克风已静音</p>
                        </div>
                      ) : (
                        <div className="text-center text-gray-500">
                          <p className="text-xs">请求麦克风权限中...</p>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* 音频质量信息 */}
                  {micPermission === 'granted' && !audioMute && mediaStreamTrack && (
                    <div className="mt-1">
                      <div className="text-xs text-gray-500">
                        <p>音频设置: 48kHz, 单声道, 优化模式</p>
                        <p>FFT 大小: 2048, 更新频率: 62.5Hz</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* 聊天区域 */}
          <div className="m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg md:flex-1">
            <div className="h-full flex flex-col">
              <div className="p-4 border-b">
                <ConnectionTest />
              </div>
              <div className="flex-1">
                <ChatCard />
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  } catch (error) {
    console.error('Home component error:', error);
    return (
      <div className="relative mx-auto flex flex-1 min-h-screen flex-col md:h-screen bg-gray-50">
        <div className="h-[60px] bg-white border-b border-gray-200 flex items-center justify-center">
          <h1 className="text-xl font-semibold">Ten Chat WebSocket</h1>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center">
            <h2 className="text-2xl font-bold mb-4">应用加载中...</h2>
            <p className="text-gray-600">正在初始化组件...</p>
            <p className="text-red-500 mt-2">错误: {error instanceof Error ? error.message : String(error)}</p>
          </div>
        </div>
      </div>
    );
  }
}

export default Home;