"use client"

import * as React from "react"
// import CamSelect from "./camSelect"
import { CamIconByStatus } from "@/components/Icon"
// import AgoraRTC, { ICameraVideoTrack, ILocalVideoTrack } from "agora-rtc-sdk-ng"
// import { LocalStreamPlayer } from "../streamPlayer"
// import { useSmallScreen } from "@/common"
import {
  DeviceSelect,
} from "@/components/Agent/Microphone"
import { Button } from "@/components/ui/button"
import { LocalStreamPlayer } from "@/components/Agent/StreamPlayer"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../ui/select"
import { VIDEO_SOURCE_OPTIONS, VideoSourceType } from "@/common"
import { MonitorIcon, MonitorXIcon } from "lucide-react"
import { aliRtcManager, IAliUserTracks } from "@/manager";
import { CameraVideoTrack, LocalVideoTrack } from "dingrtc";


export const ScreenIconByStatus = (
  props: React.SVGProps<SVGSVGElement> & { active?: boolean; color?: string },
) => {
  const { active, color, ...rest } = props
  if (active) {
    return <MonitorIcon color={color || "#3D53F5"} {...rest} />
  }
  return <MonitorXIcon color={color || "#667085"} {...rest} />
}

export function VideoDeviceWrapper(props: {
  children: React.ReactNode
  title: string
  Icon: (
    props: React.SVGProps<SVGSVGElement> & { active?: boolean },
  ) => React.ReactNode
  onIconClick: () => void
  videoSourceType: VideoSourceType
  onVideoSourceChange: (value: VideoSourceType) => void
  isActive: boolean
  select?: React.ReactNode
}) {
  const { Icon, onIconClick, isActive, select, children, onVideoSourceChange, videoSourceType } = props

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="text-sm font-medium">{props.title}</div>
          <div className="w-[150px]">
            <Select value={videoSourceType} onValueChange={onVideoSourceChange}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {VIDEO_SOURCE_OPTIONS.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
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

export default function CameraBlock(props: {
  videoSourceType:VideoSourceType,
  onVideoSourceChange:(value: VideoSourceType) => void,
  cameraTrack?: CameraVideoTrack,
  screenTrack?: LocalVideoTrack
}) {
  const { videoSourceType, cameraTrack, screenTrack, onVideoSourceChange } = props
  const [videoMute, setVideoMute] = React.useState(false)

  React.useEffect(() => {
    cameraTrack?.setMuted(videoMute)
    screenTrack?.setMuted(videoMute)
  }, [cameraTrack, screenTrack, videoMute])

  const onClickMute = () => {
    setVideoMute(!videoMute)
  }

  return (
    <VideoDeviceWrapper
      title="视频"
      Icon={videoSourceType === VideoSourceType.CAMERA ? CamIconByStatus : ScreenIconByStatus}
      onIconClick={onClickMute}
      isActive={!videoMute}
      videoSourceType={videoSourceType}
      onVideoSourceChange={onVideoSourceChange}
      select={videoSourceType === VideoSourceType.CAMERA ? <CamSelect videoTrack={cameraTrack} /> : <div className="w-[180px]" />}
    >
      <div className="my-3 h-60 w-full overflow-hidden rounded-lg">
        <LocalStreamPlayer videoTrack={videoSourceType === VideoSourceType.CAMERA ? cameraTrack : screenTrack} />
      </div>
    </VideoDeviceWrapper>
  )
}

interface SelectItem {
  label: string
  value: string
  deviceId: string
}

const DEFAULT_ITEM: SelectItem = {
  label: "默认",
  value: "default",
  deviceId: "default",
}

const CamSelect = (props: { videoTrack?: CameraVideoTrack }) => {
  const { videoTrack } = props
  const [items, setItems] = React.useState<SelectItem[]>([DEFAULT_ITEM])
  const [value, setValue] = React.useState("default")

  // 组件挂载时获取设备列表
  React.useEffect(() => {
    console.log("CamSelect: component mounted");
    aliRtcManager.getCameras().then((arr) => {

      const filteredArr = arr.filter(item => item.deviceId && item.deviceId.trim() !== "" && item.deviceId !== "default");
      const newItems = [DEFAULT_ITEM, ...filteredArr.map((item) => ({
        value: item.deviceId,
        label: item.label,
        deviceId: item.deviceId,
      }))];

      setItems(newItems);
    }).catch((error) => {
      console.error("CamSelect: failed to get initial cameras", error);
    });
  }, []);

  // track 变化时设置选中项
  React.useEffect(() => {
    console.log("CamSelect: videoTrack changed", videoTrack);
    if (videoTrack) {
      // 尝试获取track的设备信息，如果失败则使用默认值
      try {
        // 检查是否有getTrackLabel方法（AgoraRTC风格）
        if (typeof videoTrack.getTrackLabel === 'function') {
          const label = videoTrack.getTrackLabel();
          console.log("CamSelect: track label", label);
          setValue(label);
        } else {
          // DingRTC可能没有这个方法，使用默认值
          console.log("CamSelect: no getTrackLabel method, using default");
          setValue("default");
        }
      } catch (error) {
        console.warn("CamSelect: failed to get track label", error);
        setValue("default");
      }
    }
  }, [videoTrack]);

  const onChange = async (value: string) => {
    console.log("CamSelect: onChange", value);
    setValue(value);
    if (videoTrack) {
      await videoTrack.setDevice(value);
    }
  };

  return (
    <DeviceSelect
      items={items}
      value={value}
      onChange={onChange}
      placeholder="选择摄像头"
    />
  );
};
