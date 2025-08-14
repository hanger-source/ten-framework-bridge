import React from 'react';
import { Button } from '@/components/ui/button';
import { webSocketManager } from "@/manager/websocket/websocket";
import { WebSocketConnectionState } from "@/types/websocket"; // Added Location
import { CommandType, Location, MessageType, CommandResult, Message, Data } from '@/types/websocket';
import { toast } from 'sonner';
import testWebsocketEchoGraph from "../../../public/test_websocket_echo_graph.json";
import { MESSAGE_CONSTANTS } from '@/common/constant';

export default function ConnectionTest() {
  const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  // const [testMessage, setTestMessage] = React.useState(''); // Removed
  const [graphName, setGraphName] = React.useState(() => {
    return localStorage.getItem('websocket_graph_name') || 'test-websocket-echo-graph';
  });
  const [appUri, setAppUri] = React.useState(() => {
    return localStorage.getItem('websocket_app_uri') || 'mock_front://test_app';
  });

  const connectionStateMap: Record<WebSocketConnectionState, string> = {
    [WebSocketConnectionState.CONNECTING]: '连接中',
    [WebSocketConnectionState.OPEN]: '已连接',
    [WebSocketConnectionState.CLOSING]: '断开中',
    [WebSocketConnectionState.CLOSED]: '已断开',
  };

  const [showSettings, setShowSettings] = React.useState(false); // New state for toggling settings visibility

  const srcLoc: Location = {
    app_uri: appUri,
    graph_id: graphName,
    extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  };

  React.useEffect(() => {
    const unsubscribeConnectionState = webSocketManager.onConnectionStateChange((state: WebSocketConnectionState) => {
      setConnectionState(state);
    });

    const unsubscribeCmdResult = webSocketManager.onMessage(MessageType.CMD_RESULT, (message: Message) => {
      const commandResult = message as CommandResult;
      console.log('收到命令结果:', commandResult);

      if (!commandResult.success && commandResult.errorMessage) {
        toast.error(commandResult.errorMessage, { duration: 5 });
      } else if (commandResult.success && commandResult.detail) {
        toast.success(commandResult.detail, { duration: 5 });
      } else if (commandResult.success) {
        toast.success('命令执行成功！', { duration: 5 });
      } else {
        toast.error('命令执行失败！', { duration: 5 });
      }
    });

    const unsubscribeData = webSocketManager.onMessage(MessageType.DATA, (message: Message) => {
      console.log('收到数据消息:', message);
      // toast.info("Data received!");
    });

    // Initial connection attempt
    const initiateConnection = async () => {
      try {
        await webSocketManager.connect();
      } catch (error) {
        console.error("Failed to connect on mount:", error);
      }
    };
    initiateConnection();

    return () => {
      unsubscribeConnectionState();
      unsubscribeCmdResult();
      unsubscribeData();
      // webSocketManager.disconnect(); // Disconnect only if necessary, manage global connection in App.tsx
    };
  }, [appUri, graphName]);

  const saveSettings = () => {
    localStorage.setItem('websocket_graph_name', graphName);
    localStorage.setItem('websocket_app_uri', appUri);
  };

  const handleConnect = async () => {
    try {
      await webSocketManager.connect();
      console.log('WebSocket 连接成功');
    } catch (error) {
      console.error('WebSocket 连接失败:', error);
    }
  };

  const handleDisconnect = () => {
    webSocketManager.disconnect();
    console.log('WebSocket 连接已断开');
  };

  // Removed handleSendTestMessage function
  // const handleSendTestMessage = () => {
  //   if (connectionState === WebSocketConnectionState.OPEN) {
  //     const destLocs: Location[] = [
  //       {
  //         app_uri: appUri,
  //         graph_id: graphName,
  //         extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  //       },
  //     ];
  //     webSocketManager.sendTextData('text_data', testMessage, srcLoc, destLocs);
  //     setTestMessage('');
  //   }
  // };

  const handleTestStartGraph = () => {
    if (connectionState === WebSocketConnectionState.OPEN) {
      const destLocs: Location[] = [
        {
          app_uri: appUri,
          graph_id: graphName,
          extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
        },
      ];
      const graphDefinition = {
        graph_name: graphName,
        graph_id: graphName,
        app_uri: appUri,
        nodes: testWebsocketEchoGraph.graph.nodes,
        connections: testWebsocketEchoGraph.graph.connections,
      };
      webSocketManager.sendCommand(CommandType.START_GRAPH, srcLoc, destLocs, {
        graph_json: JSON.stringify(graphDefinition),
      });
      console.log('发送 start_graph 命令');
    }
  };

  const handleTestStopGraph = () => {
    if (connectionState === WebSocketConnectionState.OPEN) {
      const destLocs: Location[] = [
        {
          app_uri: appUri,
          graph_id: graphName,
          extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
        },
      ];
      webSocketManager.sendCommand(CommandType.STOP_GRAPH, srcLoc, destLocs, {
        location_uri: `${appUri}/${graphName}`,
      });
      console.log('发送 stop_graph 命令');
    }
  };

  return (
    <div className="p-4 border rounded-lg bg-gray-50">
      <h3 className="text-lg font-semibold mb-4">WebSocket 连接测试</h3>

      <div className="space-y-4">
        {/* 设置区域 */}
        <div className="space-y-3 p-3 bg-white rounded border">
          <div className="flex justify-between items-center cursor-pointer" onClick={() => setShowSettings(!showSettings)}>
            <h4 className="text-sm font-medium">设置</h4>
            <span className="text-gray-500 text-lg">
              {showSettings ? '▲' : '▼'} {/* Arrow icon for toggle */}
            </span>
          </div>
          {showSettings && ( // Conditionally render settings content
            <div className="grid grid-cols-1 gap-3">
              <div>
                <label className="block text-xs text-gray-600 mb-1">App URI:</label>
                <input
                  type="text"
                  value={appUri}
                  onChange={(e) => setAppUri(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                  placeholder="mock_front://test_app"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">Graph Name:</label>
                <input
                  type="text"
                  value={graphName}
                  onChange={(e) => setGraphName(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                  placeholder="test-websocket-echo-graph"
                />
              </div>
              <Button
                onClick={saveSettings}
                size="sm"
                variant="outline"
                className="w-full"
              >
                保存设置
              </Button>
            </div>
          )}
        </div>

        <div className="flex items-center gap-4">
          <span className="text-sm">WebSocket :</span>
          <span className={`px-2 py-1 rounded text-xs ${
            connectionState === WebSocketConnectionState.OPEN
              ? 'bg-green-100 text-green-800'
              : 'bg-red-100 text-red-800'
          }`}>
            {connectionStateMap[connectionState]}
          </span>
          <Button
            onClick={handleConnect}
            disabled={connectionState === WebSocketConnectionState.OPEN}
            size="sm" // Reverted to sm
          >
            连接
          </Button>
          <Button
            onClick={handleDisconnect}
            disabled={connectionState === WebSocketConnectionState.CLOSED}
            size="sm" // Reverted to sm
            variant="outline"
          >
            断开
          </Button>
        </div>

        <div className="flex gap-2">
          <Button
            onClick={handleTestStartGraph}
            disabled={connectionState !== WebSocketConnectionState.OPEN}
            size="sm"
            variant="secondary"
          >
            测试 Start Graph
          </Button>
          <Button
            onClick={handleTestStopGraph}
            disabled={connectionState !== WebSocketConnectionState.OPEN}
            size="sm"
            variant="secondary"
          >
            测试 Stop Graph
          </Button>
        </div>

        {/* Removed Test Message Input and Button */}
        {/* <div className="space-y-2">
          <input
            type="text"
            placeholder="输入测试消息"
            value={testMessage}
            onChange={(e) => setTestMessage(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            disabled={connectionState !== WebSocketConnectionState.OPEN}
          />
          <Button
            onClick={handleSendTestMessage}
            disabled={!testMessage.trim() || connectionState !== WebSocketConnectionState.OPEN}
            size="sm"
          >
            发送测试消息
          </Button>
        </div> */}

      </div>
    </div>
  );
}