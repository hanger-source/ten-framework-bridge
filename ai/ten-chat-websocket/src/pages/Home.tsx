import React from "react";
import { useAppSelector, EMobileActiveTab } from "@/common";
import Header from "@/components/Layout/Header";
import Action from "@/components/Layout/Action";
import { cn } from "@/lib/utils";
import MicrophoneBlock from "@/components/Agent/Microphone";
import { Button } from "@/components/ui/button";
import { MicIconByStatus } from "@/components/Icon";
import ChatCard from "@/components/Chat/ChatCard";
import ConnectionTest from "@/components/Chat/ConnectionTest";
import AudioVisualizer from "@/components/Agent/AudioVisualizer";
import TalkingHead from "@/components/Agent/TalkingHead";
import MicrophoneDeviceSelect from "@/components/Agent/MicrophoneDeviceSelect";
import { useWebSocketSession } from "@/hooks/useWebSocketSession";
import { useMicrophoneStream } from "@/hooks/useMicrophoneStream";
import { performanceMonitor } from "@/common/utils";
import { SessionConnectionState } from "@/types/websocket";
import { useAudioRecorder } from "@/hooks/useAudioRecorder"; // Import useAudioRecorder

function Home() {
  try {
    const mobileActiveTab = useAppSelector(
      (state) => state.global.mobileActiveTab,
    );
    const [showLive2D, setShowLive2D] = React.useState(false);

    const { isConnected, sessionState, defaultLocation, startSession } = useWebSocketSession();
    const { mediaStreamTrack, micPermission, sendAudioFrame } = useMicrophoneStream({ isConnected, sessionState, defaultLocation });
    const [audioMute, setAudioMute] = React.useState(false); // Managed by MicrophoneBlock now

    const { recordedChunksCount, onAudioDataCaptured, downloadRecordedAudio } = useAudioRecorder(); // Use the audio recorder hook

    const getSessionStateText = () => {
      if (!isConnected) {
        return "WebSocket 未连接";
      }
      switch (sessionState) {
        case SessionConnectionState.IDLE:
          return "会话准备就绪";
        case SessionConnectionState.CONNECTING_SESSION:
          return "正在连接会话...";
        case SessionConnectionState.SESSION_ACTIVE:
          return "会话已激活";
        default:
          return "未知状态";
      }
    };

    // 根据静音状态决定是否传递 track
    // const activeTrack = audioMute ? undefined : (mediaStreamTrack || undefined);
    // console.log('Home component: activeTrack', activeTrack);

    console.log('Home component render: isConnected =', isConnected, ', sessionState =', sessionState);
    return (
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
                  {/* 会话状态显示 */}
                  <div className="flex items-center justify-between">
                    <div className="text-sm font-medium">会话状态:</div>
                    <div className={`px-2 py-1 rounded text-xs ${
                      sessionState === SessionConnectionState.SESSION_ACTIVE
                        ? 'bg-green-100 text-green-800'
                        : 'bg-red-100 text-red-800'
                    }`}>
                      {getSessionStateText()}
                    </div>
                    <Button
                      onClick={startSession}
                      disabled={!isConnected || sessionState !== SessionConnectionState.IDLE}
                      size="sm"
                    >
                      开始会话
                    </Button>
                  </div>

                  {/* 麦克风控制 - 一行显示所有元素 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="text-sm font-medium">麦克风</div>
                      <MicrophoneDeviceSelect />
                    </div>
                    {/* Remove this button as MicrophoneBlock handles its own mute control now */}
                    {/*
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        className="border-secondary bg-transparent"
                        onClick={() => setAudioMute(!audioMute)}
                      >
                        <MicIconByStatus className="h-5 w-5" active={!audioMute} />
                      </Button>
                    </div>
                    */}
                    {/* Download Recorded Audio Button */}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={downloadRecordedAudio}
                      disabled={recordedChunksCount === 0}
                    >
                      下载录音 ({recordedChunksCount})
                    </Button>
                  </div>
                  {/* Move MicrophoneBlock here to prevent overlap */}
                  <MicrophoneBlock sendAudioFrame={sendAudioFrame} onMuteChange={setAudioMute} isConnected={isConnected} sessionState={sessionState} onAudioDataCaptured={onAudioDataCaptured} />

                  {/* 音频可视化区域 */}
                  <div>
                    <div className="text-sm font-medium text-gray-700 mb-2">
                      音频可视化 {audioMute ? '(已静音)' : '(录音中)'}
                    </div>
                    <div className="flex h-10 flex-col items-center justify-center gap-2 self-stretch rounded-md border border-gray-200 bg-gray-50 p-2">
                      {/* {console.log('AudioVisualizer render condition:', micPermission === 'granted' && !audioMute)} */}
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
                        <div className="text-center text-gray-500">
                          <p className="text-xs">麦克风权限被拒绝</p>
                        </div>
                      ) : audioMute ? (
                        <div className="text-center text-gray-500">
                          <p className="text-xs">麦克风已静音</p>
                        </div>
                      ) : (
                        <div className="text-center text-gray-500">
                          <p className="text-xs">请求麦克风权限中...</p>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* 音频质量信息 */}
                  {micPermission === 'granted' && !audioMute && mediaStreamTrack && (
                    <div className="mt-1">
                      <div className="text-xs text-gray-500">
                        <p>音频设置: 48kHz, 单声道, 优化模式</p>
                        <p>FFT 大小: 2048, 更新频率: 62.5Hz</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* 聊天区域 */}
          <div className="m-0 w-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg md:flex-1">
            <div className="h-full flex flex-col">
              <div className="p-4 border-b">
                <ConnectionTest />
              </div>
              <div className="flex-1">
                <ChatCard />
              </div>
            </div>
          </div>
        </div>
      </div>
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