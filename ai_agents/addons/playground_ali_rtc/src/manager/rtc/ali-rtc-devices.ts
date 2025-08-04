import DingRTC, {
    CameraVideoTrack,
    MicrophoneAudioTrack,
    LocalVideoTrack,
} from "dingrtc";
import { IAliUserTracks } from "./ali-types";
import { VideoSourceType } from "./ali-rtc-types";
import { toast } from "sonner";

export interface DeviceInfo {
    deviceId: string;
    label: string;
    kind: string;
}

export interface DeviceManagerConfig {
    enableAudio: boolean;
    enableVideo: boolean;
    enableScreenShare: boolean;
}

export class AliRtcDeviceManager {
    private localTracks: IAliUserTracks = {};
    private deviceConfig: DeviceManagerConfig;

    constructor(config: DeviceManagerConfig) {
        this.deviceConfig = config;
    }

    // 设备枚举方法
    static async getCameras(): Promise<DeviceInfo[]> {
        try {
            // 先请求摄像头权限，这样可以获取到所有设备
            try {
                await navigator.mediaDevices.getUserMedia({ video: true });
            } catch (permissionError) {
                console.warn("Camera permission not granted, may only show default device");
            }

            const devices = await navigator.mediaDevices.enumerateDevices();
            const cameras = devices
                .filter(device => device.kind === 'videoinput' && device.deviceId && device.deviceId.trim() !== '')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Camera ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));

