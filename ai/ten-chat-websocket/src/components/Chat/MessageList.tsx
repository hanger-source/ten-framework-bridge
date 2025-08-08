import * as React from "react";
import { Bot, Brain, MessageCircleQuestion } from "lucide-react";
import { EMessageDataType, EMessageType, type IChatItem } from "@/types/chat";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

export default function MessageList(props: { className?: string }) {
  const { className } = props;

  // 模拟聊天数据
  const chatItems: IChatItem[] = [
    {
      userId: "agent",
      userName: "AI助手",
      text: "你好！我是你的AI助手，有什么可以帮助你的吗？",
      data_type: EMessageDataType.TEXT,
      type: EMessageType.AGENT,
      isFinal: true,
      time: Date.now() - 10000,
    },
    {
      userId: "user",
      userName: "用户",
      text: "你好，我想了解一下这个项目",
      data_type: EMessageDataType.TEXT,
      type: EMessageType.USER,
      isFinal: true,
      time: Date.now() - 8000,
    },
    {
      userId: "agent",
      userName: "AI助手",
      text: "这是一个基于 Ten Framework Bridge 的聊天应用，集成了 Live2D 虚拟形象和实时语音交互功能。",
      data_type: EMessageDataType.TEXT,
      type: EMessageType.AGENT,
      isFinal: true,
      time: Date.now() - 5000,
    },
  ];

  const containerRef = React.useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  React.useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [chatItems]);

  return (
    <div
      ref={containerRef}
      className={cn("flex-grow space-y-2 overflow-y-auto p-4", className)}
    >
      {chatItems.map((item, index) => {
        return <MessageItem data={item} key={item.time} />;
      })}
    </div>
  );
}

export function MessageItem(props: { data: IChatItem }) {
  const { data } = props;

  return (
    <>
      <div
        className={cn("flex items-start gap-2", {
          "flex-row-reverse": data.type === EMessageType.USER,
        })}
      >
        {data.type === EMessageType.AGENT ? (
          data.data_type === EMessageDataType.REASON ? (
            <Avatar>
              <AvatarFallback>
                <Brain size={20} />
              </AvatarFallback>
            </Avatar>
          ) : (
            <Avatar>
              <AvatarFallback>
                <Bot />
              </AvatarFallback>
            </Avatar>
          )
        ) : null}
        <div
          className={cn("max-w-[80%] rounded-lg p-2", {
            "bg-secondary text-secondary-foreground": data.type === EMessageType.AGENT,
            "bg-primary text-primary-foreground": data.type === EMessageType.USER,
          })}
        >
          {data.data_type === EMessageDataType.IMAGE ? (
            <img src={data.text} alt="chat" className="w-full" />
          ) : (
            <p
              className={
                data.data_type === EMessageDataType.REASON
                  ? cn("text-xs", "text-zinc-500")
                  : ""
              }
            >
              {data.text}
            </p>
          )}
        </div>
      </div>
    </>
  );
}
