// WebSocket 消息基础接口
export interface Message {
    id: string; // 消息的唯一标识符
    type: MessageType; // 消息类型，使用枚举
    src_loc: Location; // 消息的源位置
    dest_locs: Location[]; // 消息的目的位置列表
    name?: string; // 消息名称 (可选，用于路由或语义)
    properties?: Record<string, any>; // 消息的附加属性 (可选)
    timestamp: number; // 时间戳
}

// 数据消息
export interface Data extends Message {
    type: MessageType.DATA;
    data?: Uint8Array; // 实际数据内容，对应Java的byte[] data
    content_type?: string;
    encoding?: string;
    is_eof?: boolean;
}

// 命令消息
export interface Command extends Message {
    type: MessageType; // 将类型改为 MessageType，允许子类精确指定
    name: string; // 命令名称，用于 Jackson 多态识别
    cmd_id: string; // Change type to string
    parent_cmd_id?: string;
}

// StartGraph 命令
export interface StartGraphCommand extends Command {
    type: MessageType.CMD_START_GRAPH; // 明确指定 type
    name: typeof CommandType.START_GRAPH; // 明确指定 name
    long_running_mode?: boolean;
    predefined_graph_name?: string;
    extension_groups_info?: any[]; // 根据 Java 定义，目前使用 any
    extensions_info?: any[]; // 根据 Java 定义，目前使用 any
    graph_json?: string; // 对应 Java 的 graphJsonDefinition
}

// StopGraph 命令
export interface StopGraphCommand extends Command {
    type: MessageType.CMD_STOP_GRAPH; // 明确指定 type
    name: typeof CommandType.STOP_GRAPH; // 明确指定 name
    location_uri?: string; // 停止特定 Engine 的 Location URI
}

// 命令结果消息
export interface CommandResult extends Message {
    type: MessageType.CMD_RESULT;
    cmd_id: string; // Change type to string
    success: boolean;
    error?: string;
    errorMessage?: string; // 添加 errorMessage 属性
    detail?: string;      // 添加 detail 属性
    data?: any; // 命令结果可能包含数据
    original_cmd_id?: string; // Add original_cmd_id
    original_cmd_name?: string; // Add original_cmd_name
}

// 音频帧消息
export interface AudioFrame extends Message {
    type: MessageType.AUDIO_FRAME;
    buf: Uint8Array; // 对应 buf
    sample_rate: number;
    number_of_channel: number; // 对应 numberOfChannel
    bits_per_sample: number; // 对应 bytesPerSample
    format: string; // 对应 dataFormat
    is_eof?: boolean;
    frame_timestamp: number; // 对应 frameTimestamp
    samples_per_channel?: number; // 对应 samplesPerChannel
    channel_layout?: number; // 对应 channelLayout
    line_size?: number; // 对应 lineSize
}

// 视频帧消息
export interface VideoFrame extends Message {
    type: MessageType.VIDEO_FRAME;
    data: Uint8Array; // 对应 data
    width: number;
    height: number;
    format: string; // 对应 pixelFormat
    is_eof?: boolean;
    frame_timestamp: number; // 对应 frameTimestamp
}

// 会话连接状态枚举
export enum SessionConnectionState {
    IDLE = 'idle',
    CONNECTING_SESSION = 'connecting_session',
    SESSION_ACTIVE = 'session_active',
    SESSION_FAILED = 'session_failed',
}

// WebSocket 连接状态枚举
export enum WebSocketConnectionState {
    CONNECTING = 'connecting',
    OPEN = 'open',
    CLOSING = 'closing',
    CLOSED = 'closed',
}

// 位置信息
export interface Location {
    app_uri: string;
    graph_id: string;
    extension_name?: string; // Change to optional
}

// 消息类型枚举
export enum MessageType {
    INVALID = 'INVALID',
    CMD = 'CMD',
    CMD_RESULT = 'CMD_RESULT',
    DATA = 'DATA',
    VIDEO_FRAME = 'VIDEO_FRAME',
    AUDIO_FRAME = 'AUDIO_FRAME',
    CMD_CLOSE_APP = 'CMD_CLOSE_APP',
    CMD_START_GRAPH = 'CMD_START_GRAPH',
    CMD_STOP_GRAPH = 'CMD_STOP_GRAPH',
    CMD_TIMER = 'CMD_TIMER',
    CMD_TIMEOUT = 'CMD_TIMEOUT',
}

// 命令类型枚举 - 匹配后端的 GraphEventCommandType (Keep as is, but START_GRAPH/STOP_GRAPH are also names in Java Command's JsonSubTypes)
export enum CommandType {
    START_GRAPH = 'CMD_START_GRAPH',
    STOP_GRAPH = 'CMD_STOP_GRAPH',
    ADD_EXTENSION_TO_GRAPH = '__add_extension_to_graph__', // This might need to be checked in Java too
    REMOVE_EXTENSION_FROM_GRAPH = '__remove_extension_from_graph__', // This might need to be checked in Java too
}

// 消息常量 - 匹配后端的 MessageConstants
export const MESSAGE_CONSTANTS = {
    NOT_APPLICABLE: 'N/A',
    SYS_EXTENSION_NAME: 'client_connection',
    PROPERTY_CLIENT_LOCATION_URI: '__client_location_uri__',
    PROPERTY_CLIENT_APP_URI: '__client_app_uri__',
    PROPERTY_CLIENT_GRAPH_ID: '__client_graph_id__',
    PROPERTY_CLIENT_GRAPH_NAME: '__client_graph_name__',
    PROPERTY_CLIENT_CHANNEL_ID: '__channel_id__',
    PROPERTY_MESSAGE_PRIORITY: '__message_priority__',
    DATA_NAME_ECHO_DATA: 'echo_data',
} as const;