            console.log("Found cameras:", cameras);
            return cameras;
        } catch (error) {
            console.error("Failed to get cameras:", error);
            toast.error("获取摄像头列表失败");
            return [];
        }
    }

    static async getMicrophones(): Promise<DeviceInfo[]> {
        try {

            // 先请求麦克风权限，这样可以获取到所有设备
            let stream: MediaStream | null = null;
            try {
                stream = await navigator.mediaDevices.getUserMedia({ audio: true });

            } catch (permissionError) {
                console.warn("Microphone permission not granted, may only show default device");
            }

            const devices = await navigator.mediaDevices.enumerateDevices();


            const microphones = devices
                .filter(device => device.kind === 'audioinput' && device.deviceId && device.deviceId.trim() !== '')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Microphone ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));



            // 清理stream
            if (stream) {
                stream.getTracks().forEach(track => track.stop());
            }

            return microphones;
        } catch (error) {
            console.error("Failed to get microphones:", error);
            toast.error("获取麦克风列表失败");
            return [];
        }
    }

    static async getSpeakers(): Promise<DeviceInfo[]> {
        try {
            const devices = await navigator.mediaDevices.enumerateDevices();
            const speakers = devices
                .filter(device => device.kind === 'audiooutput' && device.deviceId && device.deviceId.trim() !== '')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Speaker ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));

            console.log("Found speakers:", speakers);
            return speakers;
        } catch (error) {
            console.error("Failed to get speakers:", error);
            toast.error("获取扬声器列表失败");
            return [];
        }
    }

    // 获取所有设备（包含权限请求）
    static async getAllDevices(): Promise<{
        cameras: DeviceInfo[];
        microphones: DeviceInfo[];
        speakers: DeviceInfo[];
    }> {
        try {
            // 请求音频和视频权限以获取完整设备列表
            try {
                await navigator.mediaDevices.getUserMedia({
                    audio: true,
                    video: true
                });
            } catch (permissionError) {
                console.warn("Device permissions not granted, may only show default devices");
            }

            const devices = await navigator.mediaDevices.enumerateDevices();

            const cameras = devices
                .filter(device => device.kind === 'videoinput' && device.deviceId && device.deviceId.trim() !== '')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Camera ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));

            const microphones = devices
                .filter(device => device.kind === 'audioinput' && device.deviceId && device.deviceId.trim() !== '')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Microphone ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));

            const speakers = devices
                .filter(device => device.kind === 'audiooutput')
                .map(device => ({
                    deviceId: device.deviceId,
                    label: device.label || `Speaker ${device.deviceId.slice(0, 8)}`,
                    kind: device.kind
                }));

            console.log("All devices found:", { cameras, microphones, speakers });

            return { cameras, microphones, speakers };
        } catch (error) {
            console.error("Failed to get all devices:", error);
            toast.error("获取设备列表失败");
            return { cameras: [], microphones: [], speakers: [] };
        }
    }

    // 创建设备轨道
    async createCameraTracks(): Promise<CameraVideoTrack | null> {
        try {
            const videoTrack = await DingRTC.createCameraVideoTrack({});
            this.localTracks.videoTrack = videoTrack;
            toast.success("摄像头轨道创建成功");
            return videoTrack;
        } catch (err) {
            console.error("Failed to create video track", err);
            toast.error("摄像头轨道创建失败");
            return null;
        }
    }

    async createMicrophoneAudioTrack(): Promise<MicrophoneAudioTrack | null> {
        try {
            const audioTrack = await DingRTC.createMicrophoneAudioTrack();
            this.localTracks.audioTrack = audioTrack;
            toast.success("麦克风轨道创建成功");
            return audioTrack;
        } catch (err) {
            console.error("Failed to create audio track", err);
            toast.error("麦克风轨道创建失败");
            return null;
        }
    }

    async createScreenShareTrack(): Promise<LocalVideoTrack | null> {
        try {
            const screenTracks = await DingRTC.createScreenVideoTrack({});
            // 阿里云RTC的createScreenVideoTrack返回数组，第一个是视频轨道
            if (screenTracks.length > 0) {
                this.localTracks.screenTrack = screenTracks[0] as LocalVideoTrack;
                toast.success("屏幕共享轨道创建成功");
                return screenTracks[0] as LocalVideoTrack;
            }
            return null;
        } catch (err) {
            console.error("Failed to create screen share track", err);
            toast.error("屏幕共享轨道创建失败");
            return null;
        }
    }

    // 切换视频源
    async switchVideoSource(type: VideoSourceType, client: any): Promise<boolean> {
        try {
            if (type === VideoSourceType.SCREEN) {
                const screenTrack = await this.createScreenShareTrack();
                if (screenTrack) {
                    // 取消发布摄像头轨道
                    if (this.localTracks.videoTrack) {
                        await client.unpublish(this.localTracks.videoTrack);
                        this.localTracks.videoTrack.close();
                        this.localTracks.videoTrack = undefined;
                    }
                    // 发布屏幕共享轨道
                    await client.publish(screenTrack);
                    toast.success("已切换到屏幕共享");
                    return true;
                }
            } else if (type === VideoSourceType.CAMERA) {
                const videoTrack = await this.createCameraTracks();
                if (videoTrack) {
                    // 取消发布屏幕共享轨道
                    if (this.localTracks.screenTrack) {
                        await client.unpublish(this.localTracks.screenTrack);
                        this.localTracks.screenTrack.close();
                        this.localTracks.screenTrack = undefined;
                    }
                    // 发布摄像头轨道
                    await client.publish(videoTrack);
                    toast.success("已切换到摄像头");
                    return true;
                }
            }
            return false;
        } catch (err) {
            console.error("Failed to switch video source", err);
            toast.error("切换视频源失败");
            return false;
        }
    }

    // 设备权限检查
    async checkDevicePermissions(): Promise<{
        camera: boolean;
        microphone: boolean;
        screen: boolean;
    }> {
        const permissions = {
            camera: false,
            microphone: false,
            screen: false
        };

        try {
            // 检查摄像头权限
            const videoStream = await navigator.mediaDevices.getUserMedia({ video: true });
            permissions.camera = true;
            videoStream.getTracks().forEach(track => track.stop());
        } catch (error) {
            console.warn("Camera permission denied");
        }

        try {
            // 检查麦克风权限
            const audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
            permissions.microphone = true;
            audioStream.getTracks().forEach(track => track.stop());
        } catch (error) {
            console.warn("Microphone permission denied");
        }

        // 屏幕共享权限通常在用户操作时检查
        permissions.screen = true; // 默认假设可用

        return permissions;
    }

    // 获取当前轨道状态
    getTrackStatus(): {
        hasVideo: boolean;
        hasAudio: boolean;
        hasScreen: boolean;
    } {
        return {
            hasVideo: !!this.localTracks.videoTrack,
            hasAudio: !!this.localTracks.audioTrack,
            hasScreen: !!this.localTracks.screenTrack
        };
    }

    // 关闭所有轨道
    closeAllTracks(): void {
        // 不关闭摄像头轨道，保持预览框工作
        // if (this.localTracks.videoTrack) {
        //     this.localTracks.videoTrack.close();
        //     this.localTracks.videoTrack = undefined;
        // }

        // 不关闭音频轨道，保持音轨图工作
        // if (this.localTracks.audioTrack) {
        //     this.localTracks.audioTrack.close();
        //     this.localTracks.audioTrack = undefined;
        // }

        // 只关闭屏幕共享轨道
        if (this.localTracks.screenTrack) {
            this.localTracks.screenTrack.close();
            this.localTracks.screenTrack = undefined;
        }

        console.log("设备轨道已关闭（摄像头和音频轨道保持活跃）");
    }

    // 获取本地轨道
    getLocalTracks(): IAliUserTracks {
        return this.localTracks;
    }

    // 设置设备配置
    updateConfig(config: Partial<DeviceManagerConfig>): void {
        this.deviceConfig = { ...this.deviceConfig, ...config };
    }
}