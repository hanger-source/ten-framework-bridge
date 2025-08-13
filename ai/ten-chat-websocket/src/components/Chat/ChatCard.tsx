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
import AudioStreamPlayer from "@/components/Agent/AudioStreamPlayer"; // Import AudioStreamPlayer

export default function ChatCard(props: { className?: string }) {
  const { className } = props;
  const [inputValue, setInputValue] = React.useState("");
  const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  const [isConnected, setIsConnected] = React.useState(false);
  const [chatMessages, setChatMessages] = React.useState<{
    text: string;
    role: 'user' | 'agent' | 'assistant';
    end_of_segment?: boolean; // Added to track streaming status of each message
    groupTimestamp?: number; // Added to link messages to their group
  }[]>([]);
  const lastGroupTimestampRef = React.useRef<number | undefined>(undefined); // New ref for group timestamp

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
        const { text, role, end_of_segment, group_timestamp: currentGroupTimestamp } = message.properties;

        console.log('ChatCard: extracted text', text);
        console.log('ChatCard: currentGroupTimestamp', currentGroupTimestamp);
        console.log('ChatCard: lastGroupTimestampRef.current', lastGroupTimestampRef.current);

        if (typeof text === 'string' && (role === 'user' || role === 'agent' || role === 'assistant')) {
          setChatMessages((prevMessages) => {
            const newMessages = [...prevMessages];
            const lastMessage = newMessages[newMessages.length - 1];

            // --- 文本消息的 group_timestamp 处理：与音频同步
            // 如果是新的 group_timestamp，则更新 lastGroupTimestampRef，并确保旧流终结
            if (typeof currentGroupTimestamp === 'number' && (
              lastGroupTimestampRef.current === undefined || // First frame ever
              currentGroupTimestamp > lastGroupTimestampRef.current // New group started
            )) {
              // 如果上一个消息属于不同的组，确保它被标记为结束，不再被追加
              if (lastMessage && (lastMessage.role === 'agent' || lastMessage.role === 'assistant') && lastMessage.groupTimestamp !== currentGroupTimestamp) {
                lastMessage.end_of_segment = true; // Mark old segment as ended
              }
              lastGroupTimestampRef.current = currentGroupTimestamp; // 更新为最新的 group_timestamp
              console.log('ChatCard: New groupTimestamp for text detected, updating lastGroupTimestampRef.');
            } else if (typeof currentGroupTimestamp === 'number' && typeof lastGroupTimestampRef.current === 'number' && currentGroupTimestamp < lastGroupTimestampRef.current) {
              // 忽略旧的 group_timestamp 消息
              console.log('ChatCard: Discarding text from old group timestamp.');
              return prevMessages; // 不更新消息列表
            }
            // --- End of group_timestamp handling

            // Handle user messages (always new, and reset AI group tracker)
            if (role === 'user') {
              newMessages.push({ text, role, end_of_segment: true, groupTimestamp: undefined }); // User messages are always complete
              lastGroupTimestampRef.current = undefined; // Reset AI group tracker when user speaks
            } else { // role is 'agent' or 'assistant'
              // Conditions for appending to the last message (streaming within the same group)
              const shouldAppend = (
                lastMessage &&
                lastMessage.groupTimestamp === currentGroupTimestamp && // Must be the same active group
                lastMessage.role === role && // Must be the same role
                lastMessage.end_of_segment === false // Last message was an ongoing stream
              );

              if (shouldAppend) {
                // Append text and update end_of_segment status
                newMessages[newMessages.length - 1] = {
                  ...lastMessage,
                  text: lastMessage.text + text,
                  end_of_segment: end_of_segment // Update with current frame's end_of_segment status
                };
              } else {
                // Start a new message
                newMessages.push({ text, role, end_of_segment, groupTimestamp: currentGroupTimestamp });
                // If it's a new AI message bubble, update the lastGroupTimestampRef
                // This is already handled by the group_timestamp logic above, but ensure it's consistent
                if (typeof currentGroupTimestamp === 'number') {
                  lastGroupTimestampRef.current = currentGroupTimestamp;
                }
              }
            }
            console.log('ChatCard: chatMessages updated', newMessages);
            return newMessages;
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
      { text: inputValue, role: 'user', end_of_segment: true, groupTimestamp: undefined },
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
      <div className={cn("h-full overflow-hidden flex flex-col", className)}> {/* Changed flex to flex-col */}
        <div className="flex w-full flex-col flex-1"> {/* Removed p-4 */}
          {/* Scrollable messages container */}
          <div className="flex-1 overflow-y-auto px-4 pt-4"> {/* Added px-4 pt-4 */}
            <MessageList messages={chatMessages} />
          </div>
          {/* Input area */}
          <div className="border-t pt-4 px-4 pb-4"> {/* Added px-4 pb-4 */}
            <AudioStreamPlayer /> {/* Render AudioStreamPlayer */}
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
