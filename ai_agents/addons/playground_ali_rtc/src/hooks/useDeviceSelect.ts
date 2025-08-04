import { useState, useEffect } from 'react';
import { MicrophoneAudioTrack } from "dingrtc";
import { aliRtcManager } from "@/manager";

export type TDeviceSelectItem = {
    label: string;
    value: string;
    deviceId: string;
};

export function useDeviceSelect(audioTrack?: MicrophoneAudioTrack) {
    const [items, setItems] = useState<TDeviceSelectItem[]>([]);
    const [value, setValue] = useState("");

    // 获取系统默认设备ID
    const getSystemDefaultDevice = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const audioTrack = stream.getAudioTracks()[0];
            const defaultDeviceId = audioTrack.getSettings().deviceId;

            // 停止stream
            stream.getTracks().forEach(track => track.stop());

            return defaultDeviceId;
        } catch (error) {
            console.warn("Failed to get system default device:", error);
            return null;
        }
    };

    // 初始化设备列表
    useEffect(() => {
        Promise.all([
            aliRtcManager.getMicrophones(),
            getSystemDefaultDevice()
        ]).then(([arr, systemDefaultDeviceId]) => {
            // 过滤掉空设备ID的设备
            const filteredArr = arr.filter(item => item.deviceId && item.deviceId.trim() !== "");

            // 构建设备列表，避免重复
            const newItems: TDeviceSelectItem[] = [];
            const seenDeviceIds = new Set<string>();

            filteredArr.forEach((item) => {
                if (!seenDeviceIds.has(item.deviceId)) {
                    seenDeviceIds.add(item.deviceId);
                    newItems.push({
                        value: item.deviceId,
                        label: item.label,
                        deviceId: item.deviceId,
                    });
                }
            });

            setItems(newItems);

            // 设置默认选中项为系统默认设备
            if (newItems.length > 0) {
                const defaultItem = newItems.find(item =>
                    systemDefaultDeviceId && item.deviceId === systemDefaultDeviceId
                );
                setValue(defaultItem ? defaultItem.value : newItems[0].value);
            }
        }).catch((error) => {
            console.error("useDeviceSelect: failed to get initial microphones", error);
        });
    }, []);

    // track 变化时设置选中项
    useEffect(() => {
        if (audioTrack && items.length > 0) {
            setValue(items[0]?.value || "");
        }
    }, [audioTrack, items]);

    const onChange = async (newValue: string) => {
        setValue(newValue);
        if (audioTrack) {
            await audioTrack.setDevice(newValue);
        }
    };

    return {
        items,
        value,
        onChange,
    };
}