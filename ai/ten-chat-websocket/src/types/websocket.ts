// WebSocket 消息基础接口
export interface Message {
    type: string;
    name: string;
    timestamp?: number;
    properties?: Record<string, any>;
}

// 数据消息
export interface Data extends Message {
    type: 'data';
    data: string;
    content_type?: string;
    encoding?: string;
    is_eof?: boolean;
}

// 命令消息
export interface Command extends Message {
    type: 'cmd';
    cmd_id: number;
    parent_cmd_id?: number;
    args?: Record<string, any>;
}

// 命令结果消息
export interface CommandResult extends Message {
    type: 'cmd_result';
    cmd_id: number;
    success: boolean;
    error?: string;
    data?: any;
}

// 音频帧消息
export interface AudioFrame extends Message {
    type: 'audio_frame';
    data: Uint8Array;
    sample_rate: number;
    channels: number;
    bits_per_sample: number;
    format: string;
    is_eof?: boolean;
}

// 视频帧消息
export interface VideoFrame extends Message {
    type: 'video_frame';
    data: Uint8Array;
    width: number;
    height: number;
    format: string;
    is_eof?: boolean;
}

// 位置信息
export interface Location {
    app_uri: string;
    graph_id: string;
    extension_name: string;
}

// 消息类型枚举
export enum MessageType {
    DATA = 'data',
    COMMAND = 'cmd',
    COMMAND_RESULT = 'cmd_result',
    AUDIO_FRAME = 'audio_frame',
    VIDEO_FRAME = 'video_frame',
}

// 命令类型枚举 - 匹配后端的 GraphEventCommandType
export enum CommandType {
    START_GRAPH = '__start_graph__',
    STOP_GRAPH = '__stop_graph__',
    ADD_EXTENSION_TO_GRAPH = '__add_extension_to_graph__',
    REMOVE_EXTENSION_FROM_GRAPH = '__remove_extension_from_graph__',
}

// 消息常量 - 匹配后端的 MessageConstants
export const MESSAGE_CONSTANTS = {
    NOT_APPLICABLE: 'N/A',
    SYS_EXTENSION_NAME: 'sys_engine',
    PROPERTY_CLIENT_LOCATION_URI: '__client_location_uri__',
    PROPERTY_CLIENT_APP_URI: '__client_app_uri__',
    PROPERTY_CLIENT_GRAPH_ID: '__client_graph_id__',
    PROPERTY_CLIENT_GRAPH_NAME: '__client_graph_name__',
    PROPERTY_CLIENT_CHANNEL_ID: '__channel_id__',
    PROPERTY_MESSAGE_PRIORITY: '__message_priority__',
    DATA_NAME_ECHO_DATA: 'echo_data',
} as const;