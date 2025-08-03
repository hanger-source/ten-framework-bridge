"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { ICameraVideoTrack, ILocalVideoTrack, IMicrophoneAudioTrack } from "agora-rtc-sdk-ng"
import { useAppSelector, useAppDispatch, VOICE_OPTIONS, VideoSourceType, useIsCompactLayout } from "@/common"
import { useAgentSettings } from "@/hooks/useAgentSettings"
import { ITextItem, EMessageType, IChatItem } from "@/types"
import { rtcManager, IUserTracks, IRtcUser } from "@/manager"
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
import dynamic from "next/dynamic"
import ChatCard from "@/components/Chat/ChatCard"
import TalkingheadBlock from "@/components/Agent/TalkingHead"

let hasInit: boolean = false

export default function RTCCard(props: { className?: string }) {
  const { className } = props

  const dispatch = useAppDispatch()
  const options = useAppSelector((state) => state.global.options)
  const trulienceSettings = useAppSelector((state) => state.global.trulienceSettings)
  const { userId, channel } = options
  const [videoTrack, setVideoTrack] = React.useState<ICameraVideoTrack>()
  const [audioTrack, setAudioTrack] = React.useState<IMicrophoneAudioTrack>()
  const [screenTrack, setScreenTrack] = React.useState<ILocalVideoTrack>()
  const [remoteuser, setRemoteUser] = React.useState<IRtcUser>()
  const [videoSourceType, setVideoSourceType] = React.useState<VideoSourceType>(VideoSourceType.CAMERA)
  const useTrulienceAvatar = trulienceSettings.enabled
  const avatarInLargeWindow = trulienceSettings.avatarDesktopLargeWindow;

  const isCompactLayout = useIsCompactLayout();
  const { agentSettings } = useAgentSettings();

  const DynamicChatCard = dynamic(() => import("@/components/Chat/ChatCard"), {
    ssr: false,
  });

  React.useEffect(() => {
    if (!options.channel) {
      return
    }
    if (hasInit) {
      return
    }

    init()

    return () => {
      if (hasInit) {
        destory()
      }
    }
  }, [options.channel])

  const init = async () => {
    console.log("[rtc] init")
    rtcManager.on("localTracksChanged", onLocalTracksChanged)
    rtcManager.on("textChanged", onTextChanged)
    rtcManager.on("remoteUserChanged", onRemoteUserChanged)
    await rtcManager.createCameraTracks()
    await rtcManager.createMicrophoneAudioTrack()
    await rtcManager.join({
      channel,
      userId,
      agentSettings,
    })
    dispatch(
      setOptions({
        ...options,
        appId: rtcManager.appId ?? "",
        token: rtcManager.token ?? "",
      }),
    )
    await rtcManager.publish()
    dispatch(setRoomConnected(true))
    hasInit = true
  }

  const destory = async () => {
    console.log("[rtc] destory")
    rtcManager.off("textChanged", onTextChanged)
    rtcManager.off("localTracksChanged", onLocalTracksChanged)
    rtcManager.off("remoteUserChanged", onRemoteUserChanged)
    await rtcManager.destroy()
    dispatch(setRoomConnected(false))
    hasInit = false
  }

  const onRemoteUserChanged = (user: IRtcUser) => {
    console.log("[rtc] onRemoteUserChanged", user)
    if (useTrulienceAvatar) {
      // trulience SDK will play audio in synch with mouth
      user.audioTrack?.stop();
    }
    if (user.audioTrack) {
      setRemoteUser(user)
    }
  }

  const onLocalTracksChanged = (tracks: IUserTracks) => {
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

  const onVoiceChange = (value: any) => {
    dispatch(setVoiceType(value))
  }

  const onVideoSourceTypeChange = async (value: VideoSourceType) => {
    await rtcManager.switchVideoSource(value)
    setVideoSourceType(value)
  }

  return (
    <div className={cn("flex h-full flex-col min-h-0 bg-gray-50", className)}>
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
