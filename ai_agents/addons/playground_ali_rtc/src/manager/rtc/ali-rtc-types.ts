// 视频源类型枚举
export enum VideoSourceType {
    CAMERA = "camera",
    SCREEN = "screen"
}

const TIMEOUT_MS = 5000; // Timeout for incomplete messages

export interface TextDataChunk {
    message_id: string;
    part_index: number;
    total_parts: number;
    content: string;
}

export { TIMEOUT_MS };