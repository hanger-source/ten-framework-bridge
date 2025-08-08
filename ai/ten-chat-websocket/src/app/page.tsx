"use client";
// 聪明的开发杭二: 本文件已由“聪明的开发杭二”修改，以移除冗余RTC相关代码。

import dynamic from "next/dynamic";

import AuthInitializer from "@/components/authInitializer";
import { useAppSelector, EMobileActiveTab, useIsCompactLayout } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import Avatar from "@/components/Agent/AvatarTrulience";
import React from "react";
import { useAppDispatch } from "@/common/hooks"; // 聪明的开发杭一: 修正useAppDispatch导入路径
import { setWebsocketConnectionState } from "@/store/reducers/global";
import { webSocketManager } from "@/manager/websocket/websocket";
import { WebSocketEvents } from "@/manager/websocket/types"; // 聪明的开发杭一: 导入WebSocketEvents

const DynamicRTCCard = dynamic(() => import("@/components/Dynamic/RTCCard"), {
  ssr: false,
});
const DynamicChatCard = dynamic(() => import("@/components/Chat/ChatCard"), {
  ssr: false,
});

export default function Home() {
  const mobileActiveTab = useAppSelector(
    (state) => state.global.mobileActiveTab,
  );
  const trulienceSettings = useAppSelector(
    (state) => state.global.trulienceSettings,
  );
  const dispatch = useAppDispatch();
  const websocketConnectionState = useAppSelector((state) => state.global.websocketConnectionState);

  const isCompactLayout = useIsCompactLayout();
  const useTrulienceAvatar = trulienceSettings.enabled;
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;

  return (
    <AuthInitializer>
      <div className="relative mx-auto flex flex-1 min-h-screen flex-col md:h-screen bg-gray-50">
        <Header className="h-[60px]" />
        <Action />
        <div
          className={cn(
            "mx-2 mb-2 flex h-full max-h-[calc(100vh-108px-24px)] flex-col md:flex-row md:gap-2 flex-1",
            {
              ["flex-col-reverse"]: avatarInLargeWindow && isCompactLayout,
            },
          )}
        >
          <DynamicRTCCard
            className={cn(
              "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:w-[480px] md:rounded-lg flex-1 flex",
              {
                ["hidden md:flex"]: mobileActiveTab === EMobileActiveTab.CHAT,
              },
            )}
            connectionState={websocketConnectionState}
          />

          {(!useTrulienceAvatar || isCompactLayout || !avatarInLargeWindow) && (
            <DynamicChatCard
              className={cn(
                "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg flex-auto",
                {
                  ["hidden md:flex"]:
                    mobileActiveTab === EMobileActiveTab.AGENT,
                },
              )}
            />
          )}

          {useTrulienceAvatar && avatarInLargeWindow && (
            <div
              className={cn("w-full", {
                ["h-60 flex-auto p-1 bg-white rounded-lg shadow-lg border border-gray-200"]:
                  isCompactLayout,
                ["hidden md:block"]: mobileActiveTab === EMobileActiveTab.CHAT,
              })}
            >
              <Avatar audioTrack={undefined} />{' '}{/* 聪明的开发杭一: 修正audioTrack属性为undefined */}
            </div>
          )}
        </div>
      </div>
    </AuthInitializer>
  );
}
