import 'pixi-live2d-display';

declare module 'pixi-live2d-display' {
  interface Live2DModel {
    speak?: (audio: string, options?: any) => void;
    stopSpeaking?: () => void;
  }
} 