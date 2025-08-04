import { useState, useEffect } from 'react';
import { MicrophoneAudioTrack } from "dingrtc";

export function useAudioTrack(audioTrack?: MicrophoneAudioTrack) {
    const [audioMute, setAudioMute] = useState(false);
    const [mediaStreamTrack, setMediaStreamTrack] = useState<MediaStreamTrack>();

    // 监听音频轨道变化
    useEffect(() => {
        if (audioTrack) {
            setMediaStreamTrack(audioTrack.getMediaStreamTrack());
        } else {
            setMediaStreamTrack(undefined);
        }
    }, [audioTrack]);

    // 监听静音状态变化
    useEffect(() => {
        if (audioTrack) {
            audioTrack.setMuted(audioMute);
        }
    }, [audioTrack, audioMute]);

    const toggleMute = () => {
        setAudioMute(!audioMute);
    };

    const updateMediaStreamTrack = () => {
        if (audioTrack) {
            const newMediaStreamTrack = audioTrack.getMediaStreamTrack();
            setMediaStreamTrack(newMediaStreamTrack);
        }
    };

    return {
        audioMute,
        mediaStreamTrack,
        toggleMute,
        updateMediaStreamTrack,
    };
}