"use client";

import DingRTC, {
    DingRTCClient,
    CameraVideoTrack,
    MicrophoneAudioTrack,
    LocalVideoTrack,
    RemoteVideoTrack,
    RemoteAudioTrack,
} from "dingrtc";
import RTM from "@dingrtc/rtm";
import { IAgentSettings } from "@/types";
import { AGEventEmitter } from "../events";
import { AliRtcEvents, IAliUserTracks } from "./ali-types";
import { apiGenAliData } from "@/common";
import { toast } from "sonner";

// 导入拆分的模块
import { VideoSourceType } from "./ali-rtc-types";
import { AliRtcInterceptor } from "./ali-rtc-interceptor";
import { AliRtcMessageHandler } from "./ali-rtc-message";
import { AliRtcDeviceManager, DeviceManagerConfig } from "./ali-rtc-devices";
import { AliRtcEventHandler } from "./ali-rtc-events";
import { AliRtcNetworkMonitor } from "./ali-rtc-network";

export class AliRtcManager extends AGEventEmitter<AliRtcEvents> {
    private _joined: boolean;
    client: DingRTCClient;
    rtm: RTM;
    appId: string | null = null;
    token: string | null = null;
    userId: string | null = null;

    // 模块实例
    private messageHandler: AliRtcMessageHandler;
    private deviceManager: AliRtcDeviceManager;
    private eventHandler: AliRtcEventHandler;
    private networkMonitor: AliRtcNetworkMonitor;

    constructor() {
        super();
        this._joined = false;
        this.client = DingRTC.createClient();
        this.rtm = new RTM({});

        // 初始化模块
        this.messageHandler = new AliRtcMessageHandler();
        this.deviceManager = new AliRtcDeviceManager({
            enableAudio: true,
            enableVideo: true,
            enableScreenShare: true
        });
        this.eventHandler = new AliRtcEventHandler(this, this.client);
        this.networkMonitor = new AliRtcNetworkMonitor(this, this.client);

        // 设置事件监听
        this.eventHandler.setupEventListeners();

        // 拦截 Ali RTC 的日志上报请求，避免 ERR_BLOCKED_BY_CLIENT 错误
        AliRtcInterceptor.interceptLogRequests();
    }

