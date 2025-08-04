import { DingRTCClient } from "dingrtc";
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
            this.manager.emit("remoteUserChanged", {
                userId: user.userId,
                audioTrack: undefined,
                videoTrack: undefined,
            });
        });

        this.client.on("user-left", (user: { userId: string }) => {
            console.log("[Ali RTC] User left channel:", user);
        });

        this.client.on("user-published", async (user: { userId: string }, mediaType: string) => {
            await this.client.subscribe(user.userId, mediaType as 'audio' | 'video');
        });

        this.client.on("user-unpublished", async (user: { userId: string }, mediaType: string) => {
            await this.client.unsubscribe(user.userId, mediaType as 'audio' | 'video');
        });

        this.client.on("connection-state-change", (curState: string, prevState: string) => {
            console.log("[Ali RTC] Connection state changed:", prevState, "->", curState);

            // 添加连接状态变化的浮层提示
            if (curState === "CONNECTED" && prevState !== "CONNECTED") {
                toast.success("频道连接成功");
            } else if (curState === "DISCONNECTED" && prevState !== "DISCONNECTED") {
                toast.error("频道连接断开");
            } else if (curState === "CONNECTING") {
                toast.info("正在连接频道...");
            }
        });

        // 错误事件统一处理
        (this.client as DingRTCClient & { on: (event: string, handler: (error: DingRTCError) => void) => void }).on('error', (error: DingRTCError) => {
            const unifiedError = ErrorAdapter.adaptError(error);
            this.manager.emit('error', unifiedError);
        });
    }
}