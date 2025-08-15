import React from "react";
import { useAppSelector, EMobileActiveTab } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import ChatCard from "@/components/Chat/ChatCard";
// import ConnectionTest from "@/components/Chat/ConnectionTest";
// import AudioVisualizer from "@/components/Agent/AudioVisualizer"; // Removed
// import TalkingHead from "@/components/Agent/TalkingHead"; // Removed
// import MicrophoneDeviceSelect from "@/components/Agent/MicrophoneDeviceSelect"; // Removed
import { useWebSocketSession } from "@/hooks/useWebSocketSession";
// import { useMicrophoneStream } from "@/hooks/useMicrophoneStream"; // Removed
// import { useAgentSettings } from "@/hooks/useAgentSettings"; // Removed
import { performanceMonitor } from "@/common/utils";
// import { SessionConnectionState } from "@/types/websocket"; // Commented out import for SessionConnectionState
// import { useAudioRecorder } from "@/hooks/useAudioRecorder"; // Commented out import for useAudioRecorder
import AuthInitializer from "@/components/authInitializer"; // Add AuthInitializer import
// import { ConnectionTest } from "@/components/Chat/ConnectionTest"; // Changed from default import
import RTCCard from "@/components/Dynamic/RTCCard"; // Import RTCCard
import { RootState } from "@/store"; // Import RootState
import { WebSocketConnectionState } from "@/types/websocket"; // Import WebSocketConnectionState

function Home() {
  try {
    const mobileActiveTab = useAppSelector(
      (state) => state.global.mobileActiveTab,
    );
    const websocketConnectionState = useAppSelector((state: RootState) => state.global.websocketConnectionState); // Get from Redux
    // const [showLive2D, setShowLive2D] = React.useState(false); // Removed

    // const { isConnected, sessionState, defaultLocation } = useWebSocketSession(); // Removed
    // const { agentSettings } = useAgentSettings(); // Removed
    // const { mediaStreamTrack, micPermission, sendAudioFrame } = useMicrophoneStream({ isConnected, sessionState, defaultLocation, settings: agentSettings }); // Removed
    // const [audioMute, setAudioMute] = React.useState(true); // Removed

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
                <RTCCard className="flex-1" />
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