    async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: IAgentSettings }) {
        // 如果已经连接，先断开连接
        if (this._joined) {
            try {
                await this.client.leave();
                this._joined = false;
                toast.success("已退出频道");
            } catch (error) {
                console.warn("断开连接时出错:", error);
                toast.error("退出频道失败");
            }
        }

        if (!this._joined) {
            let appId: string;
            let finalToken: string;

            if (agentSettings?.token) {
                // 使用提供的临时token，不需要调用generate API
                // 从 agentSettings 中获取 appId，如果没有则使用环境变量
                appId = agentSettings?.env?.ALI_APP_ID || import.meta.env.VITE_ALI_APP_ID || '';
                finalToken = agentSettings.token;
            } else {
                // 没有token，调用generate API
                const data = await apiGenAliData({
                    userId: userId,
                    channel: channel,
                    userName: `user_${userId}`
                });
                const { code, data: tokenData } = data;
                if (code !== "0") {
                    throw new Error("Failed to get Ali RTC token");
                }
                appId = tokenData.appId;
                finalToken = tokenData.token;
            }

            // 验证 App ID 不为空
            if (!appId || appId.trim() === '') {
                toast.error("阿里云 App ID 不能为空，请在设置中配置阿里云 App ID");
                throw new Error("阿里云 App ID 不能为空，请在设置中配置阿里云 App ID");
            }

            this.appId = appId;
            this.token = finalToken;
            this.userId = String(userId);

            // 注册RTM到RTC客户端
            try {
                this.client.register(this.rtm);
                console.log("RTM registered successfully");
            } catch (error) {
                console.warn("RTM registration failed:", error);
            }

            // 加入频道 - 阿里云RTC的join方法参数结构不同
            console.log("准备加入频道:", {
                appId: appId,
                token: finalToken.substring(0, 50) + "...",
                uid: String(userId),
                channel: channel,
                userName: `user_${userId}`,
            });

            try {
                const joinResult = await this.client?.join({
                    appId: appId,
                    token: finalToken,
                    uid: String(userId),
                    channel: channel,
                    userName: `user_${userId}`,
                });
                console.log("✅ 成功加入频道", {
                    channel,
                    userId,
                    appId: appId.substring(0, 20) + "...",
                    token: finalToken.substring(0, 50) + "...",
                    tokenLength: finalToken.length,
                    existingRemoteUsers: joinResult?.remoteUsers?.length || 0,
                });
                this._joined = true;
                toast.success("成功加入频道");

                // 启动网络质量监控
                this.networkMonitor.startNetworkQualityMonitoring();

                // 通知已有的远程用户
                if (joinResult?.remoteUsers) {
                    joinResult.remoteUsers.forEach(user => {
                        console.log("[Ali RTC] Found existing remote user:", {
                            userId: user.userId,
                            hasAudio: !!user.audioTrack,
                            hasVideo: !!user.videoTrack,
                            audioTrackType: user.audioTrack?.constructor.name,
                            videoTrackType: user.videoTrack?.constructor.name
                        });

                        this.emit("remoteUserChanged",
                            user.userId,
                            user.userName,
                            user.audioTrack,
                            user.videoTrack
                        );
                    });
                }
            } catch (error: any) {
                console.error("加入频道失败:", error);
                console.error("错误详情:", {
                    appId,
                    tokenLength: finalToken.length,
                    uid: String(userId),
                    channel,
                    userName: `user_${userId}`,
                    errorMessage: error?.message || 'Unknown error',
                    errorStack: error?.stack || 'No stack trace'
                });
                toast.error("加入频道失败");
                throw error;
            }
        }
    }

    // 设备管理方法 - 委托给设备管理器
    async createCameraTracks() {
        const track = await this.deviceManager.createCameraTracks();
        this.emit("localTracksChanged", this.deviceManager.getLocalTracks());
        return track;
    }

    async createMicrophoneAudioTrack() {
        const track = await this.deviceManager.createMicrophoneAudioTrack();
        this.emit("localTracksChanged", this.deviceManager.getLocalTracks());
        return track;
    }

    async createScreenShareTrack() {
        const track = await this.deviceManager.createScreenShareTrack();
        this.emit("localTracksChanged", this.deviceManager.getLocalTracks());
        return track;
    }

    async switchVideoSource(type: VideoSourceType) {
        const success = await this.deviceManager.switchVideoSource(type, this.client);
        if (success) {
            this.emit("localTracksChanged", this.deviceManager.getLocalTracks());
        }
        return success;
    }

    async publish() {
        const localTracks = this.deviceManager.getLocalTracks();
        const tracks = [localTracks.videoTrack, localTracks.audioTrack].filter(Boolean) as (CameraVideoTrack | MicrophoneAudioTrack)[];

        if (tracks.length > 0) {
            await this.client.publish(tracks);
        } else {
            console.warn("[rtc] No tracks to publish");
        }
    }

    async destroy() {
        try {
            // 停止网络监控
            this.networkMonitor.stopNetworkQualityMonitoring();

            // 关闭所有设备轨道（除了音频轨道）
            this.deviceManager.closeAllTracks();

            // 触发轨道变化事件，通知组件轨道状态变化
            this.emit("localTracksChanged", this.deviceManager.getLocalTracks());

            await this.client.leave();
            this._joined = false;
            this._resetData();
            toast.success("已退出频道（摄像头和音频轨道保持活跃）");
        } catch (err) {
            console.error("Failed to destroy RTC manager", err);
            toast.error("退出频道失败");
        }
    }

    handleChunk(formattedChunk: string) {
        const textItem = this.messageHandler.handleChunk(formattedChunk, this.userId);
        if (textItem) {
            this.emit("textChanged", textItem);
        }
    }

    _playAudio(
        audioTrack: MicrophoneAudioTrack | RemoteAudioTrack | undefined
    ) {
        if (audioTrack) {
            audioTrack.play();
        }
    }

    private _resetData() {
        this.appId = null;
        this.token = null;
        this.userId = null;
        this.messageHandler.clearCache();
        // 不清除音频轨道，保持音轨图工作
    }

    // 设备管理方法 - 委托给设备管理器
    async getCameras() {
        return await AliRtcDeviceManager.getCameras();
    }

    async getMicrophones() {
        return await AliRtcDeviceManager.getMicrophones();
    }

    async getSpeakers() {
        return await AliRtcDeviceManager.getSpeakers();
    }

    async getAllDevices() {
        return await AliRtcDeviceManager.getAllDevices();
    }

    async checkDevicePermissions() {
        return await this.deviceManager.checkDevicePermissions();
    }

    getTrackStatus() {
        return this.deviceManager.getTrackStatus();
    }

    updateDeviceConfig(config: Partial<DeviceManagerConfig>) {
        this.deviceManager.updateConfig(config);
    }

    getLocalTracks(): IAliUserTracks {
        return this.deviceManager.getLocalTracks();
    }

    // 获取所有远程用户
    getRemoteUsers() {
        return this.client.remoteUsers;
    }


}

// 懒加载单例模式，只在客户端执行
let _aliRtcManager: AliRtcManager | null = null;

export const aliRtcManager = (() => {
    if (typeof window === 'undefined') {
        // 服务端渲染时返回一个空对象，避免初始化
        return {} as AliRtcManager;
    }

    if (!_aliRtcManager) {
        _aliRtcManager = new AliRtcManager();
    }

    return _aliRtcManager;
})();