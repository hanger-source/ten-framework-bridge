# 阿里云DingRTC完整API对比

## 1. 概述

根据[阿里云DingRTC官方文档](https://help.aliyun.com/document_detail/2674351.html)，阿里云DingRTC提供了完整的RTC功能，与Agora RTC功能对应。本文档详细对比两个SDK的API差异。

## 2. 核心API对比

### 2.1 客户端创建

**Agora RTC**:

```typescript
import AgoraRTC from "agora-rtc-sdk-ng";

const client = AgoraRTC.createClient({
  mode: "rtc",
  codec: "vp8",
});
```

**阿里云DingRTC**:

```typescript
import DingRTC from "dingrtc";

const client = DingRTC.createClient();
```

**关键差异**:

- **Agora**: 需要配置mode和codec参数
- **阿里云**: 无需配置参数，更简洁

### 2.2 轨道创建API

**Agora RTC**:

```typescript
// 摄像头视频轨道
const videoTrack = await AgoraRTC.createCameraVideoTrack();

// 麦克风音频轨道
const audioTrack = await AgoraRTC.createMicrophoneAudioTrack();

// 屏幕共享轨道
const screenTrack = await AgoraRTC.createScreenVideoTrack();

// 自定义轨道
const customVideoTrack = AgoraRTC.createCustomVideoTrack({
  mediaStreamTrack: stream.getVideoTracks()[0],
});
```

**阿里云DingRTC** (根据[官方文档](https://help.aliyun.com/document_detail/2674351.html)):

```typescript
// 摄像头视频轨道
const videoTrack = await DingRTC.createCameraVideoTrack();

// 麦克风音频轨道
const audioTrack = await DingRTC.createMicrophoneAudioTrack();

// 屏幕共享轨道
const screenTrack = await DingRTC.createScreenVideoTrack();

// 自定义轨道
const customVideoTrack = DingRTC.createCustomVideoTrack({
  mediaStreamTrack: stream.getVideoTracks()[0],
});

// 同时创建音频和视频轨道
const tracks = await DingRTC.createMicrophoneAndCameraTracks(
  videoConfig,
  audioConfig
);
```

**关键差异**:

- **API一致性**: 大部分API名称相同
- **额外功能**: 阿里云支持同时创建音频和视频轨道

### 2.3 设备管理API

**Agora RTC**:

```typescript
// 获取设备列表
const microphones = await AgoraRTC.getMicrophones();
const cameras = await AgoraRTC.getCameras();
const playbackDevices = await AgoraRTC.getPlaybackDevices();
```

**阿里云DingRTC**:

```typescript
// 获取设备列表
const microphones = await DingRTC.getMicrophones();
const cameras = await DingRTC.getCameras();
const playbackDevices = await DingRTC.getPlaybackDevices();
const allDevices = await DingRTC.getDevices(); // 获取所有设备
```

**关键差异**:

- **API一致性**: 设备管理API完全相同
- **额外功能**: 阿里云提供`getDevices()`获取所有设备

### 2.4 系统检查API

**Agora RTC**:

```typescript
// 检查系统兼容性
const supported = AgoraRTC.checkSystemRequirements();
```

**阿里云DingRTC**:

```typescript
// 检查系统兼容性
const supported = DingRTC.checkSystemRequirements();

// 获取支持的编解码器
const codecs = await DingRTC.getSupportedCodec();
```

**关键差异**:

- **基础功能**: 系统检查API相同
- **额外功能**: 阿里云提供编解码器检查功能

## 3. 配置管理对比

### 3.1 日志级别设置

**Agora RTC**:

```typescript
AgoraRTC.setLogLevel(AgoraRTC.LOG_LEVEL.INFO);
```

**阿里云DingRTC**:

```typescript
DingRTC.setLogLevel("info");
```

### 3.2 客户端配置

**Agora RTC**:

```typescript
// 在创建客户端时配置
const client = AgoraRTC.createClient({
  mode: "rtc",
  codec: "vp8",
});
```

**阿里云DingRTC**:

```typescript
// 创建客户端
const client = DingRTC.createClient();

// 设置全局配置
DingRTC.setClientConfig({
  // 配置参数
});
```

## 4. 事件系统对比

### 4.1 全局事件

**Agora RTC**:

```typescript
// 设备变化事件
AgoraRTC.on("camera-changed", (info) => {
  console.log("摄像头设备变化:", info);
});

AgoraRTC.on("microphone-changed", (info) => {
  console.log("麦克风设备变化:", info);
});

AgoraRTC.on("playback-device-changed", (info) => {
  console.log("播放设备变化:", info);
});
```

**阿里云DingRTC** (根据[官方文档](https://help.aliyun.com/document_detail/2674351.html)):

```typescript
// 设备变化事件
DingRTC.on("camera-changed", (info) => {
  console.log("摄像头设备变化:", info);
});

DingRTC.on("microphone-changed", (info) => {
  console.log("麦克风设备变化:", info);
});

DingRTC.on("playback-device-changed", (info) => {
  console.log("播放设备变化:", info);
});

// 自动播放失败事件
DingRTC.on("autoplay-failed", (track) => {
  console.log("自动播放失败:", track);
});
```

### 4.2 客户端事件

**Agora RTC**:

```typescript
// 用户事件
client.on("user-joined", (user) => {
  console.log("用户加入:", user.uid);
});

client.on("user-left", (user) => {
  console.log("用户离开:", user.uid);
});

// 连接状态
client.on("connection-state-change", (curState, prevState) => {
  console.log("连接状态变化:", prevState, "->", curState);
});

// 网络质量
client.on("network-quality", (quality) => {
  console.log("网络质量:", quality);
});
```

**阿里云DingRTC**:

```typescript
// 用户事件
client.on("user-joined", (user) => {
  console.log("用户加入:", user.userId);
});

client.on("user-left", (user) => {
  console.log("用户离开:", user.userId);
});

// 连接状态
client.on("connection-state-change", (curState, prevState, reason) => {
  console.log("连接状态变化:", prevState, "->", curState, reason);
});

// 网络质量
client.on("network-quality", (uplink, downlink) => {
  console.log("网络质量:", uplink, downlink);
});
```

## 5. 类型定义对比

### 5.1 客户端类型

**Agora RTC**:

```typescript
import { IAgoraRTCClient } from "agora-rtc-sdk-ng";
```

**阿里云DingRTC**:

```typescript
import { DingRTCClient } from "dingrtc";
```

### 5.2 轨道类型

**Agora RTC**:

```typescript
import {
  ICameraVideoTrack,
  IMicrophoneAudioTrack,
  ILocalVideoTrack,
  ILocalAudioTrack,
} from "agora-rtc-sdk-ng";
```

**阿里云DingRTC** (根据[官方文档](https://help.aliyun.com/document_detail/2674346.html)):

```typescript
import {
  CameraVideoTrack,
  MicrophoneAudioTrack,
  LocalVideoTrack,
  LocalAudioTrack,
  CustomVideoTrack,
  CustomAudioTrack,
} from "dingrtc";
```

### 5.3 远程用户类型

**Agora RTC**:

```typescript
import { IAgoraRTCRemoteUser } from "agora-rtc-sdk-ng";
```

**阿里云DingRTC**:

```typescript
import { RemoteUser } from "dingrtc";
```

## 6. 迁移实施要点

### 6.1 依赖更新

```json
// package.json
{
  "dependencies": {
    // 移除
    "agora-rtc-sdk-ng": "^4.20.0",
    // 添加
    "dingrtc": "^3.4.0"
  }
}
```

### 6.2 导入语句更新

**迁移前**:

```typescript
import AgoraRTC, {
  IAgoraRTCClient,
  ICameraVideoTrack,
  IMicrophoneAudioTrack,
} from "agora-rtc-sdk-ng";
```

**迁移后**:

```typescript
import DingRTC, {
  DingRTCClient,
  CameraVideoTrack,
  MicrophoneAudioTrack,
} from "dingrtc";
```

### 6.3 客户端创建更新

**迁移前**:

```typescript
const client = AgoraRTC.createClient({
  mode: "rtc",
  codec: "vp8",
});
```

**迁移后**:

```typescript
const client = DingRTC.createClient();
```

### 6.4 轨道创建更新

**迁移前**:

```typescript
const videoTrack = await AgoraRTC.createCameraVideoTrack();
const audioTrack = await AgoraRTC.createMicrophoneAudioTrack();
```

**迁移后**:

```typescript
const videoTrack = await DingRTC.createCameraVideoTrack();
const audioTrack = await DingRTC.createMicrophoneAudioTrack();
```

### 6.5 事件监听更新

**迁移前**:

```typescript
client.on("user-joined", (user) => {
  console.log("用户加入:", user.uid);
});
```

**迁移后**:

```typescript
client.on("user-joined", (user) => {
  console.log("用户加入:", user.userId);
});
```

## 7. 优势总结

### 7.1 阿里云DingRTC优势

1. **更简洁的API**: 客户端创建无需复杂配置
2. **完整的设备管理**: 提供`getDevices()`获取所有设备
3. **编解码器检查**: 提供`getSupportedCodec()`检查编解码器支持
4. **同时创建轨道**: 支持`createMicrophoneAndCameraTracks()`同时创建音频和视频轨道
5. **更好的事件系统**: 提供更详细的连接状态信息

### 7.2 迁移复杂度评估

- **低复杂度**: 大部分API名称相同
- **高兼容性**: 功能完全对应
- **简单实施**: 主要是导入语句和类型定义的更新

## 8. 注意事项

1. **类型定义更新**: 需要更新所有类型引用
2. **用户标识差异**: uid → userId
3. **事件参数差异**: 部分事件回调参数略有不同
4. **配置方式差异**: 从创建时配置改为全局配置
5. **测试验证**: 需要充分测试所有功能

## 9. 平滑过渡实施策略

### 9.1 连接管理平滑过渡

**实施要点**:

- 保持相同的连接状态监听逻辑
- 适配参数结构差异（位置参数 → 对象参数）
- 保持连接状态枚举的一致性

**代码适配**:

```typescript
// 适配函数
function adaptJoinParams(
  appId: string,
  channel: string,
  token: string,
  uid: string,
  userName: string
) {
  return {
    appId,
    channel,
    token,
    uid,
    userName,
  };
}
```

### 9.2 断联处理平滑过渡

**实施要点**:

- 保持相同的断联检测逻辑
- 利用阿里云提供的reason参数进行更精确的断联处理
- 保持重连机制的一致性

**代码适配**:

```typescript
// 断联处理适配
function handleDisconnect(reason?: string) {
  console.log("连接断开，原因:", reason);
  // 根据reason进行不同的处理
  if (reason === "network_error") {
    // 网络错误，尝试重连
    attemptReconnect();
  } else if (reason === "leave") {
    // 主动离开，清理资源
    cleanupResources();
  }
}
```

### 9.3 事件监听平滑过渡

**实施要点**:

- 保持相同的事件监听结构
- 适配用户标识差异（uid → userId）
- 保持轨道订阅/取消订阅逻辑的一致性

**代码适配**:

```typescript
// 用户标识适配
function adaptUserIdentifier(user: any) {
  return {
    ...user,
    uid: user.userId, // 保持向后兼容
  };
}

// 事件监听适配
function setupEventListeners(client: any) {
  client.on("user-joined", (user: any) => {
    const adaptedUser = adaptUserIdentifier(user);
    handleUserJoined(adaptedUser);
  });
}
```

### 9.4 消息推送平滑过渡

**实施要点**:

- 保持消息处理逻辑的一致性
- 适配消息格式差异（字符串 → Uint8Array）
- 保持消息分片和重组机制
- 利用阿里云的session管理功能

**代码适配**:

```typescript
// 消息格式适配
function adaptMessageFormat(message: string): Uint8Array {
  const encoder = new TextEncoder();
  return encoder.encode(message);
}

function adaptReceivedMessage(data: any): any {
  const decoder = new TextDecoder("utf-8");
  const message = decoder.decode(data.message);
  return {
    ...data,
    message: JSON.parse(message),
  };
}
```

### 9.5 渐进式迁移策略

**阶段一**: 基础功能迁移

- 客户端创建和连接
- 基本事件监听
- 轨道创建和发布

**阶段二**: 高级功能迁移

- 消息传递功能
- 断联重连机制
- 网络质量监控

**阶段三**: 优化和测试

- 性能优化
- 兼容性测试
- 用户体验验证

### 9.6 兼容性保证

**向后兼容**:

- 保持现有API接口不变
- 提供适配层处理差异
- 确保现有功能不受影响

**向前兼容**:

- 利用阿里云的新功能
- 优化用户体验
- 提升系统性能
