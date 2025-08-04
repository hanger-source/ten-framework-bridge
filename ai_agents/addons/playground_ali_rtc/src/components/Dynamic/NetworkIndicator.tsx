"use client"

import * as React from "react"
import { aliRtcManager } from "@/manager";
import { NetworkIconByLevel } from "@/components/Icon"
import { AliRtcEvents } from "@/manager/rtc/ali-types"

type NetworkQualityData = Parameters<AliRtcEvents['networkQuality']>[0]

export default function NetworkIndicator() {
  const [networkQuality, setNetworkQuality] = React.useState<NetworkQualityData>()
  const [isConnected, setIsConnected] = React.useState<boolean>(false)

  React.useEffect(() => {
    // 监听网络质量事件
    aliRtcManager.on("networkQuality", onNetworkQuality)

    // 监听连接状态变化
    const checkConnectionState = () => {
      const client = aliRtcManager.client;
      const currentState = client?.connectionState;
      const connected = currentState === 'connected';
      setIsConnected(connected);

      // 如果断开连接，重置网络状态为断开
      if (!connected) {
        console.log("[NetworkIndicator] Connection lost, resetting network quality");
        setNetworkQuality({
          uplinkQuality: 6, // DISCONNECTED
          downlinkQuality: 6
        });
      }
    };

    // 初始检查连接状态
    checkConnectionState();

    // 监听连接状态变化
    aliRtcManager.client?.on("connection-state-change", checkConnectionState);

    return () => {
      aliRtcManager.off("networkQuality", onNetworkQuality)
      aliRtcManager.client?.off("connection-state-change", checkConnectionState);
    }
  }, [])

  const onNetworkQuality = (quality: NetworkQualityData) => {
    // 直接更新网络质量，让组件根据连接状态决定显示什么
    setNetworkQuality(quality)
  }

  // 如果未连接，显示断开状态
  const displayQuality = isConnected ? networkQuality?.uplinkQuality : 6;

  return (
    <NetworkIconByLevel
      level={displayQuality}
      className="h-4 w-4 md:h-5 md:w-5"
    />
  )
}
