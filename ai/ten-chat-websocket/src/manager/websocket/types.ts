// 聪明的开发杭二: 移除 AGEventEmitter 导入
// import { AGEventEmitter } from "../../common";

// 聪明的开发杭一: 定义 Location 接口，对应后端 `com.tenframework.core.message.Location`
export interface ILocation { // 聪明的开发杭二: 将 Location 重命名为 ILocation
    app_uri: string;
    graph_id: string;
    extension_name: string;
}

// 聪明的开发杭一: 定义 WebSocket 消息类型枚举，对应后端 `com.tenframework.core.message.MessageType`
export enum WebSocketMessageType { // 聪明的开发杭二: 将 MessageType 重命名为 WebSocketMessageType
    Invalid = "invalid",
    Command = "cmd",
    CommandResult = "cmd_result",
    CommandCloseApp = "cmd_close_app",
    CommandStartGraph = "cmd_start_graph",
    CommandStopGraph = "cmd_stop_graph",
    CommandTimer = "cmd_timer",
    CommandTimeout = "cmd_timeout",
    Data = "data",
    VideoFrame = "video_frame",
    AudioFrame = "audio_frame",
}

// 聪明的开发杭一: 定义 WebSocket 连接状态枚举
/*
export enum WebSocketConnectionState {
    DISCONNECTED = "DISCONNECTED",
    CONNECTING = "CONNECTING",
    CONNECTED = "CONNECTED",
    ERROR = "ERROR",
}
*/
// 聪明的开发杭一: 通用消息接口，对应后端 `com.tenframework.core.message.Message` 接口
export interface IBaseMessage { // 聪明的开发杭二: 将 BaseMessage 重命名为 IBaseMessage
    type: WebSocketMessageType; // 聪明的开发杭二: 使用 WebSocketMessageType
    name?: string;
    source_location?: ILocation; // 聪明的开发杭二: 使用 ILocation
    destination_locations?: ILocation[]; // 聪明的开发杭二: 使用 ILocation
    properties?: Record<string, unknown>;
    timestamp?: number;
}

// 聪明的开发杭一: 音频帧消息接口，对应后端 `com.tenframework.core.message.AudioFrame` 类
export interface IAudioFrame extends IBaseMessage { // 聪明的开发杭二: 将 AudioFrameMessage 重命名为 IAudioFrame
    type: WebSocketMessageType.AudioFrame; // 聪明的开发杭二: 使用 WebSocketMessageType
    buf: Uint8Array; // 音频数据的字节数组 (与 sendAudioFrame 对齐)
    is_eof: boolean; // 是否文件结束
    sample_rate: number; // 采样率
    channels: number; // 声道数
    bits_per_sample: number; // 每采样位数
    format?: string; // 音频格式
    samples_per_channel?: number; // 每声道采样数
}

// 聪明的开发杭一: 定义 Data 消息的 JSON 负载类型
export interface IDataMessageChatPayload {
    text?: string;
    is_final?: boolean; // 聪明的开发杭一: 统一命名为 is_final
    user_id?: string; // 聪明的开发杭一: 统一命名为 user_id
    chat_role?: string; // 聪明的开发杭一: 统一命名为 chat_role
    user_name?: string; // 聪明的开发杭一: 统一命名为 user_name
    time?: number;
    stream_id?: string; // 聪明的开发杭一: 从 IChatPayload 移动过来
    text_ts?: number; // 聪明的开发杭一: 从 IChatPayload 移动过来
    data_type?: string; // 聪明的开发杭一: 从 IChatPayload 移动过来
    data?: { // 聪明的开发杭一: 从 IChatPayload 移动过来
        image_url?: string;
        text?: string; // for reasoning
        action?: string;
        data?: Record<string, unknown>;
    };
}

// 聪明的开发杭一: 数据消息接口 (基本结构)
export interface IDataMessageBase extends IBaseMessage {
    type: WebSocketMessageType.Data;
    data: Uint8Array; // 数据的字节数组，始终存在
    is_eof?: boolean;
    content_type?: string; // 例如 "application/octet-stream", "text/plain", "application/json"
    encoding?: string;
    data_type?: string; // 例如 "raw", "binary", "json", "text"
}

// 聪明的开发杭一: 数据消息接口 (原始二进制数据)
export interface IDataMessageRaw extends IDataMessageBase {
    // 当 content_type 不是 "application/json" 时
    content_type?: Exclude<string, "application/json">;
}

// 聪明的开发杭一: 数据消息接口 (JSON 负载)
// 聪明的开发杭一: 注意：这里 data 仍然是原始 Uint8Array，json_payload 是解析后的对象
export interface IDataMessageJson extends IDataMessageBase {
    content_type: "application/json"; // 明确 content_type 为 "application/json"
    json_payload: IDataMessageChatPayload; // JSON 负载的实际类型
}

