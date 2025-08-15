"use client";

import * as React from "react";
import { NetworkIconByLevel } from "@/components/Icon";
import { WebSocketConnectionState } from "@/types/websocket"; // Corrected import path

export default function NetworkIndicator({
  connectionState,
}: {
  connectionState: WebSocketConnectionState;
}) {

  React.useEffect(() => {
    return () => {
    };
  }, []);

  const getNetworkLevel = (state: WebSocketConnectionState) => {
    switch (state) {
      case WebSocketConnectionState.OPEN:
        return 3;
      case WebSocketConnectionState.CONNECTING:
        return 2;
      case WebSocketConnectionState.CLOSED:
      case WebSocketConnectionState.CLOSING:
      default:
        return 0;
    }
  };

  return (
    <NetworkIconByLevel
      level={getNetworkLevel(connectionState)}
      className="h-4 w-4 md:h-5 md:w-5"
    />
  );
}
