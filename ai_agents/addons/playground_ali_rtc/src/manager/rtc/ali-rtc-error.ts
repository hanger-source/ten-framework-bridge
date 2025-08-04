import { DingRTCError } from "@dingrtc/shared";

// 统一错误适配器（使用 SDK 官方类型）
export class ErrorAdapter {
    static adaptError(error: DingRTCError): { code: string; message: string; originalError: DingRTCError; rtcType: 'ali' } {
        const code = error?.code?.toString() || 'UNKNOWN_ERROR';
        return {
            code,
            message: error?.reason || '未知错误',
            originalError: error,
            rtcType: 'ali',
        };
    }
}