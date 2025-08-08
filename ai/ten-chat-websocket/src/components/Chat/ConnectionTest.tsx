import React from 'react';
import { Button } from '@/components/ui/button';
import { webSocketManager, WebSocketConnectionState } from '@/manager/websocket/websocket';
import { CommandType, MESSAGE_CONSTANTS } from '@/types/websocket';

export default function ConnectionTest() {
  const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  const [testMessage, setTestMessage] = React.useState('');
  const [lastResponse, setLastResponse] = React.useState<string>('');

  // 从 localStorage 读取设置，如果没有则使用默认值
  const [graphName, setGraphName] = React.useState(() => {
    return localStorage.getItem('websocket_graph_name') || 'test-websocket-echo-graph';
  });
  const [appUri, setAppUri] = React.useState(() => {
    return localStorage.getItem('websocket_app_uri') || 'mock_front://test_app';
  });

  React.useEffect(() => {
    webSocketManager.onConnectionStateChange((state) => {
      setConnectionState(state);
    });

    // 注册消息处理器
    webSocketManager.onMessage('cmd_result', (message) => {
      console.log('收到命令结果:', message);
      setLastResponse(JSON.stringify(message, null, 2));
    });

    webSocketManager.onMessage('data', (message) => {
      console.log('收到数据消息:', message);
      setLastResponse(JSON.stringify(message, null, 2));
    });
  }, []);

  // 保存设置到 localStorage
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

  const handleSendTestMessage = () => {
    if (connectionState === WebSocketConnectionState.OPEN) {
      webSocketManager.sendTextData('test_message', testMessage);
      setTestMessage('');
    }
  };

  const handleTestStartGraph = () => {
    if (connectionState === WebSocketConnectionState.OPEN) {
      // 发送 start_graph 命令，只保留必要的 properties
      webSocketManager.sendCommand(CommandType.START_GRAPH, {
        properties: {
          [MESSAGE_CONSTANTS.PROPERTY_CLIENT_APP_URI]: 'mock_front://test_app',
          [MESSAGE_CONSTANTS.PROPERTY_CLIENT_GRAPH_NAME]: 'test_graph'
        }
      });
      console.log('发送 start_graph 命令');
    }
  };

  const handleTestStopGraph = () => {
    if (connectionState === WebSocketConnectionState.OPEN) {
      // 发送 stop_graph 命令，所有属性都放在 properties 中
      webSocketManager.sendCommand(CommandType.STOP_GRAPH, {
        properties: {
          [MESSAGE_CONSTANTS.PROPERTY_CLIENT_LOCATION_URI]: `${appUri}/${graphName}`
        }
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
          <h4 className="text-sm font-medium">设置</h4>
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
        </div>

        <div className="flex items-center gap-4">
          <span className="text-sm">连接状态:</span>
          <span className={`px-2 py-1 rounded text-xs ${
            connectionState === WebSocketConnectionState.OPEN
              ? 'bg-green-100 text-green-800'
              : 'bg-red-100 text-red-800'
          }`}>
            {connectionState}
          </span>
        </div>

        <div className="flex gap-2">
          <Button
            onClick={handleConnect}
            disabled={connectionState === WebSocketConnectionState.OPEN}
            size="sm"
          >
            连接
          </Button>
          <Button
            onClick={handleDisconnect}
            disabled={connectionState === WebSocketConnectionState.CLOSED}
            size="sm"
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

        <div className="space-y-2">
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
        </div>

        {/* 响应显示区域 */}
        {lastResponse && (
          <div className="mt-4">
            <h4 className="text-sm font-medium mb-2">最后收到的响应:</h4>
            <div className="bg-gray-100 p-3 rounded-md text-xs font-mono overflow-auto max-h-32">
              <pre>{lastResponse}</pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}