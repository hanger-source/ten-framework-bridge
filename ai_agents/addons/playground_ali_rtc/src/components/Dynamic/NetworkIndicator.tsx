"use client"

import * as React from "react"
import { aliRtcManager } from "@/manager";
import { NetworkIconByLevel } from "@/components/Icon"
import { AliRtcEvents } from "@/manager/rtc/ali-types"

type NetworkQualityData = Parameters<AliRtcEvents['networkQuality']>[0]

export default function NetworkIndicator() {
  const [networkQuality, setNetworkQuality] = React.useState<NetworkQualityData>()

  React.useEffect(() => {
    aliRtcManager.on("networkQuality", onNetworkQuality)

    return () => {
      aliRtcManager.off("networkQuality", onNetworkQuality)
    }
  }, [])

  const onNetworkQuality = (quality: NetworkQualityData) => {
    setNetworkQuality(quality)
  }

  return (
    <NetworkIconByLevel
      level={networkQuality?.uplinkQuality}
      className="h-4 w-4 md:h-5 md:w-5"
    />
  )
}
