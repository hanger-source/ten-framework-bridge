import { DingRTCClient } from "dingrtc";
import { AGEventEmitter } from "../events";
import { AliRtcEvents } from "./ali-types";

export class AliRtcNetworkMonitor {
    private manager: AGEventEmitter<AliRtcEvents>;
    private client: DingRTCClient;
    private isMonitoring: boolean = false;

    constructor(manager: AGEventEmitter<AliRtcEvents>, client: DingRTCClient) {
        this.manager = manager;
        this.client = client;
    }

    startNetworkQualityMonitoring() {
        if (this.isMonitoring) {
            return;
        }

        // 网络质量事件监听现在在 AliRtcEventHandler 中处理
        // 这里可以添加其他网络监控逻辑，如定期获取统计信息等

        this.isMonitoring = true;
    }

    stopNetworkQualityMonitoring() {
        if (!this.isMonitoring) {
            return;
        }

        // 网络质量事件监听在 AliRtcEventHandler 中处理
        // 这里可以清理其他网络监控资源

        this.isMonitoring = false;
    }

    getNetworkStats() {
        return {
            isMonitoring: this.isMonitoring,
            // 可以在这里返回更多网络统计信息
        };
    }
}