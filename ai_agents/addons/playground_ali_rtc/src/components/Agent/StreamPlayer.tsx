"use client"

import * as React from "react"
import { aliRtcManager, IAliUserTracks } from "@/manager";

export interface StreamPlayerProps {
  videoTrack?: IAliUserTracks["videoTrack"]
  audioTrack?: IAliUserTracks["audioTrack"]
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
