"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { aliRtcManager, IAliUserTracks, IAliRtcUser } from "@/manager";
import { CameraVideoTrack, MicrophoneAudioTrack, LocalVideoTrack } from "dingrtc";
import { useAppSelector, useAppDispatch, VOICE_OPTIONS, VideoSourceType, useIsCompactLayout } from "@/common"
import { useAgentSettings } from "@/hooks/useAgentSettings"
import { ITextItem, EMessageType, IChatItem } from "@/types"
import {
  setRoomConnected,
  addChatItem,
  setVoiceType,
  setOptions,
} from "@/store/reducers/global"
import AgentVoicePresetSelect from "@/components/Agent/VoicePresetSelect"
import AgentView from "@/components/Agent/View"
import Avatar from "@/components/Agent/AvatarTrulience"
import MicrophoneBlock from "@/components/Agent/Microphone"
import VideoBlock from "@/components/Agent/Camera"
import ChatCard from "@/components/Chat/ChatCard"
import TalkingheadBlock from "@/components/Agent/TalkingHead"

let hasInit: boolean = false

export default function RTCCard(props: { className?: string }) {
  const { className } = props

  const dispatch = useAppDispatch()
  const options = useAppSelector((state) => state.global.options)
  const trulienceSettings = useAppSelector((state) => state.global.trulienceSettings)
  const { userId, channel } = options
  const [videoTrack, setVideoTrack] = React.useState<CameraVideoTrack>()
  const [audioTrack, setAudioTrack] = React.useState<MicrophoneAudioTrack>()
  const [screenTrack, setScreenTrack] = React.useState<LocalVideoTrack>()
  const [remoteuser, setRemoteUser] = React.useState<IAliRtcUser>()
  const [videoSourceType, setVideoSourceType] = React.useState<VideoSourceType>(VideoSourceType.CAMERA)
  const [isConnected, setIsConnected] = React.useState<boolean>(false)
  const [isConnecting, setIsConnecting] = React.useState<boolean>(false)
  const useTrulienceAvatar = trulienceSettings.enabled
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;

  const isCompactLayout = useIsCompactLayout();
  const { agentSettings } = useAgentSettings();

  React.useEffect(() => {
    if (!options.channel) {
      return
    }
    // 移除自动初始化，改为手动控制

        // 在组件挂载时创建音轨，这样音轨图就能正常工作
    const initAudioTrack = async () => {
      try {
        console.log("[rtc] Initializing audio track on component mount")

        // 先设置事件监听器，确保不会错过事件
        aliRtcManager.on("localTracksChanged", onLocalTracksChanged)

        await aliRtcManager.createMicrophoneAudioTrack()
      } catch (error) {
        console.error("[rtc] Failed to create audio track on mount:", error)
      }
    }

    initAudioTrack()

    // 清理函数
    return () => {
      aliRtcManager.off("localTracksChanged", onLocalTracksChanged)
    }
  }, [options.channel])

  const init = async () => {
    if (isConnecting || isConnected) {
      return
    }

    console.log("[rtc] init")
    setIsConnecting(true)

    try {
      aliRtcManager.on("localTracksChanged", onLocalTracksChanged)
      aliRtcManager.on("textChanged", onTextChanged)
      aliRtcManager.on("remoteUserChanged", onRemoteUserChanged)
      await aliRtcManager.createCameraTracks()
      await aliRtcManager.createMicrophoneAudioTrack()
      await aliRtcManager.join({
        channel,
        userId,
        agentSettings,
      })
      dispatch(
        setOptions({
          ...options,
          appId: aliRtcManager.appId ?? "",
          token: aliRtcManager.token ?? "",
        }),
      )
      await aliRtcManager.publish()
      dispatch(setRoomConnected(true))
      setIsConnected(true)
      hasInit = true
      console.log("[rtc] 成功加入房间")
    } catch (error) {
      console.error("[rtc] 加入房间失败:", error)
      setIsConnected(false)
      hasInit = false
    } finally {
      setIsConnecting(false)
    }
  }

  const destory = async () => {
    if (!isConnected) {
      return
    }

    console.log("[rtc] destory")
    try {
      aliRtcManager.off("textChanged", onTextChanged)
      aliRtcManager.off("localTracksChanged", onLocalTracksChanged)
      aliRtcManager.off("remoteUserChanged", onRemoteUserChanged)
      await aliRtcManager.destroy()
      dispatch(setRoomConnected(false))
      setIsConnected(false)
      hasInit = false
      console.log("[rtc] 成功退出房间")
    } catch (error) {
      console.error("[rtc] 退出房间失败:", error)
    }
  }

  const onRemoteUserChanged = (user: IAliRtcUser) => {
    console.log("[rtc] onRemoteUserChanged", user)
    if (useTrulienceAvatar) {
      // trulience SDK will play audio in synch with mouth
      user.audioTrack?.stop();
    }
    if (user.audioTrack) {
      setRemoteUser(user)
    }
  }

  const onLocalTracksChanged = (tracks: IAliUserTracks) => {
    console.log("[rtc] onLocalTracksChanged", tracks)
    const { videoTrack, audioTrack, screenTrack } = tracks
    setVideoTrack(videoTrack)
    setScreenTrack(screenTrack)
    if (audioTrack) {
      setAudioTrack(audioTrack)
    }
  }

  const onTextChanged = (text: IChatItem) => {
    console.log("[rtc] onTextChanged", text)
    dispatch(
      addChatItem(text),
    )
  }

  const onVoiceChange = (value: "male" | "female") => {
    dispatch(setVoiceType(value))
  }

  const onVideoSourceTypeChange = async (value: VideoSourceType) => {
    await aliRtcManager.switchVideoSource(value)
    setVideoSourceType(value)
  }

  return (
    <div className={cn("flex h-full flex-col min-h-0 bg-gray-50", className)}>
      {/* 房间控制按钮 */}
      <div className="w-full px-2 py-2 bg-white rounded-lg shadow-sm border border-gray-200 mb-2">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <span className="text-sm font-medium text-gray-700">房间状态:</span>
            <span className={`px-2 py-1 text-xs rounded-full ${
              isConnected
                ? 'bg-green-100 text-green-800'
                : isConnecting
                ? 'bg-yellow-100 text-yellow-800'
                : 'bg-gray-100 text-gray-800'
            }`}>
              {isConnected ? '已连接' : isConnecting ? '连接中...' : '未连接'}
            </span>
          </div>

          <div className="flex items-center space-x-2">
            {!isConnected && !isConnecting ? (
              <button
                onClick={init}
                disabled={!channel || isConnecting}
                className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed text-sm font-medium"
              >
                加入房间
              </button>
            ) : (
              <button
                onClick={destory}
                disabled={isConnecting}
                className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 disabled:bg-gray-300 disabled:cursor-not-allowed text-sm font-medium"
              >
                退出房间
              </button>
            )}
          </div>
        </div>

        {channel && (
          <div className="mt-2 text-xs text-gray-500">
            频道: {channel} | 用户ID: {userId}
          </div>
        )}
      </div>

      {/* Scrollable top region (Avatar or ChatCard or Talkinghead) */}
      <div className="flex-1 min-h-0 z-10">
        {useTrulienceAvatar ? (
          !avatarInLargeWindow ? (
            <div className="h-60 w-full p-1">
              <Avatar localAudioTrack={audioTrack} audioTrack={remoteuser?.audioTrack} />
            </div>
          ) : (
            !isCompactLayout &&
            <ChatCard
              className="m-0 w-full h-full rounded-b-lg bg-white shadow-lg border border-gray-200 md:rounded-lg"
            />
          )
        ) : (
          // <AgentView  audioTrack={remoteuser?.audioTrack} />
          <div style={{ height: 700, minHeight: 500 }} className="bg-white rounded-lg shadow-lg border border-gray-200">
            <TalkingheadBlock audioTrack={remoteuser?.audioTrack} />
          </div>
        )}
      </div>

      {/* Bottom region for microphone and video blocks */}
      <div className="w-full space-y-2 px-2 py-2 bg-white rounded-lg shadow-sm border border-gray-200">
        <MicrophoneBlock audioTrack={audioTrack} />
        <VideoBlock
          cameraTrack={videoTrack}
          screenTrack={screenTrack}
          videoSourceType={videoSourceType}
          onVideoSourceChange={onVideoSourceTypeChange}
        />
      </div>
    </div>
  );
}
