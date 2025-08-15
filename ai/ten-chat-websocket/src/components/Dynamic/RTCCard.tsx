"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import { useAppSelector, useAppDispatch, useIsCompactLayout } from "@/common";
import { webSocketManager } from "@/manager/websocket/websocket";
import {
  addChatItem,
  setVoiceType,
  setOptions,
  setWebsocketConnectionState,
} from "@/store/reducers/global";
import Avatar from "@/components/Agent/AvatarTrulience";
import TalkingheadBlock from "@/components/Agent/TalkingHead";
import {
  WebSocketEvents,
  WebSocketMessageType,
  IAudioFrame,
  IDataMessage,
  IDataMessageRaw,
  IDataMessageJson,
} from "@/manager/websocket/types";
import { RootState } from "@/store";
import {
  IChatItem,
  EMessageType,
  EMessageDataType,
  VoiceType,
} from "@/types";
// import NetworkIndicator from "@/components/Dynamic/NetworkIndicator"; // Removed
import DynamicChatCard from "@/components/Chat/ChatCard";
import { Message, MessageType, WebSocketConnectionState } from "@/types/websocket";
import { useMicrophoneStream } from "@/hooks/useMicrophoneStream"; // Import useMicrophoneStream
import { useWebSocketSession } from "@/hooks/useWebSocketSession"; // Import useWebSocketSession
import { useAgentSettings } from "@/hooks/useAgentSettings"; // Import useAgentSettings
import { Microphone } from "@/components/Agent/Microphone"; // Import Microphone
import AudioVisualizer from "@/components/Agent/AudioVisualizer"; // Import AudioVisualizer
import { Button } from "@/components/ui/button"; // Import Button
import { useState } from "react"; // Import useState

let hasInit: boolean = false;

