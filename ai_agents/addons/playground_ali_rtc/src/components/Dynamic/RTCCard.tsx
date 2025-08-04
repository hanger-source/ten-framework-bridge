"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { aliRtcManager, IAliUserTracks, IAliRtcUser } from "@/manager";
import { CameraVideoTrack, MicrophoneAudioTrack, LocalVideoTrack, RemoteAudioTrack, RemoteVideoTrack } from "dingrtc";
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
import { RemoteStreamPlayer } from "@/components/Agent/StreamPlayer"
import { AudioVisualizerWrapper } from "@/components/Agent/AudioVisualizer"
import { useRTCUsers } from "@/hooks/useRTCUsers"

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
  const [videoSourceType, setVideoSourceType] = React.useState<VideoSourceType>(VideoSourceType.CAMERA)
  const [isConnecting, setIsConnecting] = React.useState<boolean>(false)
    const [audioTrackCreated, setAudioTrackCreated] = React.useState<boolean>(false)
  const useTrulienceAvatar = trulienceSettings.enabled
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;

  // 使用 RTC 用户管理 Hook
  const {
    remoteUsers,
    currentRemoteUser: remoteuser,
    isConnected,
    setupEventListeners,
    cleanupEventListeners,
    clearUsers
  } = useRTCUsers(useTrulienceAvatar)

  const isCompactLayout = useIsCompactLayout();
  const { agentSettings } = useAgentSettings();

  React.useEffect(() => {
    // 在组件挂载时创建音轨和摄像头轨道，这样音轨图和预览框就能正常工作
    const initTracks = async () => {
      try {
        // 先设置事件监听器，确保不会错过事件
        aliRtcManager.on("localTracksChanged", onLocalTracksChanged)

        // 检查是否已有音频轨道
        const localTracks = aliRtcManager.getLocalTracks();
        if (!localTracks.audioTrack) {
          await aliRtcManager.createMicrophoneAudioTrack()
        }
        setAudioTrackCreated(true)

        // 创建摄像头轨道，让预览框立即显示
        await aliRtcManager.createCameraTracks()
      } catch (error) {
        console.error("[rtc] Failed to create tracks on mount:", error)
      }
    }

    initTracks()

    // 清理函数
    return () => {
      aliRtcManager.off("localTracksChanged", onLocalTracksChanged)
    }
  }, []) // 移除 options.channel 依赖，确保轨道始终创建

  // 设置 RTC 用户事件监听器
  React.useEffect(() => {
    setupEventListeners()
    return () => {
      cleanupEventListeners()
    }
  }, [setupEventListeners, cleanupEventListeners])

  const init = async () => {
    if (isConnecting || isConnected) {
      return
    }

    console.log("[rtc] init")
    setIsConnecting(true)

    try {
      aliRtcManager.on("localTracksChanged", onLocalTracksChanged)

      // 检查是否已有音频轨道，如果没有则创建
      const localTracks = aliRtcManager.getLocalTracks();
      if (!localTracks.audioTrack) {
        await aliRtcManager.createMicrophoneAudioTrack()
        setAudioTrackCreated(true)
      } else {
        setAudioTrackCreated(true)
      }

      // 摄像头轨道已经在组件挂载时创建，这里不需要重复创建
      // await aliRtcManager.createCameraTracks()

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
          userName: `user_${userId}`,
        }),
      )

      await aliRtcManager.publish()

      dispatch(setRoomConnected(true))
      hasInit = true
      console.log("[rtc] 成功加入频道")
    } catch (error) {
      console.error("[rtc] 加入频道失败:", error)
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
      aliRtcManager.off("localTracksChanged", onLocalTracksChanged)

      // 清理组件状态，但保持音频轨道用于音轨图
      setVideoTrack(undefined)
      setScreenTrack(undefined)
      clearUsers() // 使用 Hook 的清理函数

      await aliRtcManager.destroy()
      dispatch(setRoomConnected(false))
      hasInit = false
      console.log("[rtc] 成功退出频道")
    } catch (error) {
      console.error("[rtc] 退出频道失败:", error)
    }
  }



  const onLocalTracksChanged = (tracks: IAliUserTracks) => {
    const { videoTrack, audioTrack, screenTrack } = tracks
    console.log("[rtc] onLocalTracksChanged", {
      hasVideoTrack: !!videoTrack,
      hasAudioTrack: !!audioTrack,
      hasScreenTrack: !!screenTrack
    })
    setVideoTrack(videoTrack)
    setScreenTrack(screenTrack)
    if (audioTrack) {
      setAudioTrack(audioTrack)
      setAudioTrackCreated(true)
    }
    // 不清除音频轨道，保持音轨图工作
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
      {/* 频道控制按钮 */}
      <div className="w-full px-2 py-2 bg-white rounded-lg shadow-sm border border-gray-200 mb-2">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <span className="text-sm font-medium text-gray-700">频道状态:</span>
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
                加入
              </button>
            ) : (
              <button
                onClick={destory}
                disabled={isConnecting}
                className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 disabled:bg-gray-300 disabled:cursor-not-allowed text-sm font-medium"
              >
                离开
              </button>
            )}
          </div>
        </div>

        <div className="mt-2 text-xs text-gray-500">
          {channel ? (
            <>
              频道: {channel} | 音频轨道: {audioTrackCreated ? '已创建' : '未创建'}
              <br />
              用户: {options.userName || 'Unknown'}({userId})
              {remoteuser && (
                <>
                  <br />
                  远程用户: {remoteuser.userName || 'Unknown'}({remoteuser.userId}) |
                  视频: {remoteuser.videoTrack && remoteuser.videoTrack.isPlaying ? '有' : '无'} |
                  音频: {remoteuser.audioTrack && remoteuser.audioTrack.isPlaying ? '有' : '无'}
                </>
              )}
              {/* 支持显示多个远程用户 */}
              {remoteUsers && remoteUsers.length > 0 && (
                <>
                  <br />
                  远程用户列表 ({remoteUsers.length}):
                  {remoteUsers.map((user, index) => (
                    <div key={user.userId || index} className="ml-2">
                      • {user.userName || 'Unknown'}({user.userId}) |
                      视频: {user.videoTrack && user.videoTrack.isPlaying ? '有' : '无'} |
                      音频: {user.audioTrack && user.audioTrack.isPlaying ? '有' : '无'}
                    </div>
                  ))}
                </>
              )}
            </>
          ) : (
            <>
              音频轨道: {audioTrackCreated ? '已创建' : '未创建'}
            </>
          )}
        </div>
      </div>

      {/* Scrollable top region (Remote Video or Avatar) */}
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
          // 根据是否有远程视频来决定显示内容
          <div style={{ height: 500, minHeight: 500 }} className="bg-white rounded-lg shadow-lg border border-gray-200">
            {/* 优先检查 remoteUsers 中是否有用户有视频轨道 */}
            {(() => {
              console.log("[RTCCard] Video preview logic:", {
                isConnected,
                remoteUsersCount: remoteUsers.length,
                remoteUsers: remoteUsers.map(u => ({
                  userId: u.userId,
                  hasVideo: !!u.videoTrack,
                  hasAudio: !!u.audioTrack,
                  videoTrackType: u.videoTrack?.constructor.name,
                  audioTrackType: u.audioTrack?.constructor.name
                })),
                remoteuser: remoteuser ? {
                  userId: remoteuser.userId,
                  hasVideo: !!remoteuser.videoTrack,
                  hasAudio: !!remoteuser.audioTrack
                } : null
              });

              // 如果没有连接，显示空状态
              if (!isConnected) {
                console.log("[RTCCard] Not connected, showing AudioVisualizer");
                return <AudioVisualizerWrapper audioTrack={undefined} type="user" />;
              }

              // 查找第一个有视频轨道的远程用户
              const userWithVideo = remoteUsers.find(user => user.videoTrack && user.videoTrack.isPlaying);
              const currentUser: IAliRtcUser | undefined = userWithVideo || remoteuser || remoteUsers[0];

              if (currentUser) {
                console.log("[RTCCard] Current user details:", {
                  userId: currentUser.userId,
                  startsWithAi: currentUser.userId?.startsWith('ai_'),
                  hasVideoTrack: !!currentUser.videoTrack,
                  hasAudioTrack: !!currentUser.audioTrack,
                  videoTrackPlaying: currentUser.videoTrack?.isPlaying
                });

                // 检查用户名是否以 "ai_" 开头
                if (currentUser.userId && currentUser.userId.startsWith('ai_')) {
                  console.log("[RTCCard] AI user detected:", currentUser.userId, "showing Talkinghead");
                  return <TalkingheadBlock audioTrack={currentUser.audioTrack || undefined} />;
                } else {
                  // 非AI用户，检查是否有视频
                  if (currentUser.videoTrack && currentUser.videoTrack.isPlaying) {
                    console.log("[RTCCard] Non-AI user with video:", currentUser.userId);
                    return (
                      <RemoteStreamPlayer
                        videoTrack={currentUser.videoTrack}
                        audioTrack={currentUser.audioTrack}
                        fit="cover"
                      />
                    );
                  } else {
                    // 非AI用户但没有视频，显示音频可视化
                    console.log("[RTCCard] Non-AI user without video:", currentUser.userId, "showing AudioVisualizer");
                    return <AudioVisualizerWrapper audioTrack={currentUser.audioTrack || undefined} type="user" />;
                  }
                }
              }

              // 没有远程用户时显示空状态
              console.log("[RTCCard] No remote users, showing AudioVisualizer");
              return <AudioVisualizerWrapper audioTrack={undefined} type="user" />;
            })()}
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
