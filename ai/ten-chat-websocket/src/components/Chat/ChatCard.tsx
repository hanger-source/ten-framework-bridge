"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import MessageList from "@/components/Chat/MessageList";
import { Button } from "@/components/ui/button";
import { Send } from "lucide-react";
import { webSocketManager, WebSocketConnectionState } from "@/manager/websocket/websocket";
import { Data, Command, CommandResult, Location } from "@/types/websocket"; // Added Location
import { MESSAGE_CONSTANTS } from '@/common/constant'; // Added MESSAGE_CONSTANTS
import { MessageType } from "@/types/websocket"; // Added MessageType

export default function ChatCard(props: { className?: string }) {
  const { className } = props;
  const [inputValue, setInputValue] = React.useState("");
  const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  const [isConnected, setIsConnected] = React.useState(false);
  const [chatMessages, setChatMessages] = React.useState<{
    text: string;
    role: 'user' | 'agent' | 'assistant';
  }[]>([]);

  // 初始化 WebSocket 连接
  React.useEffect(() => {
    // 注册连接状态处理器
    const unsubscribeConnectionState = webSocketManager.onConnectionStateChange((state) => {
      setConnectionState(state);
      setIsConnected(state === WebSocketConnectionState.OPEN);
    });

    // 注册消息处理器
    const unsubscribeData = webSocketManager.onMessage(MessageType.DATA, (message) => {
      console.log('收到数据消息:', message);
      console.log('ChatCard: received message properties', message.properties);
      // 根据返回数据 的 property 里面的属性 text 和 role 来渲染 已经的 对话框
      if (message.type === MessageType.DATA && message.properties) {
        const { text, role, end_of_segment } = message.properties; // Destructure end_of_segment
        console.log('ChatCard: extracted text', text);
        // console.log('ChatCard: extracted role', role, 'type', typeof role);
        // console.log('ChatCard: extracted end_of_segment', end_of_segment, 'type', typeof end_of_segment);
        if (typeof text === 'string' && (role === 'user' || role === 'agent' || role === 'assistant')) {
          setChatMessages((prevMessages) => {
            const lastMessage = prevMessages[prevMessages.length - 1];
            // If it's a streaming message (end_of_segment === false) and the role is the same as the last message
            if (lastMessage && lastMessage.role === role && end_of_segment === false) {
              // Append text to the last message
              const updatedMessages = [...prevMessages];
              updatedMessages[updatedMessages.length - 1] = {
                ...lastMessage,
                text: lastMessage.text + text,
              };
              console.log('ChatCard: chatMessages updated (appended)', updatedMessages);
              return updatedMessages;
            } else {
              // Add a new message (either a new segment starts, or it's the end of a segment, or initial message)
              const newMessages = [...prevMessages, { text, role }];
              console.log('ChatCard: chatMessages updated (new message)', newMessages);
              return newMessages;
            }
          });
        }
      }
    });

    const unsubscribeCmdResult = webSocketManager.onMessage('cmd_result', (message) => {
      console.log('收到命令结果:', message);
    });

    // 连接 WebSocket
    const initiateConnection = async () => {
      try {
        await webSocketManager.connect();
      } catch (error) {
        console.error('WebSocket 连接失败:', error);
      }
    };
    initiateConnection();

    // 清理函数
    return () => {
      unsubscribeConnectionState();
      unsubscribeData();
      unsubscribeCmdResult();
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
    webSocketManager.sendTextData('text_data', inputValue, srcLoc, destLocs);

    // 添加用户消息到聊天列表
    setChatMessages((prevMessages) => [
      ...prevMessages,
      { text: inputValue, role: 'user' },
    ]);

    console.log("发送消息:", inputValue);
    setInputValue("");
  };

  // 定义 srcLoc 和 destLocs (暂时硬编码)
  const srcLoc: Location = {
    app_uri: "mock_front://test_app",
    graph_id: "test-websocket-echo-graph",
    extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  };

  const destLocs: Location[] = [
    {
      app_uri: "mock_front://test_app",
      graph_id: "test-websocket-echo-graph",
      extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
    },
  ];

  return (
    <>
      {/* Chat Card */}
      <div className={cn("h-full overflow-hidden min-h-0 flex", className)}>
        <div className="flex w-full flex-col flex-1"> {/* Removed p-4 */}
          {/* Scrollable messages container */}
          <div className="flex-1 overflow-y-auto px-4 pt-4"> {/* Added px-4 pt-4 */}
            <MessageList messages={chatMessages} />
          </div>
          {/* Input area */}
          <div className="border-t pt-4 px-4 pb-4"> {/* Added px-4 pb-4 */}
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
