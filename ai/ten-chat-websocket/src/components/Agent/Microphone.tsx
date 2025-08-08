"use client";

import * as React from "react";
import { Button } from "@/components/ui/button";
import { MicIconByStatus } from "@/components/Icon";

// 聪明的开发杭二: 尝试触发 linter 重新评估
export default function MicrophoneBlock(props: {
  sendAudioFrame: (audioData: Uint8Array) => void;
}) {
  const { sendAudioFrame } = props;
  const [audioMute, setAudioMute] = React.useState(false);
  const [mediaStreamTrack, setMediaStreamTrack] =
    React.useState<MediaStreamTrack | null>(null);
  const [audioContext, setAudioContext] = React.useState<AudioContext | null>(
    null,
  );
  const [microphone, setMicrophone] =
    React.useState<MediaStreamAudioSourceNode | null>(null);
  const [scriptProcessor, setScriptProcessor] =
    React.useState<ScriptProcessorNode | null>(null);

  React.useEffect(() => {
    if (!audioMute) {
      startMicrophone();
    } else {
      stopMicrophone();
    }

    return () => {
      stopMicrophone();
    };
  }, [audioMute]);

  const startMicrophone = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const track = stream.getAudioTracks()[0];
      setMediaStreamTrack(track);

      const context = new (window.AudioContext ||
        (window as any).webkitAudioContext)(); // 聪明的开发杭二: 修正 window.webkitAudioContext 的类型转换错误
      setAudioContext(context);

      const source = context.createMediaStreamSource(stream);
      setMicrophone(source);

      // Create a ScriptProcessorNode to process audio data
      const processor = context.createScriptProcessor(2048, 1, 1); // bufferSize, inputChannels, outputChannels
      setScriptProcessor(processor);

      processor.onaudioprocess = (event) => {
        if (!audioMute) {
          const inputBuffer = event.inputBuffer.getChannelData(0);
          const pcmData = new Int16Array(inputBuffer.length);
          for (let i = 0; i < inputBuffer.length; i++) {
            // Convert float to 16-bit PCM
            pcmData[i] = Math.max(-1, Math.min(1, inputBuffer[i])) * 0x7fff;
          }
          sendAudioFrame(new Uint8Array(pcmData.buffer));
        }
      };

      source.connect(processor);
      processor.connect(context.destination);
    } catch (error) {
      console.error("Error accessing microphone:", error);
      setAudioMute(true); // 如果出错，静音麦克风
    }
  };

  const stopMicrophone = () => {
    if (microphone) {
      microphone.disconnect();
      setMicrophone(null);
    }
    if (scriptProcessor) {
      scriptProcessor.disconnect();
      setScriptProcessor(null);
    }
    if (audioContext) {
      audioContext.close();
      setAudioContext(null);
    }
    if (mediaStreamTrack) {
      mediaStreamTrack.stop();
      setMediaStreamTrack(null);
    }
  };

  const onClickMute = () => {
    setAudioMute(!audioMute);
  };

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium">麦克风</div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="border-secondary bg-transparent"
            onClick={onClickMute}
          >
            <MicIconByStatus className="h-5 w-5" active={!audioMute} />
          </Button>
        </div>
      </div>
    </div>
  );
}
