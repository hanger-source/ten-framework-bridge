export type Language = "en-US" | "zh-CN" | "ja-JP" | "ko-KR";
export type VoiceType = "male" | "female";

export interface ColorItem {
  active: string;
  default: string;
}

export interface IOptions {
  channel: string;
  userName: string;
  userId: number;
}

export interface IAgentEnv {
  GREETING?: string;
  CHAT_PROMPT?: string;
  [key: string]: string | undefined;
}

export interface IAgentSettings {
  greeting: string;
  prompt: string;
  env: Record<string, string>;
  echoCancellation: boolean;
  noiseSuppression: boolean;
  autoGainControl: boolean;
}

export interface ITrulienceSettings {
  enabled: boolean;
  avatarToken: string;
  avatarId: string;
  avatarDesktopLargeWindow: boolean;
  animationURL: string;
  trulienceSDK: string;
}

export enum EMessageType {
  AGENT = "agent",
  USER = "user",
}

export enum EMessageDataType {
  TEXT = "text",
  REASON = "reason",
  IMAGE = "image",
  OTHER = "other",
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

export interface GraphOptionItem {
  label: string;
  value: string;
}

export interface LanguageOptionItem {
  label: string;
  value: Language;
}

export interface VoiceOptionItem {
  label: string;
  value: VoiceType;
}

export interface OptionType {
  value: string;
  label: string;
}

export interface IPdfData {
  fileName: string;
  collection: string;
}
