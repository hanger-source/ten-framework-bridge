import { DingRTCClient } from "dingrtc";
import {
    NetworkQuality,
    LocalAudioStates,
    LocalVideoStates,
    RemoteAudioStates,
    RemoteVideoStates,
} from "dingrtc";
import { AGEventEmitter } from "../events";
import { AliRtcEvents } from "./ali-types";

export class AliRtcNetworkMonitor {
    private manager: AGEventEmitter<AliRtcEvents>;
    private client: DingRTCClient;
    private networkQualityTimer: ReturnType<typeof setInterval> | null = null;

    constructor(manager: AGEventEmitter<AliRtcEvents>, client: DingRTCClient) {
        this.manager = manager;
        this.client = client;
    }

    startNetworkQualityMonitoring() {
        if (this.networkQualityTimer) {
            clearInterval(this.networkQualityTimer);
        }

        this.networkQualityTimer = setInterval(async () => {
            try {
                const localAudio: LocalAudioStates | undefined = await this.client.getLocalAudioStats?.();
                const localVideoMap = await this.client.getLocalVideoStats?.();
                const localVideo: LocalVideoStates | undefined = localVideoMap?.camera;
                const remoteAudioMap = await this.client.getRemoteAudioStats?.();
                const remoteVideoMap = await this.client.getRemoteVideoStats?.();
                const remoteAudios: RemoteAudioStates[] = remoteAudioMap ? Object.values(remoteAudioMap) : [];
                const remoteVideos: RemoteVideoStates[] = remoteVideoMap ? Object.values(remoteVideoMap).flatMap(v => [v.camera, v.auxiliary].filter((vv): vv is RemoteVideoStates => !!vv)) : [];

                // 这里只做简单聚合，实际可根据业务需求细化
                const uplinkQuality = localAudio?.rtt || 0;
                const downlinkQuality = remoteAudios[0]?.rtt || 0;

                // 这里的 networkQuality 可根据 rtt/丢包等映射 NetworkQuality
                this.manager.emit('networkQuality', {
                    uplinkQuality,
                    downlinkQuality,
                    localAudio,
                    localVideo,
                    remoteAudios,
                    remoteVideos,
                });
            } catch (err) {
                // 忽略统计异常
                console.warn("Network quality monitoring error:", err);
            }
        }, 2000);
    }

    stopNetworkQualityMonitoring() {
        if (this.networkQualityTimer) {
            clearInterval(this.networkQualityTimer);
            this.networkQualityTimer = null;
        }
    }

    getNetworkStats() {
        return {
            hasTimer: this.networkQualityTimer !== null,
            interval: 2000
        };
    }
}