declare module "pixi-live2d-display" {
  export class Live2DModel {
    static from(url: string): Promise<Live2DModel>;
    x: number;
    y: number;
    anchor: { set(x: number, y: number): void };
    speak(audio: HTMLAudioElement): void;
    // ...可根据需要补充更多方法
  }
}
declare module "pixi-live2d-display-lipsyncpatch" {
  // 该模块为 lipsync patch，无需导出内容
}
