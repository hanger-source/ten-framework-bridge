"use client";

import * as React from "react";
import { Button } from "@/components/ui/button";
import { MicIconByStatus } from "@/components/Icon";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";

// 聪明的开发杭二: 尝试触发 linter 重新评估
export default function MicrophoneBlock(props: {
  sendAudioFrame: (audioData: Uint8Array) => void;
  onMuteChange?: (muted: boolean) => void;
}) {
  const { sendAudioFrame, onMuteChange } = props;
  const [audioMute, setAudioMute] = React.useState(false);
  const [mediaStreamTrack, setMediaStreamTrack] =
    React.useState<MediaStreamTrack | null>(null);
  const [audioContext, setAudioContext] = React.useState<AudioContext | null>(
    null,
  );
  const [microphone, setMicrophone] =
    React.useState<MediaStreamAudioSourceNode | null>(null);
  const [scriptProcessor, setScriptProcessor] =
    React.useState<ScriptProcessorNode | null>(null);

  // 音频处理控制
  const [disableAGC, setDisableAGC] = React.useState(true);
  const [disableNS, setDisableNS] = React.useState(true);
  const [disableAEC, setDisableAEC] = React.useState(true);
  const [showAdvancedSettings, setShowAdvancedSettings] = React.useState(false);

  React.useEffect(() => {
    if (!audioMute) {
      startMicrophone();
    } else {
      stopMicrophone();
    }

    // 通知父组件静音状态变化
    onMuteChange?.(audioMute);

    return () => {
      stopMicrophone();
    };
  }, [audioMute, disableAGC, disableNS, disableAEC, onMuteChange]);

  const startMicrophone = async () => {
    try {
      // 配置音频约束，禁用音频处理
      const audioConstraints: MediaTrackConstraints = {
        echoCancellation: !disableAEC,
        noiseSuppression: !disableNS,
        autoGainControl: !disableAGC,
        // 设置较高的采样率以获得更好的音质
        sampleRate: 48000,
        channelCount: 1,
      };

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: audioConstraints
      });

      const track = stream.getAudioTracks()[0];
      setMediaStreamTrack(track);

      // 检查实际应用的约束
      const settings = track.getSettings();
      console.log("音频设置:", {
        echoCancellation: settings.echoCancellation,
        noiseSuppression: settings.noiseSuppression,
        autoGainControl: settings.autoGainControl,
        sampleRate: settings.sampleRate,
        channelCount: settings.channelCount,
      });

      const context = new (window.AudioContext ||
        (window as any).webkitAudioContext)(); // 聪明的开发杭二: 修正 window.webkitAudioContext 的类型转换错误
      setAudioContext(context);

      const source = context.createMediaStreamSource(stream);
      setMicrophone(source);

      // Create a ScriptProcessorNode to process audio data
      const processor = context.createScriptProcessor(2048, 1, 1); // bufferSize, inputChannels, outputChannels
      setScriptProcessor(processor);

      processor.onaudioprocess = (event) => {
        if (!audioMute) {
          const inputBuffer = event.inputBuffer.getChannelData(0);
          const pcmData = new Int16Array(inputBuffer.length);
          for (let i = 0; i < inputBuffer.length; i++) {
            // Convert float to 16-bit PCM
            pcmData[i] = Math.max(-1, Math.min(1, inputBuffer[i])) * 0x7fff;
          }
          sendAudioFrame(new Uint8Array(pcmData.buffer));
        }
      };

      source.connect(processor);
      processor.connect(context.destination);
    } catch (error) {
      console.error("Error accessing microphone:", error);
      setAudioMute(true); // 如果出错，静音麦克风
    }
  };

  const stopMicrophone = () => {
    if (microphone) {
      microphone.disconnect();
      setMicrophone(null);
    }
    if (scriptProcessor) {
      scriptProcessor.disconnect();
      setScriptProcessor(null);
    }
    if (audioContext) {
      audioContext.close();
      setAudioContext(null);
    }
    if (mediaStreamTrack) {
      mediaStreamTrack.stop();
      setMediaStreamTrack(null);
    }
  };

  const onClickMute = () => {
    setAudioMute(!audioMute);
  };

  return (
    <div className="flex flex-col space-y-3">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium">麦克风</div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="border-secondary bg-transparent"
            onClick={onClickMute}
          >
            <MicIconByStatus className="h-5 w-5" active={!audioMute} />
          </Button>
        </div>
      </div>

      {/* 高级音频设置 */}
      <div className="space-y-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setShowAdvancedSettings(!showAdvancedSettings)}
          className="text-xs text-gray-600 hover:text-gray-800"
        >
          {showAdvancedSettings ? "隐藏" : "显示"} 音频设置
        </Button>

        {showAdvancedSettings && (
          <div className="space-y-3 p-3 bg-gray-50 rounded-lg">
            <div className="text-xs font-medium text-gray-700 mb-2">
              音频处理设置 (可能影响音质)
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="disable-agc" className="text-xs">
                禁用自动增益控制 (AGC)
              </Label>
              <Switch
                id="disable-agc"
                checked={disableAGC}
                onCheckedChange={setDisableAGC}
              />
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="disable-ns" className="text-xs">
                禁用噪声抑制 (NS)
              </Label>
              <Switch
                id="disable-ns"
                checked={disableNS}
                onCheckedChange={setDisableNS}
              />
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="disable-aec" className="text-xs">
                禁用回声消除 (AEC)
              </Label>
              <Switch
                id="disable-aec"
                checked={disableAEC}
                onCheckedChange={setDisableAEC}
              />
            </div>

            <div className="text-xs text-gray-500 mt-2">
              提示：禁用这些功能可能获得更原始的音质，但可能包含更多噪音
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
