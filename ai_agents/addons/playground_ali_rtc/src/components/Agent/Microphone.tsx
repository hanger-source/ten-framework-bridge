"use client"

import * as React from "react"
import { useMultibandTrackVolume } from "@/common"
import AudioVisualizer from "@/components/Agent/AudioVisualizer"
import { MicrophoneAudioTrack } from "dingrtc";
import { Button } from "@/components/ui/button"
import { MicIconByStatus } from "@/components/Icon"
import { useAudioTrack } from "@/hooks/useAudioTrack"
import { useDeviceSelect, TDeviceSelectItem } from "@/hooks/useDeviceSelect"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export default function MicrophoneBlock(props: {
  audioTrack?: MicrophoneAudioTrack
}) {
  const { audioTrack } = props
  const { audioMute, mediaStreamTrack, toggleMute, updateMediaStreamTrack } = useAudioTrack(audioTrack)
  const { items, value, onChange } = useDeviceSelect(audioTrack)

  const subscribedVolumes = useMultibandTrackVolume(mediaStreamTrack, 20)

      return (
      <CommonDeviceWrapper
        title="麦克风"
        Icon={MicIconByStatus}
        onIconClick={toggleMute}
        isActive={!audioMute}
        select={<MicrophoneSelect
          audioTrack={audioTrack}
          onDeviceChange={updateMediaStreamTrack}
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
  const { items, value, onChange } = useDeviceSelect(audioTrack)

  const handleChange = async (newValue: string) => {
    await onChange(newValue);
    // 设备切换后，通知父组件更新 mediaStreamTrack
    setTimeout(() => {
      onDeviceChange?.();
    }, 100);
  };

  return <DeviceSelect items={items} value={value} onChange={handleChange} />;
};
