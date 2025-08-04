"use client"

import * as React from "react"
import { aliRtcManager, IAliUserTracks } from "@/manager";
import { RemoteVideoTrack, RemoteAudioTrack } from "dingrtc";

export interface StreamPlayerProps {
  videoTrack?: IAliUserTracks["videoTrack"] | RemoteVideoTrack
  audioTrack?: IAliUserTracks["audioTrack"] | RemoteAudioTrack
  style?: React.CSSProperties
  fit?: "cover" | "contain" | "fill"
  onClick?: () => void
  mute?: boolean
}

export const LocalStreamPlayer = React.forwardRef(
  (props: StreamPlayerProps, ref) => {
    const {
      videoTrack,
      audioTrack,
      mute = false,
      style = {},
      fit = "cover",
      onClick = () => {},
    } = props
    const vidDiv = React.useRef(null)

    React.useLayoutEffect(() => {
      const config = { fit }

      if (videoTrack && vidDiv.current) {
        if (mute) {
          videoTrack.stop()
        } else {
          // 如果轨道没有在播放，则开始播放
          if (!videoTrack.isPlaying) {
            console.log("[LocalStreamPlayer] Starting video playback");
            videoTrack.play(vidDiv.current!, config)
          }
        }
      }

      return () => {
        if (videoTrack) {
          console.log("[LocalStreamPlayer] Stopping video playback");
          videoTrack.stop()
        }
      }
    }, [videoTrack, fit, mute])

    // local audio track need not to be played
    // useLayoutEffect(() => {}, [audioTrack, localAudioMute])

    return (
      <div
        className="relative h-full w-full overflow-hidden"
        style={style}
        ref={vidDiv}
        onClick={onClick}
      />
    )
  },
)

export const RemoteStreamPlayer = React.forwardRef(
  (props: StreamPlayerProps, ref) => {
    const {
      videoTrack,
      audioTrack,
      mute = false,
      style = {},
      fit = "cover",
      onClick = () => {},
    } = props
    const vidDiv = React.useRef(null)

    React.useLayoutEffect(() => {
      const config = { fit }

      console.log("[RemoteStreamPlayer] useLayoutEffect triggered:", {
        hasVideoTrack: !!videoTrack,
        videoTrackType: videoTrack?.constructor.name,
        videoTrackState: videoTrack ? {
          isPlaying: videoTrack.isPlaying
        } : null,
        hasVidDiv: !!vidDiv.current
      });

      if (videoTrack && vidDiv.current) {
        if (mute) {
          videoTrack.stop()
        } else {
          // 如果轨道没有在播放，则开始播放
          if (!videoTrack.isPlaying) {
            console.log("[RemoteStreamPlayer] Starting video playback");
            videoTrack.play(vidDiv.current!, config)
          } else {
            console.log("[RemoteStreamPlayer] Video track already playing");
          }
        }
      } else {
        console.log("[RemoteStreamPlayer] Missing videoTrack or vidDiv:", {
          hasVideoTrack: !!videoTrack,
          hasVidDiv: !!vidDiv.current
        });
      }

      return () => {
        if (videoTrack) {
          console.log("[RemoteStreamPlayer] Stopping video playback");
          videoTrack.stop()
        }
      }
    }, [videoTrack, fit, mute])

    // remote audio track need to be played
    React.useLayoutEffect(() => {
      if (audioTrack && !mute) {
        audioTrack.play()
      }
    }, [audioTrack, mute])

    return (
      <div
        className="relative h-full w-full overflow-hidden"
        style={style}
        ref={vidDiv}
        onClick={onClick}
      />
    )
  },
)
