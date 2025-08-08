export enum EMessageType {
    AGENT = "agent",
    USER = "user",
}

export enum EMessageDataType {
    TEXT = "text",
    REASON = "reason",
    IMAGE = "image",
}

export interface IChatItem {
    userId: number | string;
    userName?: string;
    text: string;
    data_type: EMessageDataType;
    type: EMessageType;
    isFinal?: boolean;
    time: number;
}