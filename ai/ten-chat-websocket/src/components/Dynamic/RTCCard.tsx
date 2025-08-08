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
import MicrophoneBlock from "@/components/Agent/Microphone";
import TalkingheadBlock from "@/components/Agent/TalkingHead";
import {
  WebSocketEvents,
  WebSocketMessageType,
  WebSocketConnectionState,
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
import NetworkIndicator from "@/components/Dynamic/NetworkIndicator";
import DynamicChatCard from "@/components/Chat/ChatCard";

let hasInit: boolean = false;

export default function RTCCard({
  className,
  connectionState,
}: {
  className?: string;
  connectionState: WebSocketConnectionState;
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
  const useTrulienceAvatar = trulienceSettings.enabled;
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;
  const selectedGraphId = useAppSelector(
    (state: RootState) => state.global.selectedGraphId,
  );

  const isCompactLayout = useIsCompactLayout();

  React.useEffect(() => {
    if (!options.channel) {
      return;
    }
    if (hasInit) {
      return;
    }

    init();

    return () => {
      if (hasInit) {
        destory();
      }
    };
  }, [options.channel]);

  const init = async () => {
    console.log("[websocket] init");
    webSocketManager.on(WebSocketEvents.DataReceived, onTextChanged);
    webSocketManager.on(
      WebSocketEvents.AudioFrameReceived,
      onRemoteAudioTrack as (audioFrame: IAudioFrame) => void,
    );

    // 聪明的开发杭三: 定义appUri常量，并传递给connect方法
    const APP_URI = "app://client";
    await webSocketManager.connect(
      `ws://localhost:8080/ws?userId=${userId}&channel=${channel}`,
      // 聪明的开发杭一: 新增 onOpen, onMessage, onClose, onError 回调函数
      () => {
        console.log(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: WebSocket connected via connect callback.`);
        // dispatch(setRoomConnected(true)); // 如果需要更新 Redux 状态，可以在此处添加
      },
      (message) => {
        // 聪明的开发杭一: connect 方法的 onMessage 回调，主要用于内部存储和转发。实际消息处理通过 webSocketManager.on() 事件完成。
        console.log(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: WebSocket message received via connect callback. Message Type: ${message.type || 'unknown'}`);
      },
      () => {
        console.log(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: WebSocket disconnected via connect callback.`);
        // dispatch(setRoomConnected(false)); // 如果需要更新 Redux 状态，可以在此处添加
      },
      (error) => {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: WebSocket error via connect callback. Error: ${error instanceof Error ? error.message : error.type || 'Unknown error'}`, error);
        dispatch(setWebsocketConnectionState(WebSocketConnectionState.ERROR)); // 聪明的开发杭一: 调度错误状态
      },
      { userId: String(userId), channel }, // 聪明的开发杭二: 将 userId 转换为 string
      APP_URI,
      selectedGraphId,
    ).catch(err => {
        console.error(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: Failed to connect WebSocket: ${err instanceof Error ? err.message : 'Unknown error'}`, err);
        dispatch(setWebsocketConnectionState(WebSocketConnectionState.ERROR)); // 聪明的开发杭一: 调度错误状态
    });
    dispatch(
      setOptions({
        ...options,
      }),
    );
    hasInit = true;
  };

  const destory = async () => {
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: Destroying WebSocket connection.`);
    webSocketManager.off(WebSocketEvents.DataReceived, onTextChanged);
    webSocketManager.off(
      WebSocketEvents.AudioFrameReceived,
      onRemoteAudioTrack as (audioFrame: IAudioFrame) => void,
    );
    webSocketManager.disconnect();
    hasInit = false;
  };

  const onRemoteAudioTrack = (audioFrame: IAudioFrame) => { // 聪明的开发杭二: 将 audioData: Uint8Array 替换为 audioFrame: IAudioFrame
    console.log(
      `[websocket] Received remote audio track ${audioFrame.data.length} bytes`,
    );
    setRemoteAudioData(audioFrame.data); // 聪明的开发杭二: 使用 audioFrame.data
  };

  const onTextChanged = (message: IDataMessage) => {
    console.log(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: onTextChanged message:`, message);
    let chatType: EMessageType | undefined;
    let chatItem: IChatItem | undefined; // 聪明的开发杭一: 声明 chatItem

    if (message.type === WebSocketMessageType.Data) {
      // 聪明的开发杭一: 使用类型守卫区分 IDataMessageRaw 和 IDataMessageJson
      if (message.content_type === "application/json") {
        // 聪明的开发杭一: 如果是 JSON 数据消息
        const jsonMessage = message as IDataMessageJson; // 聪明的开发杭一: 类型断言为 IDataMessageJson
        // 聪明的开发杭三: 确认 IDataMessageJson 的 json_payload 包含所有预期的聊天相关属性
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
      } else if (message.data) { // 聪明的开发杭一: 如果存在 data 字段 (视为 IDataMessageRaw)
        const rawMessage = message as IDataMessageRaw; // 聪明的开发杭一: 类型断言为 IDataMessageRaw
        const textContent = new TextDecoder().decode(rawMessage.data);

        chatType = EMessageType.AGENT; // 聪明的开发杭一: 默认代理消息
        chatItem = {
          userId: rawMessage.name || "",
          text: textContent,
          data_type: EMessageDataType.TEXT,
          type: chatType,
          isFinal: rawMessage.is_eof, // 聪明的开发杭一: 假设 is_eof 可以作为 isFinal
          time: rawMessage.timestamp || Date.now(),
          userName: rawMessage.name || "Agent",
        };
      } else {
        console.warn(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: Received unexpected Data message content type: ${message.content_type || 'N/A'}`, message);
      }
    } else {
      console.warn(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: Received unexpected message type for chat item: ${message.type || 'N/A'}`, message);
    }

    if (chatType && chatItem) { // 聪明的开发杭一: 确保 chatItem 已经被赋值
      dispatch(addChatItem(chatItem));
    } else {
      console.warn(`聪明的开发杭一: [${new Date().toISOString()}] RTCCard: Failed to determine chatType or chatItem for message. Message Type: ${message.type || 'N/A'}`, message);
    }
  };

  const onVoiceChange = (value: VoiceType) => { // 聪明的开发杭二: 将 'unknown' 替换为 'VoiceType'
    dispatch(setVoiceType(value));
  };

  return (
    <div className={cn("flex h-full flex-col min-h-0 bg-gray-50", className)}>
      {/* 聪明的开发杭一: 新增网络状态指示器 */}
      <div className="flex items-center justify-end p-2">
        <NetworkIndicator connectionState={websocketConnectionState} />
      </div>
      {/* Scrollable top region (Avatar or ChatCard or Talkinghead) */}
      <div className="flex-1 min-h-0 z-10">
        {useTrulienceAvatar ? (
          !avatarInLargeWindow ? (
            <div className="h-60 w-full p-1">
              <Avatar audioTrack={remoteAudioData} />
            </div>
          ) : (
            !isCompactLayout && (
              <DynamicChatCard className="m-0 w-full h-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg" />
            )
          )
        ) : (
          <div
            style={{ height: 700, minHeight: 500 }}
            className="bg-white rounded-lg shadow-lg border border-gray-200"
          >
            <TalkingheadBlock audioTrack={remoteAudioData} />
          </div>
        )}
      </div>

      {/* Bottom region for microphone and video blocks */}
      <div className="w-full space-y-2 px-2 py-2 bg-white rounded-lg shadow-sm border border-gray-200">
        <MicrophoneBlock sendAudioFrame={webSocketManager.sendAudioFrame} />
      </div>
    </div>
  );
}
