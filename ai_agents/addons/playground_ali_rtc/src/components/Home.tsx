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
          <Suspense fallback={
            <div className={cn(
              "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:w-[480px] md:rounded-lg flex-1 flex flex-col",
              {
                ["hidden md:flex"]: mobileActiveTab === EMobileActiveTab.CHAT,
              }
            )}>
              {/* RTC 组件骨架屏 */}
              <div className="p-2">
                {/* 频道控制区域骨架 */}
                <div className="w-full px-2 py-2 bg-gray-100 rounded-lg mb-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <div className="w-20 h-4 bg-gray-200 rounded animate-pulse"></div>
                      <div className="w-16 h-6 bg-gray-200 rounded-full animate-pulse"></div>
                    </div>
                    <div className="w-20 h-8 bg-gray-200 rounded-lg animate-pulse"></div>
                  </div>
                  <div className="mt-2 w-48 h-3 bg-gray-200 rounded animate-pulse"></div>
                </div>

                {/* 主要内容区域骨架 */}
                <div className="flex-1 bg-gray-100 rounded-lg p-4">
                  <div className="flex items-center justify-center h-full">
                    <div className="text-center">
                      <div className="w-16 h-16 bg-gray-200 rounded-full mx-auto mb-4 animate-pulse"></div>
                      <div className="w-32 h-4 bg-gray-200 rounded mx-auto mb-2 animate-pulse"></div>
                      <div className="w-24 h-3 bg-gray-200 rounded mx-auto animate-pulse"></div>
                    </div>
                  </div>
                </div>

                {/* 底部控制区域骨架 */}
                <div className="mt-2 p-2 bg-gray-100 rounded-lg">
                  <div className="space-y-2">
                    <div className="w-full h-12 bg-gray-200 rounded animate-pulse"></div>
                    <div className="w-full h-12 bg-gray-200 rounded animate-pulse"></div>
                  </div>
                </div>
              </div>
            </div>
          }>
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
            <Suspense fallback={
              <div className={cn(
                "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg flex-auto flex flex-col",
                {
                  ["hidden md:flex"]: mobileActiveTab === EMobileActiveTab.AGENT,
                }
              )}>
                {/* Chat 组件骨架屏 */}
                <div className="p-4 h-full flex flex-col">
                  {/* 头部配置区域骨架 */}
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center space-x-2">
                      <div className="w-24 h-8 bg-gray-200 rounded animate-pulse"></div>
                      <div className="w-20 h-8 bg-gray-200 rounded animate-pulse"></div>
                      <div className="w-16 h-8 bg-gray-200 rounded animate-pulse"></div>
                    </div>
                    <div className="w-8 h-8 bg-gray-200 rounded animate-pulse"></div>
                  </div>

                  {/* 消息列表区域骨架 */}
                  <div className="flex-1 bg-gray-100 rounded-lg p-4 mb-4">
                    <div className="space-y-4">
                      {/* 模拟消息气泡 */}
                      <div className="flex justify-start">
                        <div className="w-64 h-12 bg-gray-200 rounded-lg animate-pulse"></div>
                      </div>
                      <div className="flex justify-end">
                        <div className="w-48 h-10 bg-gray-200 rounded-lg animate-pulse"></div>
                      </div>
                      <div className="flex justify-start">
                        <div className="w-56 h-16 bg-gray-200 rounded-lg animate-pulse"></div>
                      </div>
                      <div className="flex justify-end">
                        <div className="w-40 h-10 bg-gray-200 rounded-lg animate-pulse"></div>
                      </div>
                    </div>
                  </div>

                  {/* 输入区域骨架 */}
                  <div className="flex items-center space-x-2">
                    <div className="flex-1 h-10 bg-gray-200 rounded-lg animate-pulse"></div>
                    <div className="w-10 h-10 bg-gray-200 rounded-lg animate-pulse"></div>
                  </div>
                </div>
              </div>
            }>
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