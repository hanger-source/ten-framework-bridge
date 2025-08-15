import React from "react";
import { useAppSelector, EMobileActiveTab } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import { Microphone } from "@/components/Agent/Microphone";
import { Button } from "@/components/ui/button";
import { MicIconByStatus } from "@/components/Icon";
import ChatCard from "@/components/Chat/ChatCard";
// import ConnectionTest from "@/components/Chat/ConnectionTest";
import AudioVisualizer from "@/components/Agent/AudioVisualizer";
import TalkingHead from "@/components/Agent/TalkingHead";
// import MicrophoneDeviceSelect from "@/components/Agent/MicrophoneDeviceSelect";
import { useWebSocketSession } from "@/hooks/useWebSocketSession";
import { useMicrophoneStream } from "@/hooks/useMicrophoneStream";
import { useAgentSettings } from "@/hooks/useAgentSettings"; // Import useAgentSettings
import { performanceMonitor } from "@/common/utils";
// import { SessionConnectionState } from "@/types/websocket"; // Commented out import for SessionConnectionState
// import { useAudioRecorder } from "@/hooks/useAudioRecorder"; // Commented out import for useAudioRecorder
import AuthInitializer from "@/components/authInitializer"; // Add AuthInitializer import
// import { ConnectionTest } from "@/components/Chat/ConnectionTest"; // Changed from default import

function Home() {
  try {
    const mobileActiveTab = useAppSelector(
      (state) => state.global.mobileActiveTab,
    );
    const [showLive2D, setShowLive2D] = React.useState(false);

    const { isConnected, sessionState, defaultLocation } = useWebSocketSession();
    const { agentSettings } = useAgentSettings(); // Get agent settings
    const { mediaStreamTrack, micPermission, sendAudioFrame } = useMicrophoneStream({ isConnected, sessionState, defaultLocation, settings: agentSettings }); // Pass settings
    const [audioMute, setAudioMute] = React.useState(true); // Managed by MicrophoneBlock now

    // const { recordedChunksCount, onAudioDataCaptured, downloadRecordedAudio } = useAudioRecorder(); // Commented out useAudioRecorder hook
    // Removed recordedChunksCount, onAudioDataCaptured, downloadRecordedAudio from usage below
    const recordedChunksCount = 0; // Dummy value
    const onAudioDataCaptured = (audioData: Uint8Array) => {}; // Dummy function
    const downloadRecordedAudio = () => {}; // Dummy function

    // Removed getSessionStateText function and related logging
    // const activeTrack = audioMute ? undefined : (mediaStreamTrack || undefined);
    // console.log('Home component: activeTrack', activeTrack);

    // console.log('Home component render: isConnected =', isConnected, ', sessionState =', sessionState); // Removed
    return (
      <AuthInitializer>
        <div className="relative mx-auto flex flex-1 min-h-screen flex-col md:h-screen bg-gray-50">
          <Header className="h-[60px]" />
          <Action />
          <div className="mx-2 mb-2 flex flex-col md:flex-row md:gap-2 flex-1">
            {/* RTC 区域 - 使用固定宽度，移除 flex-1 限制 */}
            <div className={cn(
              "m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:w-[400px] md:rounded-lg",
              {
                ["hidden md:block"]: mobileActiveTab === EMobileActiveTab.CHAT,
              },
            )}>
              <div className="flex h-full flex-col min-h-0 bg-gray-50 w-full">
                {/* TalkingHead 区域 - 占据大部分空间 */}
                <div className="relative flex-1 min-h-[500px] z-10 bg-white rounded-lg shadow-lg border border-gray-200">
                  {showLive2D && (
                    <div
                      style={{ height: '100%', width: '100%' }}
                      className="absolute inset-0"
                    >
                      <TalkingHead audioTrack={audioMute ? undefined : undefined} />
                    </div>
                  )}
                  {/* Live2D Control Button - fixed to bottom right of this container */}
                  <div className="absolute bottom-3 right-3 z-20">
                    <Button
                      variant="outline"
                      className="border-secondary bg-transparent"
                      onClick={() => setShowLive2D(!showLive2D)}
                    >
                      {showLive2D ? '隐藏 Live2D' : '显示 Live2D'}
                    </Button>
                  </div>
                </div>

                {/* 麦克风控制区域 - 放在 TalkingHead 下面，固定高度 */}
                <div className="mt-2 p-3 bg-white rounded-lg shadow-sm border border-gray-200">
                  <div className="space-y-3">
                    {/* 会话状态显示 - Removed Entire Block */}
                    <Microphone onMuteChange={setAudioMute} isConnected={isConnected} sessionState={sessionState} defaultLocation={defaultLocation} onAudioDataCaptured={onAudioDataCaptured} />

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
                            // frequencies={[]} // Removed subscribedVolumes
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
              </div>
            </div>

            {/* 聊天区域 */}
            <div className="m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg md:flex-1">
              <div className="h-full flex flex-col">
                <div className="p-4 border-b">
                  {/* <ConnectionTest /> */}
                </div>
                <div className="flex-1">
                  <ChatCard />
                </div>
              </div>
            </div>
          </div>
        </div>
      </AuthInitializer>
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