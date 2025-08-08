declare module "@met4citizen/talkinghead" {
  export class TalkingHead {
    constructor(node: HTMLElement, opt?: unknown);
    showAvatar(
      avatar: unknown,
      onprogress?: (url: string, event: unknown) => void,
    ): Promise<void>;
    speakAudio(audio: MediaStream | unknown, opt?: unknown, onsubtitles?: unknown): void;
    speakText(text: string, opt?: unknown, onsubtitles?: unknown, excludes?: unknown): void;
    setMood(mood: string): void;
    setView(view: string, opt?: unknown): void;
    dispose?(): void;
    // ...可根据需要补充更多方法
  }
}