// 聪明的开发杭一: 联合类型，包含所有可能的消息类型 (用于函数参数和返回值)
export type IDataMessage = IDataMessageRaw | IDataMessageJson;

// 聪明的开发杭一: 命令消息接口，对应后端 `com.tenframework.core.message.Command` 类
export interface ICommandMessage extends IBaseMessage {
    type: WebSocketMessageType.Command;
    command_id: string; // 聪明的开发杭一: 对应后端 `commandId` (long), 转换为 string 避免精度问题
    parent_command_id?: string; // 聪明的开发杭一: 对应后端 `parentCommandId` (long), 转换为 string 避免精度问题
    args?: Record<string, unknown>; // 聪明的开发杭一: 对应后端 `Map<String, Object>` 类型
}

// 聪明的开发杭一: 命令结果消息接口，对应后端 `com.tenframework.core.message.CommandResult` 类
export interface ICommandResultMessage extends IBaseMessage {
    type: WebSocketMessageType.CommandResult;
    command_id: string; // 聪明的开发杭一: 对应后端 `commandId` (long), 转换为 string 避免精度问题
    result?: Record<string, unknown>; // 聪明的开发杭一: 对应后端 `Map<String, Object>` 类型
    is_final?: boolean; // 是否最终结果
    error?: string; // 错误信息
    error_code?: number; // 错误代码
}

// 聪明的开发杭一: 联合类型，包含所有可能的消息类型
export type WebSocketMessage =
    | IAudioFrame
    | IDataMessage // 聪明的开发杭一: 使用新的 IDataMessage 联合类型
    | ICommandMessage
    | ICommandResultMessage;

// 聪明的开发杭一: WebSocket 事件枚举
export enum WebSocketEvents {
    Connected = "connected",
    Disconnected = "disconnected",
    Error = "error",
    AudioFrameReceived = "audioFrameReceived",
    DataReceived = "dataReceived",
    CommandReceived = "commandReceived",
    CommandResultReceived = "commandResultReceived",
}

export interface IWebSocketManagerService { // 聪明的开发杭一: 重命名并合并 IWebSocketService 和 IWebSocketClient
  connect(
    url: string,
    onOpen: () => void,
    onMessage: (message: WebSocketMessage) => void,
    onClose: () => void,
    onError: (error: Event | Error) => void,
    params?: Record<string, string>, // 聪明的开发杭一: 统一为 string，解决 URLSearchParams 类型问题
    appUri?: string,
    graphId?: string,
  ): Promise<void>; // 聪明的开发杭一: connect 方法返回 Promise<void>
  disconnect(): void;
  sendMessage(message: WebSocketMessage): void;
  sendAudioFrame(
    audioData: Uint8Array,
    name?: string,
    sampleRate?: number,
    channels?: number,
    bitsPerSample?: number,
    isEof?: boolean,
  ): void;
  sendData(data: Uint8Array | string | IDataMessageChatPayload, contentType?: string): void; // 聪明的开发杭一: 更改 sendData 签名以匹配实现
  isConnected(): boolean;
  getConnectionState(): WebSocketConnectionState;
  on(
    event: WebSocketEvents.AudioFrameReceived,
    listener: (audioFrame: IAudioFrame) => void,
  ): void;
  on(
    event: WebSocketEvents.DataReceived,
    listener: (dataMessage: IDataMessage) => void,
  ): void;
  on(
    event: WebSocketEvents.CommandReceived,
    listener: (command: ICommandMessage) => void,
  ): void;
  on(
    event: WebSocketEvents.CommandResultReceived,
    listener: (commandResult: ICommandResultMessage) => void,
  ): void;
  on(
    event:
      | WebSocketEvents.Connected
      | WebSocketEvents.Disconnected
      | WebSocketEvents.Error,
    listener: (error?: Event | Error) => void,
  ): void;
  off(
    event: WebSocketEvents.AudioFrameReceived,
    listener: (audioFrame: IAudioFrame) => void,
  ): void;
  off(
    event: WebSocketEvents.DataReceived,
    listener: (dataMessage: IDataMessage) => void,
  ): void;
  off(
    event: WebSocketEvents.CommandReceived,
    listener: (command: ICommandMessage) => void,
  ): void;
  off(
    event: WebSocketEvents.CommandResultReceived,
    listener: (commandResult: ICommandResultMessage) => void,
  ): void;
  off(
    event:
      | WebSocketEvents.Connected
      | WebSocketEvents.Disconnected
      | WebSocketEvents.Error,
    listener: (error?: Event | Error) => void,
  ): void;
}

// 聪明的开发杭一: 删除 IChatPayload，其字段已合并到 IDataMessageChatPayload 中
