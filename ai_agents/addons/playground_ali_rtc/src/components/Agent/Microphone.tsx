"use client"

import * as React from "react"
import { useMultibandTrackVolume } from "@/common"
import AudioVisualizer from "@/components/Agent/AudioVisualizer"
import { aliRtcManager, IAliUserTracks } from "@/manager";
import { MicrophoneAudioTrack } from "dingrtc";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { MicIconByStatus } from "@/components/Icon"

export default function MicrophoneBlock(props: {
  audioTrack?: MicrophoneAudioTrack
}) {
  const { audioTrack } = props
  const [audioMute, setAudioMute] = React.useState(false)
  const [mediaStreamTrack, setMediaStreamTrack] =
    React.useState<MediaStreamTrack>()

  React.useEffect(() => {
    if (audioTrack) {
      setMediaStreamTrack(audioTrack.getMediaStreamTrack())
    } else {
      setMediaStreamTrack(undefined)
    }
  }, [audioTrack])

  React.useEffect(() => {
    if (audioTrack) {
      audioTrack.setMuted(audioMute)
    }
  }, [audioTrack, audioMute])

  const subscribedVolumes = useMultibandTrackVolume(mediaStreamTrack, 20)



  const onClickMute = () => {
    setAudioMute(!audioMute)
  }

  return (
    <CommonDeviceWrapper
      title="麦克风"
      Icon={MicIconByStatus}
      onIconClick={onClickMute}
      isActive={!audioMute}
      select={<MicrophoneSelect
        audioTrack={audioTrack}
        onDeviceChange={() => {
          if (audioTrack) {
            const newMediaStreamTrack = audioTrack.getMediaStreamTrack();
            setMediaStreamTrack(newMediaStreamTrack);
          }
        }}
      />}
    >
      <div className="mt-3 flex h-12 flex-col items-center justify-center gap-2.5 self-stretch rounded-md border border-gray-200 bg-white p-2 shadow-sm">
        <AudioVisualizer
          type="user"
          barWidth={4}
          minBarHeight={2}
          maxBarHeight={20}
          frequencies={subscribedVolumes}
          borderRadius={2}
          gap={4}
        />
      </div>
    </CommonDeviceWrapper>
  )
}

export function CommonDeviceWrapper(props: {
  children: React.ReactNode
  title: string
  Icon: (
    props: React.SVGProps<SVGSVGElement> & { active?: boolean },
  ) => React.ReactNode
  onIconClick: () => void
  isActive: boolean
  select?: React.ReactNode
}) {
  const { title, Icon, onIconClick, isActive, select, children } = props

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between">
        <div className="text-sm font-medium">{title}</div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="border-secondary bg-transparent"
            onClick={onIconClick}
          >
            <Icon className="h-5 w-5" active={isActive} />
          </Button>
          {select}
        </div>
      </div>
      {children}
    </div>
  )
}

export type TDeviceSelectItem = {
  label: string
  value: string
  deviceId: string
}



export const DeviceSelect = (props: {
  items: TDeviceSelectItem[]
  value: string
  onChange: (value: string) => void
  placeholder?: string
}) => {
  const { items, value, onChange, placeholder } = props

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-[180px]">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {items.map((item) => (
          <SelectItem key={item.value} value={item.value}>
            {item.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export const MicrophoneSelect = (props: {
  audioTrack?: MicrophoneAudioTrack
  onDeviceChange?: () => void
}) => {
  const { audioTrack, onDeviceChange } = props
  const [items, setItems] = React.useState<TDeviceSelectItem[]>([])
  const [value, setValue] = React.useState("")

  // 组件挂载时获取设备列表
  React.useEffect(() => {

    // 获取系统默认设备ID
    const getSystemDefaultDevice = async () => {
      try {
        // 请求音频权限并获取当前使用的设备
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

    // 并行获取设备列表和系统默认设备
    Promise.all([
      aliRtcManager.getMicrophones(),
      getSystemDefaultDevice()
    ]).then(([arr, systemDefaultDeviceId]) => {


      // 过滤掉空设备ID的设备
      const filteredArr = arr.filter(item => item.deviceId && item.deviceId.trim() !== "");

      // 构建设备列表，避免重复
      const newItems: TDeviceSelectItem[] = [];
      const seenDeviceIds = new Set<string>();

      // 先添加所有设备，标记系统默认设备
      filteredArr.forEach((item) => {
        // 避免重复添加相同的设备ID
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
        // 找到系统默认设备并选中
        const defaultItem = newItems.find(item =>
          systemDefaultDeviceId && item.deviceId === systemDefaultDeviceId
        );
        setValue(defaultItem ? defaultItem.value : newItems[0].value);
      }
    }).catch((error) => {
      console.error("MicrophoneSelect: failed to get initial microphones", error);
    });
  }, []);

  // track 变化时设置选中项
  React.useEffect(() => {
    if (audioTrack && items.length > 0) {
      // 如果有设备列表，选中第一个设备（系统默认设备）
      setValue(items[0]?.value || "");
    }
  }, [audioTrack, items]);

  const onChange = async (value: string) => {
    setValue(value);
    if (audioTrack) {
      await audioTrack.setDevice(value);
      // 设备切换后，通知父组件更新 mediaStreamTrack
      setTimeout(() => {
        onDeviceChange?.();
      }, 100);
    }
  };

  return <DeviceSelect items={items} value={value} onChange={onChange} />;
};
