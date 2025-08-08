"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import MessageList from "@/components/Chat/MessageList";
import { Button } from "@/components/ui/button";
import { Send } from "lucide-react";
import { webSocketManager, WebSocketConnectionState } from "@/manager/websocket/websocket";
import { Data, Command, CommandResult } from "@/types/websocket";

export default function ChatCard(props: { className?: string }) {
  const { className } = props;
  const [inputValue, setInputValue] = React.useState("");
  const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  const [isConnected, setIsConnected] = React.useState(false);

  // 初始化 WebSocket 连接
  React.useEffect(() => {
    // 注册连接状态处理器
    webSocketManager.onConnectionStateChange((state) => {
      setConnectionState(state);
      setIsConnected(state === WebSocketConnectionState.OPEN);
    });

    // 注册消息处理器
    webSocketManager.onMessage('data', (message) => {
      console.log('收到数据消息:', message);
      // 这里可以更新聊天消息列表
    });

    webSocketManager.onMessage('cmd_result', (message) => {
      console.log('收到命令结果:', message);
    });

    // 连接 WebSocket
    webSocketManager.connect().catch(error => {
      console.error('WebSocket 连接失败:', error);
    });

    // 清理函数
    return () => {
      webSocketManager.disconnect();
    };
  }, []);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };

  const handleInputSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!inputValue.trim() || !isConnected) {
      return;
    }

    // 发送文本消息
    webSocketManager.sendTextData('chat_message', inputValue);
    console.log("发送消息:", inputValue);
    setInputValue("");
  };

  return (
    <>
      {/* Chat Card */}
      <div className={cn("h-full overflow-hidden min-h-0 flex", className)}>
        <div className="flex w-full flex-col p-4 flex-1">
          {/* Scrollable messages container */}
          <div className="flex-1 overflow-y-auto">
            <MessageList />
          </div>
          {/* Input area */}
          <div className="border-t pt-4">
            <div className="mb-2 text-xs text-gray-500">
              连接状态: {connectionState} {isConnected ? '✅' : '❌'}
            </div>
            <form onSubmit={handleInputSubmit} className="flex items-center space-x-2">
              <input
                type="text"
                placeholder={isConnected ? "输入消息..." : "连接中..."}
                value={inputValue}
                onChange={handleInputChange}
                disabled={!isConnected}
                className={cn(
                  "flex-grow rounded-md border bg-background p-1.5 focus:outline-none focus:ring-1 focus:ring-ring",
                  {
                    "opacity-50 cursor-not-allowed": !isConnected,
                  }
                )}
              />
              <Button
                type="submit"
                disabled={inputValue.length === 0 || !isConnected}
                size="icon"
                variant="outline"
                className={cn("bg-transparent", {
                  ["opacity-50"]: inputValue.length === 0 || !isConnected,
                })}
              >
                <Send className="h-4 w-4" />
                <span className="sr-only">发送消息</span>
              </Button>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}
