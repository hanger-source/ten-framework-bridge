# 实际代码迁移实施 - 1: 前端RTC客户端完整迁移

## 迁移概述

基于对现有代码的深入分析，前端RTC客户端迁移是核心迁移点之一。迁移策略是参考已有的阿里云RTC实现，将Agora RTC代码迁移为阿里云RTC，同时保持向后兼容性。

## 迁移文件清单

### 需要修改的核心文件

- `ai_agents/playground/src/components/RTCManager.ts` - RTC管理器
- `ai_agents/playground/src/hooks/useRTC.ts` - RTC Hook
- `ai_agents/playground/src/store/rtc.ts` - RTC状态管理
- `ai_agents/playground/src/common/request.ts` - Token请求服务

### 需要新增的文件

- `ai_agents/playground/src/types/rtc.ts` - RTC类型定义
- `ai_agents/playground/src/utils/rtcAdapter.ts` - RTC适配器

## 核心迁移文件

### 1. RTC客户端创建

**当前Agora实现**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
import AgoraRTC, { IAgoraRTCClient } from "agora-rtc-sdk-ng";

export class RTCManager {
  private client: IAgoraRTCClient;

  constructor() {
    this.client = AgoraRTC.createClient({
      mode: "rtc",
      codec: "vp8",
    });
  }
}
```

**迁移为阿里云RTC**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
import DingRTC, { DingRTCClient } from "dingrtc";

export class RTCManager {
  private client: DingRTCClient;

  constructor() {
    this.client = DingRTC.createClient();
  }

  async joinChannel(channel: string, uid: string) {
    // 获取Token
    const token = await requestToken(channel, uid);

    // 加入房间
    await this.client.join(token, channel, uid);
  }
}
```

### 2. 轨道创建迁移

**当前Agora实现**:

```typescript
// 音频轨道创建
this.localAudioTrack = await AgoraRTC.createMicrophoneAudioTrack({
  encoderConfig: "music_standard",
  AEC: true,
  AGC: true,
  ANS: true,
});

// 视频轨道创建
this.localVideoTrack = await AgoraRTC.createCameraVideoTrack({
  encoderConfig: "1080p_1",
});
```

**迁移为阿里云RTC**:

```typescript
// 音频轨道创建
this.localAudioTrack = await DingRTC.createMicrophoneAudioTrack();

// 视频轨道创建
this.localVideoTrack = await DingRTC.createCameraVideoTrack();
```

### 3. 房间加入迁移

**当前Agora实现**:

```typescript
// 加入房间
await this.client.join(token, channel, uid);

// 发布轨道
await this.client.publish([this.localAudioTrack, this.localVideoTrack]);
```

**迁移为阿里云RTC**:

```typescript
// 加入房间
await this.client.join(token, channel, uid);

// 发布轨道
await this.client.publish([this.localAudioTrack, this.localVideoTrack]);
```

## 状态管理迁移

### 1. 客户端状态

**当前Agora状态**:

```typescript
// ai_agents/playground/src/store/rtc.ts
export const rtcClient = atom<IAgoraRTCClient>({
  key: "rtcClient",
  default: null,
});
```

**迁移为阿里云RTC状态**:

```typescript
// ai_agents/playground/src/store/rtc.ts
export const rtcClient = atom<DingRTCClient>({
  key: "rtcClient",
  default: null,
});
```

### 2. 轨道状态

**当前Agora轨道状态**:

```typescript
export const localTracks = atom<{
  audioTrack?: IMicrophoneAudioTrack;
  videoTrack?: ICameraVideoTrack;
}>({
  key: "localTracks",
  default: {},
});
```

**迁移为阿里云RTC轨道状态**:

```typescript
export const localTracks = atom<{
  audioTrack?: MicrophoneAudioTrack;
  videoTrack?: CameraVideoTrack;
}>({
  key: "localTracks",
  default: {},
});
```

## 事件处理迁移

### 1. 用户加入事件

**当前Agora实现**:

```typescript
this.client.on("user-joined", (user) => {
  console.log("User joined:", user.uid);
});
```

**迁移为阿里云RTC**:

```typescript
this.client.on("user-joined", (user) => {
  console.log("User joined:", user.userId);
});
```

### 2. 用户离开事件

**当前Agora实现**:

```typescript
this.client.on("user-left", (user) => {
  console.log("User left:", user.uid);
});
```

**迁移为阿里云RTC**:

```typescript
this.client.on("user-left", (user) => {
  console.log("User left:", user.userId);
});
```

## 网络质量监控迁移

### 1. 网络质量事件

**当前Agora实现**:

```typescript
this.client.on("network-quality", (stats) => {
  console.log("Network quality:", stats);
});
```

**迁移为阿里云RTC**:

```typescript
this.client.on("network-quality", (quality) => {
  console.log("Network quality:", quality);
});
```

## 关键差异总结

### 1. 客户端创建

- **Agora**: `AgoraRTC.createClient({mode: "rtc", codec: "vp8"})`
- **阿里云**: `DingRTC.createClient()`

### 2. 轨道创建

- **Agora**: `AgoraRTC.createMicrophoneAudioTrack()` (静态方法)
- **阿里云**: `DingRTC.createMicrophoneAudioTrack()` (静态方法)

### 3. 用户标识

- **Agora**: `user.uid`
- **阿里云**: `user.userId`

### 4. 迁移要点

- 删除Agora RTC相关导入
- 添加阿里云RTC相关导入
- 修改客户端创建方式（简化配置）
- 修改轨道创建方式（保持静态方法）
- 修改用户标识字段（uid -> userId）
- 更新事件处理逻辑
