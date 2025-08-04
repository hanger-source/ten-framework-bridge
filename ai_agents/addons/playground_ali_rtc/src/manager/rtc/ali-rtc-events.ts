import { DingRTCClient, NetworkQuality } from "dingrtc";
import { DingRTCError } from "@dingrtc/shared";
import { AGEventEmitter } from "../events";
import { AliRtcEvents } from "./ali-types";
import { ErrorAdapter } from "./ali-rtc-error";
import { toast } from "sonner";

export class AliRtcEventHandler {
    private manager: AGEventEmitter<AliRtcEvents>;
    private client: DingRTCClient;

    constructor(manager: AGEventEmitter<AliRtcEvents>, client: DingRTCClient) {
        this.manager = manager;
        this.client = client;
    }

    setupEventListeners() {
        this.client.on("user-joined", (user: { userId: string }) => {
            console.log("[Ali RTC] User joined channel:", user);
            this.manager.emit("remoteUserChanged", user.userId, undefined, undefined, undefined);
        });

        this.client.on("user-left", (user: { userId: string }) => {
            console.log("[Ali RTC] User left channel:", user);
            // 发送用户离开事件
            this.manager.emit("remoteUserLeft", user.userId);
        });

        this.client.on("user-published", async (user: { userId: string }, mediaType: string) => {
            console.log("[Ali RTC] User published:", {
                user: user,
                userId: user.userId,
                mediaType: mediaType,
                userType: typeof user,
                userKeys: Object.keys(user)
            });

            // 自动订阅用户的音视频轨道
            try {
                await this.client.subscribe(user.userId, mediaType as 'audio' | 'video');
                console.log("[Ali RTC] Successfully subscribed to user:", user.userId, mediaType);

                // 获取订阅后的完整轨道信息
                const remoteUser = this.client.remoteUsers.find(u => u.userId === user.userId);
                if (remoteUser) {
                    console.log("[Ali RTC] Found remote user:", {
                        userId: remoteUser.userId,
                        hasAudio: !!remoteUser.audioTrack,
                        hasVideo: !!remoteUser.videoTrack,
                        audioTrackType: remoteUser.audioTrack?.constructor.name,
                        videoTrackType: remoteUser.videoTrack?.constructor.name
                    });

                    // 发送远程用户变化事件，传递完整的用户信息
                    this.manager.emit("remoteUserChanged",
                        remoteUser.userId,
                        remoteUser.userName,
                        remoteUser.audioTrack,
                        remoteUser.videoTrack
                    );
                } else {
                    console.warn("[Ali RTC] Remote user not found after subscription:", user.userId);
                }
            } catch (error) {
                console.error("[Ali RTC] Failed to subscribe to user:", user.userId, mediaType, error);
            }
        });

        this.client.on("user-unpublished", async (user: { userId: string }, mediaType: string) => {
            await this.client.unsubscribe(user.userId, mediaType as 'audio' | 'video');
        });

        this.client.on("connection-state-change", (curState: string, prevState: string) => {
            console.log("[Ali RTC] Connection state changed:", prevState, "->", curState);

            // 添加连接状态变化的浮层提示
            if (curState === "connected" && prevState !== "connected") {
                toast.success("频道连接成功");
            } else if (curState === "disconnected" && prevState !== "disconnected") {
                toast.error("频道连接断开");
            } else if (curState === "connecting") {
                toast.info("正在连接频道...");
            }

            // 发送连接状态变化事件
            this.manager.emit("connectionStateChanged", curState);
        });

        // 监听 DingRTC 网络质量事件
        this.client.on("network-quality", (quality: NetworkQuality) => {
            const qualityText = this.getNetworkQualityText(quality);
            console.log(`[Ali RTC] Network quality: ${quality} (${qualityText})`);
            this.manager.emit("networkQuality", {
                uplinkQuality: quality,
                downlinkQuality: quality,
            });
        });

        // 错误事件统一处理 - 直接处理，不通过 manager 发射
        (this.client as DingRTCClient & { on: (event: string, handler: (error: DingRTCError) => void) => void }).on('error', (error: DingRTCError) => {
            console.error("[Ali RTC] Client error:", error);
            const errorInfo = ErrorAdapter.adaptError(error);
            toast.error(`RTC错误: ${errorInfo.message}`);
        });
    }

    private getNetworkQualityText(quality: NetworkQuality): string {
        switch (quality) {
            case 0: return "UNKNOWN";
            case 1: return "EXCELLENT";
            case 2: return "GOOD";
            case 3: return "NORMAL";
            case 4: return "POOR";
            case 5: return "BAD";
            case 6: return "DISCONNECTED";
            default: return "UNKNOWN";
        }
    }
}