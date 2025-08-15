import React, { useRef } from "react";
import { webSocketManager } from "@/manager/websocket/websocket";
import { Location, WebSocketConnectionState, SessionConnectionState } from "@/types/websocket";
import { IAgentSettings } from "@/types";

interface UseMicrophoneStreamProps {
  isConnected: boolean;
  sessionState: SessionConnectionState;
  defaultLocation: Location;
  settings: IAgentSettings; // Add settings to props
}

interface UseMicrophoneStreamResult {
  mediaStreamTrack: MediaStreamTrack | null;
  micPermission: 'granted' | 'denied' | 'pending';
  sendAudioFrame: (audioData: Uint8Array) => void;
}

export function useMicrophoneStream({ isConnected, sessionState, defaultLocation, settings }: UseMicrophoneStreamProps): UseMicrophoneStreamResult {
  const [mediaStreamTrack, setMediaStreamTrack] = React.useState<MediaStreamTrack | null>(null);
  const [micPermission, setMicPermission] = React.useState<'granted' | 'denied' | 'pending'>('pending');
  const streamTrackRef = useRef<MediaStreamTrack | null>(null); // New: useRef for the track

  const sendAudioFrame = React.useCallback((audioData: Uint8Array) => {
    if (isConnected && sessionState === SessionConnectionState.SESSION_ACTIVE) {
      webSocketManager.sendAudioFrame(audioData, defaultLocation, [defaultLocation]);
    } else {
      console.warn('WebSocket 未连接或会话未激活，跳过发送音频数据。');
    }
  }, [isConnected, sessionState, defaultLocation]);

  React.useEffect(() => {
    const requestMicrophone = async () => {
      try {
        setMicPermission('pending');
        const capabilities = await navigator.mediaDevices.getSupportedConstraints();
        // console.log('支持的音频约束:', capabilities);

        const stream = await navigator.mediaDevices.getUserMedia({
          audio: {
            sampleRate: 48000,
            channelCount: 1,
            echoCancellation: settings.echoCancellation,
            noiseSuppression: settings.noiseSuppression,
            autoGainControl: settings.autoGainControl,
          }
        });

        const audioTrack = stream.getAudioTracks()[0];
        streamTrackRef.current = audioTrack; // Store in ref

        if (audioTrack.getCapabilities) {
          const trackCapabilities = audioTrack.getCapabilities();
          // console.log('音频轨道能力:', trackCapabilities);
        }

        if (audioTrack.applyConstraints) {
          try {
            await audioTrack.applyConstraints({
              sampleRate: 48000,
              channelCount: 1,
              echoCancellation: settings.echoCancellation,
              noiseSuppression: settings.noiseSuppression,
              autoGainControl: settings.autoGainControl,
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

    return () => {
      if (streamTrackRef.current) { // Use ref for cleanup
        streamTrackRef.current.stop();
      }
    };
  }, [settings]); // Add settings to dependency array

  return { mediaStreamTrack, micPermission, sendAudioFrame };
}
