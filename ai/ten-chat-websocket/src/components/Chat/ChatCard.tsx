"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import { useAppDispatch, useAutoScroll, useAppSelector } from "@/common";
import { addChatItem } from "@/store/reducers/global";
import MessageList from "@/components/Chat/MessageList";
import { Button } from "@/components/ui/button";
import { Send } from "lucide-react";
import { webSocketManager } from "@/manager/websocket/websocket";
import { IDataMessage, WebSocketMessageType, WebSocketConnectionState } from "@/manager/websocket/types";
import { RootState } from "@/store";
import { IChatItem, EMessageType, EMessageDataType } from "@/types";

export default function ChatCard(props: { className?: string }) {
  const { className } = props;
  const [inputValue, setInputValue] = React.useState("");

  const websocketConnectionState = useAppSelector(
    (state: RootState) => state.global.websocketConnectionState,
  );

  const dispatch = useAppDispatch();
  const agentConnected = useAppSelector((state) => state.global.agentConnected);
  const options = useAppSelector((state) => state.global.options);

  const disableInputMemo = React.useMemo(() => {
    return (
      !options.channel ||
      !options.userId ||
      websocketConnectionState !== "open" ||
      !agentConnected
    );
  }, [options.channel, options.userId, websocketConnectionState, agentConnected]);

  const chatRef = React.useRef(null);

  useAutoScroll(chatRef);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setInputValue(e.target.value);
  };

  const handleInputSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!inputValue || disableInputMemo) {
      return;
    }
    const messageToSend: IDataMessage = {
      name: "textMessage",
      type: WebSocketMessageType.Data,
      text: inputValue,
      chatRole: EMessageType.USER,
      isFinal: true,
      content_type: "text/plain",
      encoding: "UTF-8",
    };
    webSocketManager.sendData(messageToSend);
    setInputValue("");
  };

  return (
    <>
      {/* Chat Card */}
      <div className={cn("h-full overflow-hidden min-h-0 flex", className)}>
        <div className="flex w-full flex-col p-4 flex-1">
          {/* Scrollable messages container */}
          <div className="flex-1 overflow-y-auto" ref={chatRef}>
            <MessageList />
          </div>
          {/* Input area */}
          <div
            className={cn("border-t pt-4", {
              ["hidden"]: websocketConnectionState !== "open",
            })}
          >
            <form
              onSubmit={handleInputSubmit}
              className="flex items-center space-x-2"
            >
              <input
                type="text"
                disabled={disableInputMemo}
                placeholder="Type a message..."
                value={inputValue}
                onChange={handleInputChange}
                className={cn(
                  "flex-grow rounded-md border bg-background p-1.5 focus:outline-none focus:ring-1 focus:ring-ring",
                  {
                    ["cursor-not-allowed"]: disableInputMemo,
                  },
                )}
              />
              <Button
                type="submit"
                disabled={disableInputMemo || inputValue.length === 0}
                size="icon"
                variant="outline"
                className={cn("bg-transparent", {
                  ["opacity-50"]: disableInputMemo || inputValue.length === 0,
                  ["cursor-not-allowed"]: disableInputMemo,
                })}
              >
                <Send className="h-4 w-4" />
                <span className="sr-only">Send message</span>
              </Button>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}
