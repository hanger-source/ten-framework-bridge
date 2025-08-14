"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import MessageList from "@/components/Chat/MessageList";
import { Button } from "@/components/ui/button";
import { Send } from "lucide-react";
import { webSocketManager } from "@/manager/websocket/websocket"; // Keep this import for now, although not used directly in this file
import { SessionConnectionState, MessageType, CommandType, CommandResult } from "@/types/websocket"; // Keep these types
import AudioStreamPlayer from "@/components/Agent/AudioStreamPlayer"; // Import AudioStreamPlayer
import { useWebSocketSession } from "@/hooks/useWebSocketSession"; // Import useWebSocketSession
import { toast } from "sonner"; // Added for toast notifications

export default function ChatCard(props: { className?: string }) {
  const { className } = props;
  const [inputValue, setInputValue] = React.useState("");
  // Removed local connectionState and sessionState
  // const [connectionState, setConnectionState] = React.useState<WebSocketConnectionState>(WebSocketConnectionState.CLOSED);
  // const [sessionState, setSessionState] = React.useState<SessionConnectionState>(SessionConnectionState.IDLE); // New session state
  
  const { isConnected, sessionState, defaultLocation } = useWebSocketSession(); // Get sessionState from hook

  const [chatMessages, setChatMessages] = React.useState<{
    text: string;
    role: 'user' | 'agent' | 'assistant';
    end_of_segment?: boolean; // Added to track streaming status of each message
    groupTimestamp?: number; // Added to link messages to their group
  }[]>([]);
  const lastGroupTimestampRef = React.useRef<number | undefined>(undefined); // New ref for group timestamp

  // Removed connectionStateMap as ConnectionTest handles it
  // const connectionStateMap: Record<WebSocketConnectionState, string> = {
  //   [WebSocketConnectionState.CONNECTING]: '连接中',
  //   [WebSocketConnectionState.OPEN]: '已连接',
  //   [WebSocketConnectionState.CLOSING]: '断开中',
  //   [WebSocketConnectionState.CLOSED]: '已断开',
  // };

  // Removed showSettings and srcLoc as they are not needed here
  // const [showSettings, setShowSettings] = React.useState(false); // New state for toggling settings visibility
  // const srcLoc: Location = {
  //   app_uri: appUri,
  //   graph_id: graphName,
  //   extension_name: MESSAGE_CONSTANTS.SYS_EXTENSION_NAME,
  // };

  // Removed WebSocket connection and message handling useEffect, ChatCard should not manage its own connection or session state
  React.useEffect(() => {
    // ChatCard should only consume messages, not manage connection or session state
    const unsubscribeData = webSocketManager.onMessage(MessageType.DATA, (message) => {
      console.log('ChatCard: 收到数据消息:', message);
      console.log('ChatCard: received message properties', message.properties);
      // 根据返回数据 的 property 里面的属性 text 和 role 来渲染 已经的 对话框
      if (message.type === MessageType.DATA && message.properties) {
        // Use audio_text if available, otherwise fallback to text
        const { role, end_of_segment, group_timestamp: currentGroupTimestamp } = message.properties;
        const text = message.properties.audio_text || message.properties.text; // Prefer audio_text

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

    const unsubscribeCmdResult = webSocketManager.onMessage(MessageType.CMD_RESULT, (message) => {
      // ChatCard no longer manages its own session state, rely on Home via useWebSocketSession
      // This handler can still be used for displaying toasts related to command results if needed
      console.log('ChatCard: 收到命令结果 (via onMessage):', message);
      const commandResult = message as CommandResult; // Explicit type assertion
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

    // Removed subscription to command send events, rely on Home for session state
    // const unsubscribeCommandSend = webSocketManager.onCommandSend((commandName, properties) => {
    //   if (commandName === CommandType.START_GRAPH) {
    //     console.log('ChatCard: START_GRAPH command sent, setting session state to CONNECTING_SESSION.');
    //     setSessionState(SessionConnectionState.CONNECTING_SESSION);
    //   }
    // });

    // Removed initial connection attempt
    // const initiateConnection = async () => {
    //   try {
    //     await webSocketManager.connect();
    //   } catch (error) {
    //     console.error('WebSocket 连接失败:', error);
    //   }
    // };
    // initiateConnection();

    // 清理函数
    return () => {
      unsubscribeData();
      unsubscribeCmdResult();
      // unsubscribeCommandSend(); // Removed
      // webSocketManager.disconnect(); // Removed, Home handles global connection
    };
  }, []); // Dependencies are empty as it should only subscribe to data and cmd results, not connection state

  // Removed handleConnect, handleDisconnect, saveSettings, handleTestStartGraph, handleTestStopGraph
  // const handleConnect = async () => { ... };
  // const handleDisconnect = () => { ... };
  // const saveSettings = () => { ... };
  // const handleTestStartGraph = () => { ... };
  // const handleTestStopGraph = () => { ... };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };

  const handleInputSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // 仅当会话激活时才允许发送消息
    if (!inputValue.trim() || sessionState !== SessionConnectionState.SESSION_ACTIVE) {
      return;
    }

    // 发送文本消息
    webSocketManager.sendTextData('text_data', inputValue, defaultLocation, [defaultLocation]); // Use defaultLocation from hook

    // 添加用户消息到聊天列表
    setChatMessages((prevMessages) => [
      ...prevMessages,
      { text: inputValue, role: 'user', end_of_segment: true, groupTimestamp: undefined },
    ]);

    console.log("发送消息:", inputValue);
    setInputValue("");
  };

  // Removed srcLoc and destLocs as they are now provided by useWebSocketSession
  // const srcLoc: Location = { ... };
  // const destLocs: Location[] = [ ... ];

  return (
    <>
      {/* Chat Card */}
      <div className={cn("h-full overflow-hidden flex flex-col", className)}>
        <div className="flex w-full flex-col flex-1">
          {/* Scrollable messages container */}
          <div className="flex-1 overflow-y-auto px-4 pt-4">
            <MessageList messages={chatMessages} />
          </div>
          {/* Input area */}
          <div className="border-t pt-4 px-4 pb-4">
            {/* 会话状态显示区域 */}
            <div className="flex items-center space-x-2 mb-2">
              {sessionState === SessionConnectionState.SESSION_ACTIVE && (
                <div className="w-2.5 h-2.5 bg-green-500 rounded-full animate-pulse" title="会话已激活"></div>
              )}
              {sessionState === SessionConnectionState.CONNECTING_SESSION && (
                <div className="w-2.5 h-2.5 bg-yellow-500 rounded-full animate-pulse" title="正在连接会话"></div>
              )}
              {sessionState === SessionConnectionState.SESSION_FAILED && (
                <div className="w-2.5 h-2.5 bg-red-500 rounded-full" title="会话连接失败"></div>
              )}
              {sessionState === SessionConnectionState.IDLE && (
                <div className="w-2.5 h-2.5 bg-gray-400 rounded-full" title="AI 待命中"></div>
              )}
              <span className="text-sm text-gray-600">
                {sessionState === SessionConnectionState.IDLE && "AI 待命中"}
                {sessionState === SessionConnectionState.CONNECTING_SESSION && "正在连接会话..."}
                {sessionState === SessionConnectionState.SESSION_ACTIVE && "会话已激活"}
                {sessionState === SessionConnectionState.SESSION_FAILED && "会话连接失败"}
              </span>
            </div>
  
            <AudioStreamPlayer /> {/* Render AudioStreamPlayer */}
            <form onSubmit={handleInputSubmit} className="flex items-center space-x-2">
              <input
                type="text"
                placeholder={
                  sessionState === SessionConnectionState.SESSION_ACTIVE
                    ? "输入消息..."
                    : sessionState === SessionConnectionState.CONNECTING_SESSION
                      ? "正在连接会话..."
                      : sessionState === SessionConnectionState.SESSION_FAILED
                        ? "会话连接失败，请重试"
                        : "等待连接..." // Default or IDLE state
                }
                value={inputValue}
                onChange={handleInputChange}
                disabled={sessionState !== SessionConnectionState.SESSION_ACTIVE} // Only enabled when session is active
                className={cn(
                  "flex-grow rounded-md border bg-background p-1.5 focus:outline-none focus:ring-1 focus:ring-ring",
                  {
                    "opacity-50 cursor-not-allowed": sessionState !== SessionConnectionState.SESSION_ACTIVE,
                  }
                )}
              />
              <Button
                type="submit"
                disabled={inputValue.length === 0 || sessionState !== SessionConnectionState.SESSION_ACTIVE}
                size="icon"
                variant="outline"
                className={cn("bg-transparent", {
                  ["opacity-50"]: inputValue.length === 0 || sessionState !== SessionConnectionState.SESSION_ACTIVE,
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
