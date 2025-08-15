"use client";

import React, { useState, useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { MicIcon } from "@/components/icons/mic";
import MicrophoneDeviceSelect from "@/components/Agent/MicrophoneDeviceSelect"; // Import MicrophoneDeviceSelect
import { SessionConnectionState, Location } from "@/types/websocket";
import { useMicrophoneStream } from "@/hooks/useMicrophoneStream";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { useAgentSettings } from "@/hooks/useAgentSettings"; // Import useAgentSettings

interface MicrophoneProps {
  isConnected: boolean;
  sessionState: SessionConnectionState;
  defaultLocation: Location;
  onMuteChange: (isMuted: boolean) => void;
  onAudioDataCaptured?: (audioData: Uint8Array) => void; // New prop for audio data
}

export const Microphone: React.FC<MicrophoneProps> = ({
  isConnected,
  sessionState,
  defaultLocation,
  onMuteChange,
  onAudioDataCaptured,
}) => {
  const [audioMute, setAudioMute] = useState(true); // Changed initial state to true
  const [showAdvancedSettings, setShowAdvancedSettings] = useState(false);
  const [audioContext, setAudioContext] = useState<AudioContext | null>(null);
  const [microphone, setMicrophone] = useState<MediaStreamAudioSourceNode | null>(null);
  const [scriptProcessor, setScriptProcessor] = useState<ScriptProcessorNode | null>(null);
  const [mediaStreamTrack, setMediaStreamTrack] = useState<MediaStreamTrack | null>(null);

  const audioMuteRef = useRef(audioMute);
  const isConnectedRef = useRef(isConnected);
  const sessionStateRef = useRef(sessionState);

  useEffect(() => {
    audioMuteRef.current = audioMute;
    isConnectedRef.current = isConnected;
    sessionStateRef.current = sessionState;
  }, [audioMute, isConnected, sessionState]);

  const { agentSettings, updateSettings } = useAgentSettings(); // Use agent settings

  const { sendAudioFrame } = useMicrophoneStream({
    isConnected,
    sessionState,
    defaultLocation,
    settings: agentSettings, // Pass agent settings to useMicrophoneStream
  });

  useEffect(() => {
    if (!audioMute && isConnected && sessionState === SessionConnectionState.SESSION_ACTIVE) {
      startMicrophone();
    } else {
      stopMicrophone();
    }
    return () => {
      // Removed: On cleanup, clear recorded audio as well
      // recordedAudioChunksRef.current = [];
      // setRecordedChunksCount(0);
    };
  }, [audioMute, isConnected, sessionState, agentSettings.autoGainControl, agentSettings.noiseSuppression, agentSettings.echoCancellation, onMuteChange]); // Add agentSettings to dependencies

  const startMicrophone = async () => {
    console.log('MicrophoneBlock: startMicrophone called');
    try {
      // 配置音频约束，禁用音频处理
      const audioConstraints: MediaTrackConstraints = {
        echoCancellation: agentSettings.echoCancellation,
        noiseSuppression: agentSettings.noiseSuppression,
        autoGainControl: agentSettings.autoGainControl,
        // 设置较高的采样率以获得更好的音质
        sampleRate: 16000,
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
        (window as any).webkitAudioContext)({ sampleRate: 16000 }); // 聪明的开发杭二: 修正 window.webkitAudioContext 的类型转换错误, 并指定采样率
      setAudioContext(context);

      const source = context.createMediaStreamSource(stream);
      setMicrophone(source);

      // Create a ScriptProcessorNode to process audio data
      const processor = context.createScriptProcessor(2048, 1, 1); // bufferSize, inputChannels, outputChannels
      setScriptProcessor(processor);

      processor.onaudioprocess = (event) => {
        console.log('MicrophoneBlock: onaudioprocess triggered, audioMute (ref):', audioMuteRef.current, 'isConnected (ref):', isConnectedRef.current, 'sessionState (ref):', sessionStateRef.current);
        console.log(`MicrophoneBlock: Pre-send check - audioMuteRef.current: ${audioMuteRef.current}, isConnectedRef.current: ${isConnectedRef.current}, sessionStateRef.current: ${sessionStateRef.current}, Expected: ${SessionConnectionState.SESSION_ACTIVE}`);
        if (!audioMuteRef.current && isConnectedRef.current && sessionStateRef.current === SessionConnectionState.SESSION_ACTIVE) { // Use refs for latest values
          const inputBuffer = event.inputBuffer.getChannelData(0);
          const pcmData = new Int16Array(inputBuffer.length);
          for (let i = 0; i < inputBuffer.length; i++) {
            // Convert float to 16-bit PCM
            pcmData[i] = Math.max(-1, Math.min(1, inputBuffer[i])) * 0x7fff;
          }
          const pcmUint8 = new Uint8Array(pcmData.buffer);
          sendAudioFrame(pcmUint8);

          // New: Pass captured audio data to parent via callback
          onAudioDataCaptured?.(pcmUint8);
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
    console.log('MicrophoneBlock: stopMicrophone called');
    if (microphone) {
      microphone.disconnect();
      console.log('MicrophoneBlock: microphone disconnected');
      setMicrophone(null); // Ensure state is updated to null
    }
    if (scriptProcessor) {
      scriptProcessor.onaudioprocess = null; // Explicitly stop processing
      scriptProcessor.disconnect();
      console.log('MicrophoneBlock: scriptProcessor disconnected and onaudioprocess set to null');
      setScriptProcessor(null); // Ensure state is updated to null
    }
    if (audioContext) {
      audioContext.close().then(() => {
        console.log('MicrophoneBlock: audioContext closed');
        setAudioContext(null);
      }).catch(error => {
        console.error('MicrophoneBlock: Failed to close audioContext', error);
      });
    }
    if (mediaStreamTrack) {
      mediaStreamTrack.stop();
      setMediaStreamTrack(null);
      console.log('MicrophoneBlock: mediaStreamTrack stopped');
    }
  };

  const onClickMute = () => {
    const newMuteState = !audioMute;
    setAudioMute(newMuteState);
    onMuteChange?.(newMuteState); // Call onMuteChange here
  };

  // Removed: downloadRecordedAudio function
  // const downloadRecordedAudio = () => { ... };

  return (
    <div className="flex flex-col space-y-3">
      <div className="flex items-center gap-3"> {/* Use gap for spacing */} 
        <div className="text-sm font-medium">麦克风</div>
        <MicrophoneDeviceSelect />
        <Button
          variant="outline"
          size="icon"
          className="border-secondary bg-transparent"
          onClick={onClickMute}
        >
          <MicIcon className="h-5 w-5" active={!audioMute} />
        </Button>
          {/* Removed: Download Button */}
          {/* <Button
            variant="outline"
            size="sm"
            onClick={downloadRecordedAudio}
            disabled={recordedChunksCount === 0}
          >
            下载录音 ({recordedChunksCount})
          </Button> */}
        </div>
      {/* 高级音频设置 */}
      {/* <div className="space-y-2">
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
                启用自动增益控制 (AGC)
              </Label>
              <Switch
                id="disable-agc"
                checked={agentSettings.autoGainControl} // Bind to agentSettings
                onCheckedChange={(checked) => updateSettings({ autoGainControl: checked })} // Update settings
              />
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="disable-ns" className="text-xs">
                启用噪声抑制 (NS)
              </Label>
              <Switch
                id="disable-ns"
                checked={agentSettings.noiseSuppression} // Bind to agentSettings
                onCheckedChange={(checked) => updateSettings({ noiseSuppression: checked })} // Update settings
              />
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="disable-aec" className="text-xs">
                启用回声消除 (AEC)
              </Label>
              <Switch
                id="disable-aec"
                checked={agentSettings.echoCancellation} // Bind to agentSettings
                onCheckedChange={(checked) => updateSettings({ echoCancellation: checked })} // Update settings
              />
            </div>

            <div className="text-xs text-gray-500 mt-2">
              提示：禁用这些功能可能获得更原始的音质，但可能包含更多噪音
            </div>
          </div>
        )}
      </div> */}
    </div>
  );
};
