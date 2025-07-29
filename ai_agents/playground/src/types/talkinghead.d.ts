declare module "@met4citizen/talkinghead" {
  export class TalkingHead {
    constructor(node: HTMLElement, opt?: any);
    showAvatar(avatar: any, onprogress?: (url: string, event: any) => void): Promise<void>;
    speakAudio(audio: MediaStream | any, opt?: any, onsubtitles?: any): void;
    speakText(text: string, opt?: any, onsubtitles?: any, excludes?: any): void;
    setMood(mood: string): void;
    setView(view: string, opt?: any): void;
    dispose?(): void;
    // ...可根据需要补充更多方法
  }
} 