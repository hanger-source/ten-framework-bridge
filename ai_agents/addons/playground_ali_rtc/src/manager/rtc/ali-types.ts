import {
    DingRTCClient,
    CameraVideoTrack,
    MicrophoneAudioTrack,
    LocalVideoTrack,
    RemoteUser,
    NetworkQuality,
    LocalAudioTrack,
    RemoteAudioTrack,
    RemoteVideoTrack,
    LocalAudioStates,
    LocalVideoStates,
    RemoteAudioStates,
    RemoteVideoStates,
} from "dingrtc"
import { DingRTCError } from "@dingrtc/shared"
import DingRTC from "dingrtc"
import RTM from "@dingrtc/rtm"
import { IChatItem } from "@/types"

export interface IAliRtcUser {
    userId: string
    audioTrack?: RemoteAudioTrack
    videoTrack?: RemoteVideoTrack
}

export interface AliRtcEvents {
    remoteUserChanged: (user: IAliRtcUser) => void
    localTracksChanged: (tracks: IAliUserTracks) => void
    networkQuality: (quality: {
        uplinkQuality: NetworkQuality;
        downlinkQuality: NetworkQuality;
    }) => void
    textChanged: (text: IChatItem) => void
    error: (error: { code: string; message: string; originalError: DingRTCError; rtcType: 'ali' }) => void
}

export interface IAliUserTracks {
    videoTrack?: CameraVideoTrack
    screenTrack?: LocalVideoTrack
    audioTrack?: MicrophoneAudioTrack
}

// 设备信息类型
export interface IAliDeviceInfo {
    device: MediaDeviceInfo;
    state: 'active' | 'inactive';
}