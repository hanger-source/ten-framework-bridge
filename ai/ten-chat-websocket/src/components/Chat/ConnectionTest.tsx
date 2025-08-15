import React from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CommandType, SessionConnectionState, MessageType } from "@/types/websocket";
import { useWebSocketSession } from "@/hooks/useWebSocketSession"; // Import useWebSocketSession
import { MESSAGE_CONSTANTS } from "@/common/constant";
import { Location } from "@/types/websocket"; // Ensure Location is imported from websocket types

export function ConnectionTest() {
  const { isConnected, sessionState, sendMessage, sendCommand, defaultLocation, activeAppUri, activeGraphId } = useWebSocketSession(); // Destructure sendCommand and defaultLocation
  const [testMessage, setTestMessage] = React.useState(''); // State for test message input

  const getCommonSrcLoc = (): Location => ({
    app_uri: defaultLocation.app_uri, // Frontend fixed URI
    graph_id: defaultLocation.graph_id, // This will be the selected or active graph ID
    extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  });

  const getCommonDestLocs = (): Location[] => {
    if (!activeAppUri || !activeGraphId) {
      return [];
    }
    return [{
      app_uri: activeAppUri, // Backend returned app_uri
      graph_id: activeGraphId,
      extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
    }];
  };

  const handleSendTestMessage = () => {
    if (testMessage.trim() === '') {
      return;
    }
    sendMessage(testMessage); // Use sendMessage from useWebSocketSession
    setTestMessage('');
  };

  const handleSendStartGraphCommand = () => {
    // Use sendCommand from useWebSocketSession
    sendCommand(
      CommandType.START_GRAPH,
      getCommonSrcLoc(), // Pass constructed src_loc
      getCommonDestLocs(), // Pass constructed dest_locs
      { predefined_graph_name: defaultLocation.graph_id } // Use defaultLocation.graph_id which reflects selectedGraphId
    );
  };

  const handleSendStopGraphCommand = () => {
    // Use sendCommand from useWebSocketSession
    sendCommand(
      CommandType.STOP_GRAPH,
      getCommonSrcLoc(), // Pass constructed src_loc
      getCommonDestLocs(), // Pass constructed dest_locs
      { location_uri: activeAppUri } // Use activeAppUri
    );
  };

  return (
    <div className="flex flex-col space-y-2 p-4 border rounded-md shadow-sm">
      <h3 className="text-lg font-semibold">WebSocket Test Module</h3>
      <div className="flex space-x-2">
        <Button onClick={handleSendStartGraphCommand} disabled={!isConnected || sessionState !== SessionConnectionState.IDLE}>
          测试 START_GRAPH 命令
        </Button>
        <Button onClick={handleSendStopGraphCommand} disabled={!isConnected || sessionState !== SessionConnectionState.SESSION_ACTIVE}>
          测试 STOP_GRAPH 命令
        </Button>
      </div>
      <div className="flex space-x-2">
        <Input
          type="text"
          value={testMessage}
          onChange={(e) => setTestMessage(e.target.value)}
          placeholder="Enter test message"
          disabled={!isConnected || sessionState !== SessionConnectionState.SESSION_ACTIVE}
        />
        <Button onClick={handleSendTestMessage} disabled={!isConnected || sessionState !== SessionConnectionState.SESSION_ACTIVE}>
          发送测试消息
        </Button>
      </div>
      <p>WebSocket Connection: {isConnected ? "Connected" : "Disconnected"}</p>
      <p>Session State: {sessionState}</p>
      <p>Active Graph ID: {defaultLocation.graph_id}</p>
      <p>Active App URI: {activeAppUri}</p>
    </div>
  );
}