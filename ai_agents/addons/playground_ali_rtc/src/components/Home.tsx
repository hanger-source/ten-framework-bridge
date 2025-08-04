import React, { Suspense } from "react";
import AuthInitializer from "@/components/authInitializer";
import { useAppSelector, EMobileActiveTab, useIsCompactLayout } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import Avatar from "@/components/Agent/AvatarTrulience";
import { IAliRtcUser, IAliUserTracks, aliRtcManager } from "@/manager";

// 使用 React.lazy 替代 next/dynamic
const DynamicRTCCard = React.lazy(() => import("@/components/Dynamic/RTCCard"));
const DynamicChatCard = React.lazy(() => import("@/components/Chat/ChatCard"));

export default function Home() {
  const mobileActiveTab = useAppSelector(
    (state) => state.global.mobileActiveTab
  );
  const trulienceSettings = useAppSelector((state) => state.global.trulienceSettings);

  const isCompactLayout = useIsCompactLayout();
  const useTrulienceAvatar = trulienceSettings.enabled;
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;
  const [remoteuser, setRemoteUser] = React.useState<IAliRtcUser>()

  React.useEffect(() => {
    // 确保只在客户端执行
    if (typeof window !== 'undefined' && aliRtcManager && typeof aliRtcManager.on === 'function') {
      aliRtcManager.on("remoteUserChanged", onRemoteUserChanged);
      return () => {
        aliRtcManager.off("remoteUserChanged", onRemoteUserChanged);
      };
    }
  }, []);

  const onRemoteUserChanged = (user: IAliRtcUser) => {
    if (useTrulienceAvatar) {
      user.audioTrack?.stop();
    }
    if (user.audioTrack) {
      setRemoteUser(user)
    }
  }

  return (
    <AuthInitializer>
      <div className="relative mx-auto flex flex-1 min-h-screen flex-col md:h-screen bg-gray-50">
        <Header className="h-[60px]" />
        <Action />
        <div className={cn(
          "mx-2 mb-2 flex h-full max-h-[calc(100vh-108px-24px)] flex-col md:flex-row md:gap-2 flex-1",
          {
            ["flex-col-reverse"]: avatarInLargeWindow && isCompactLayout
          }
        )}>
          <Suspense fallback={<div>Loading RTC...</div>}>
            <DynamicRTCCard
              className={cn(
                "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:w-[480px] md:rounded-lg flex-1 flex",
                {
                  ["hidden md:flex"]: mobileActiveTab === EMobileActiveTab.CHAT,
                }
              )}
            />
          </Suspense>

          {(!useTrulienceAvatar || isCompactLayout || !avatarInLargeWindow) && (
            <Suspense fallback={<div>Loading Chat...</div>}>
              <DynamicChatCard
                className={cn(
                  "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg flex-auto",
                  {
                    ["hidden md:flex"]: mobileActiveTab === EMobileActiveTab.AGENT,
                  }
                )}
              />
            </Suspense>
          )}

          {(useTrulienceAvatar && avatarInLargeWindow) && (
            <div className={cn(
              "w-full",
              {
                ["h-60 flex-auto p-1 bg-white rounded-lg shadow-lg border border-gray-200"]: isCompactLayout,
                ["hidden md:block"]: mobileActiveTab === EMobileActiveTab.CHAT,
              }
            )}>
              <Avatar audioTrack={remoteuser?.audioTrack} />
            </div>
          )}

        </div>
      </div>
    </AuthInitializer>
  );
}