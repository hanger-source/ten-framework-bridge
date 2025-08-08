// 聪明的开发杭一: 新增后端WebSocket消息相关的类型定义
export enum MessageType {
  INVALID = 'invalid',
  COMMAND = 'cmd',
  COMMAND_RESULT = 'cmd_result',
  COMMAND_CLOSE_APP = 'cmd_close_app',
  COMMAND_START_GRAPH = 'cmd_start_graph',
  COMMAND_STOP_GRAPH = 'cmd_stop_graph',
  COMMAND_TIMER = 'cmd_timer',
  COMMAND_TIMEOUT = 'cmd_timeout',
  DATA = 'data',
  VIDEO_FRAME = 'video_frame',
  AUDIO_FRAME = 'audio_frame',
}

export interface Location {
  app_uri: string;
  graph_id: string;
  extension_name: string;
}

export interface BaseMessage {
  type: MessageType;
  name?: string; // 对应后端的name，可以为空
  source_location?: Location;
  destination_locations?: Location[];
  properties?: { [key: string]: any }; // 后端Properties是Map<String, Object>
  timestamp?: number;
}

export interface AudioFrameMessage extends BaseMessage {
  type: MessageType.AUDIO_FRAME;
  data: Uint8Array;
  is_eof: boolean;
  sample_rate: number;
  channels: number;
  bits_per_sample: number;
  format: string;
  samples_per_channel?: number;
}

export interface DataMessage extends BaseMessage {
  type: MessageType.DATA;
  data: Uint8Array;
  is_eof: boolean;
  content_type?: string;
  encoding?: string;
}

export interface CommandMessage extends BaseMessage {
  type: MessageType.COMMAND;
  // TODO: 聪明的开发杭一: 根据后端Command.java的实际结构细化
}

export interface CommandResultMessage extends BaseMessage {
  type: MessageType.COMMAND_RESULT;
  // TODO: 聪明的开发杭一: 根据后端CommandResult.java的实际结构细化
}

// 联合类型，包含所有可能的消息类型
export type WebSocketMessage =
  | AudioFrameMessage
  | DataMessage
  | CommandMessage
  | CommandResultMessage;

// 聪明的开发杭一: WebSocket连接状态
export enum WebSocketConnectionState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  ERROR = 'ERROR',
}

// 聪明的开发杭一: 定义WebSocket服务接口
export interface IWebSocketService {
  connect(url: string, onOpen: () => void, onMessage: (message: WebSocketMessage) => void, onClose: () => void, onError: (error: Event) => void): void;
  disconnect(): void;
  sendMessage(message: WebSocketMessage): void;
  getConnectionState(): WebSocketConnectionState;
}