export default function RTCCard({
  className,
}: {
  className?: string;
}) {
  const dispatch = useAppDispatch();
  const options = useAppSelector((state: RootState) => state.global.options);
  const trulienceSettings = useAppSelector(
    (state: RootState) => state.global.trulienceSettings,
  );
  const websocketConnectionState = useAppSelector(
    (state: RootState) => state.global.websocketConnectionState,
  );
  const { userId, channel } = options;
  const [remoteAudioData, setRemoteAudioData] = React.useState<Uint8Array>();
  const useTrulienceAvatar = false; // Temporarily force to false for debugging
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;
  const selectedGraphId = useAppSelector(
    (state: RootState) => state.global.selectedGraphId,
  );

  const isCompactLayout = useIsCompactLayout();
  const { isConnected, sessionState, defaultLocation } = useWebSocketSession();
  const { agentSettings } = useAgentSettings();
  const { mediaStreamTrack, micPermission, sendAudioFrame } = useMicrophoneStream({ isConnected, sessionState, defaultLocation, settings: agentSettings }); // Pass settings and get micPermission and sendAudioFrame
  const [audioMute, setAudioMute] = React.useState(true);
  // Live2D 控制
  const [showLive2D, setShowLive2D] = useState(true); 
  // const onAudioDataCaptured = (audioData: Uint8Array) => {}; // Dummy function for Microphone

  React.useEffect(() => {
    if (!options.channel) {
      return;
    }
    init(); // 聪明的开发杭二: 重新启用 init 调用以注册 WebSocket 消息监听器

    // Return cleanup function to disconnect only if connected and not manual disconnect
    return () => {
      // No automatic disconnect here, rely on Action.tsx stopSession
    };
  }, [options.channel]); // options.channel is the only dependency as webSocketManager.connect is removed

  const init = async () => {
    console.log("[websocket] init");
    webSocketManager.onMessage(MessageType.DATA, onTextChanged);
    webSocketManager.onMessage(MessageType.AUDIO_FRAME, onRemoteAudioTrack as (message: Message) => void);

    // await webSocketManager.connect(); // Removed automatic connect

    dispatch(
      setOptions({
        ...options,
      }),
    );
    hasInit = true;
  };

  const destory = async () => {
    console.log(`[${new Date().toISOString()}] RTCCard: Destroying WebSocket connection.`);
    webSocketManager.offMessage(MessageType.DATA, onTextChanged);
    webSocketManager.offMessage(MessageType.AUDIO_FRAME, onRemoteAudioTrack as (message: Message) => void);
    webSocketManager.disconnect();
    hasInit = false;
  };

  const onRemoteAudioTrack = (message: Message) => {
    // console.log("RTCCard: onRemoteAudioTrack called with message:", message.type);
    if (message.type === MessageType.AUDIO_FRAME) {
      // console.log("RTCCard: Full audio frame message received:", message); // Add this line to log the full message
      const audioFrame = message as unknown as IAudioFrame;
      // console.log(
      //   `[websocket] Received remote audio track ${audioFrame.buf.length} bytes`,
      // );
      setRemoteAudioData(audioFrame.buf);
      // console.log("RTCCard: remoteAudioData updated with length:", audioFrame.buf.length);
    }
  };

  const onTextChanged = (message: Message) => {
    // console.log(`[${new Date().toISOString()}] RTCCard: onTextChanged message:`, message);
    let chatType: EMessageType | undefined;
    let chatItem: IChatItem | undefined;

    if (message.type === MessageType.DATA) {
      const dataMessage = message as unknown as IDataMessage;
      if (dataMessage.content_type === "application/json") {
        const jsonMessage = dataMessage as unknown as IDataMessageJson;
        const payload = jsonMessage.json_payload;

        chatType = (payload.chat_role as EMessageType) || EMessageType.AGENT;

        chatItem = {
          userId: payload.user_id || "",
          text: payload.text || "",
          data_type: payload.data_type ? (payload.data_type as EMessageDataType) : EMessageDataType.OTHER,
          type: chatType,
          isFinal: payload.is_final,
          time: payload.time || Date.now(),
          userName: payload.user_name || "",
        };
      } else if (dataMessage.data) {
        const rawMessage = dataMessage as unknown as IDataMessageRaw;
        const textContent = new TextDecoder().decode(rawMessage.data);

        chatType = EMessageType.AGENT;
        chatItem = {
          userId: rawMessage.name || "",
          text: textContent,
          data_type: EMessageDataType.TEXT,
          type: chatType,
          isFinal: rawMessage.is_eof,
          time: rawMessage.timestamp || Date.now(),
          userName: rawMessage.name || "Agent",
        };
      } else {
        // console.warn(`[${new Date().toISOString()}] RTCCard: Received unexpected Data message content type: ${dataMessage.content_type || 'N/A'}`, dataMessage);
      }
    }
     else {
      // console.warn(`[${new Date().toISOString()}] RTCCard: Received unexpected message type for chat item: ${message.type || 'N/A'}`, message);
    }

    if (chatType && chatItem) {
      dispatch(addChatItem(chatItem));
    }
     else {
      // console.warn(`[${new Date().toISOString()}] RTCCard: Failed to determine chatType or chatItem for message. Message Type: ${message.type || 'N/A'}`, message);
    }
  };

  const onVoiceChange = (value: VoiceType) => {
    dispatch(setVoiceType(value));
  };

  return (
    <div className={cn("flex h-full flex-col min-h-0 bg-gray-50", className)}>
      {/* Scrollable top region (Avatar or ChatCard or Talkinghead) */}
      {useTrulienceAvatar ? (
        !avatarInLargeWindow ? (
          <div className="h-60 w-full p-1">
            <Avatar audioTrack={mediaStreamTrack || undefined} />
          </div>
        ) : (
          !isCompactLayout && (
            <DynamicChatCard className="m-0 w-full h-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg" />
          )
        )
      ) : (
        <div className="flex-1 min-h-[500px] z-10 relative bg-white rounded-lg shadow-lg border border-gray-200"> {/* Combined classes */}
          {/* TalkingHead 区域 */}
          <div className="h-full w-full">
            {showLive2D && (
              <div
                style={{ height: '100%', width: '100%' }}
                className="absolute inset-0"
              >
                {/* console.log("RTCCard: Passing audioTrack to TalkingHead. audioMute:", audioMute, "remoteAudioData length:", remoteAudioData?.length, "remoteAudioData content (first 20 bytes):"), remoteAudioData?.slice(0, 20) */}
                <TalkingheadBlock audioTrack={remoteAudioData} /> {/* Use remoteAudioData for TalkingHead */}
              </div>
            )}
            {/* Live2D Control Button */}
            <div className="absolute bottom-3 right-3 z-20">
              <Button
                variant="outline"
                className={cn("border-secondary bg-transparent", showLive2D ? "text-white" : "text-black")} // Dynamic text color
                onClick={() => setShowLive2D(!showLive2D)}
              >
                {showLive2D ? '隐藏 Live2D' : '显示 Live2D'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Bottom region for microphone and video blocks */}
      <div className="w-full space-y-2 px-2 py-2 bg-white rounded-lg shadow-sm border border-gray-200">
        {/* Microphone control area */}
        <Microphone onMuteChange={setAudioMute} isConnected={isConnected} sessionState={sessionState} defaultLocation={defaultLocation} onAudioDataCaptured={sendAudioFrame} /> {/* Pass sendAudioFrame */}

        {/* Audio Visualizer area */}
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
                maxBarHeight={16} // Added missing prop
                borderRadius={2}
                gap={3}
                track={audioMute ? undefined : mediaStreamTrack}
              />
            ) : micPermission === 'denied' ? (
              <div className="h-full flex items-center justify-center">
                <p className="text-xs text-center text-gray-500">麦克风权限被拒绝</p>
              </div>
            ) : audioMute ? (
              <div className="h-full flex items-center justify-center">
                <p className="text-xs text-center text-gray-500">麦克风已静音</p>
              </div>
            ) : (
              <div className="h-full flex items-center justify-center">
                <p className="text-xs text-center text-gray-500">请求麦克风权限中...</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
