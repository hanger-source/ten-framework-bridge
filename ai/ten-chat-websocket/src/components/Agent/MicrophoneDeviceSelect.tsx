import React from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// 设备选择组件
function MicrophoneDeviceSelect() {
  const [devices, setDevices] = React.useState<Array<{label: string, value: string, deviceId: string}>>([]);
  const [selectedDevice, setSelectedDevice] = React.useState("default");

  React.useEffect(() => {
    // 获取麦克风设备列表
    navigator.mediaDevices.enumerateDevices()
      .then(devices => {
        const audioDevices = devices
          .filter(device => device.kind === 'audioinput')
          .map(device => ({
            label: device.label || `麦克风 ${device.deviceId.slice(0, 8)}`,
            value: device.deviceId || `device-${Math.random()}`, // 确保 value 不为空
            deviceId: device.deviceId
          }));

        if (audioDevices.length > 0) {
          setDevices(audioDevices);
          setSelectedDevice(audioDevices[0].value);
        }
      })
      .catch(error => {
        console.error('获取设备列表失败:', error);
      });
  }, []);

  const handleDeviceChange = (deviceId: string) => {
    setSelectedDevice(deviceId);
    console.log('切换到设备:', deviceId);
  };

  return (
    <Select value={selectedDevice} onValueChange={handleDeviceChange}>
      <SelectTrigger className="w-[180px]">
        <SelectValue placeholder="选择麦克风" />
      </SelectTrigger>
      <SelectContent>
        {devices.map((device) => (
          <SelectItem key={device.value} value={device.value}>
            {device.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export default MicrophoneDeviceSelect;
