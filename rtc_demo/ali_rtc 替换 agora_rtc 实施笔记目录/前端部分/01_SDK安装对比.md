# SDK安装对比

## 包管理器安装

### Agora RTC SDK 安装

```bash
# npm
npm install agora-rtc-sdk-ng

# yarn
yarn add agora-rtc-sdk-ng

# pnpm
pnpm add agora-rtc-sdk-ng
```

### 阿里云RTC SDK 安装

```bash
# npm
npm install dingrtc

# yarn
yarn add dingrtc

# pnpm
pnpm add dingrtc
```

## package.json 依赖对比

### Agora RTC

```json
{
  "dependencies": {
    "agora-rtc-sdk-ng": "^4.20.0"
  }
}
```

### 阿里云RTC

```json
{
  "dependencies": {
    "dingrtc": "^1.0.0"
  }
}
```

## TypeScript 类型支持

### Agora RTC

```typescript
import AgoraRTC, {
  IAgoraRTCClient,
  ICameraVideoTrack,
  IMicrophoneAudioTrack,
} from "agora-rtc-sdk-ng";
```

### 阿里云RTC

```typescript
import DingRTC, {
  DingRTCClient,
  CameraVideoTrack,
  MicrophoneAudioTrack,
} from "dingrtc";
```

## 安装后配置

### Agora RTC 配置

```typescript
// 设置日志级别
AgoraRTC.setLogLevel(AgoraRTC.LOG_LEVEL.INFO);

// 创建客户端
const client = AgoraRTC.createClient({
  mode: "rtc",
  codec: "vp8",
});
```

### 阿里云RTC 配置

```typescript
// 设置日志级别
DingRTC.setLogLevel("info");

// 创建客户端
const client = DingRTC.createClient();
```

## 关键差异总结

### 1. 安装命令

- **Agora**: `npm install agora-rtc-sdk-ng`
- **阿里云**: `npm install dingrtc`

### 2. 导入方式

- **Agora**: `import AgoraRTC from "agora-rtc-sdk-ng"`
- **阿里云**: `import DingRTC from "dingrtc"`

### 3. 客户端创建

- **Agora**: 需要配置参数 `{mode: "rtc", codec: "vp8"}`
- **阿里云**: 无需配置参数

### 4. 日志配置

- **Agora**: `AgoraRTC.setLogLevel(AgoraRTC.LOG_LEVEL.INFO)`
- **阿里云**: `DingRTC.setLogLevel("info")`

### 5. 类型定义差异

- **Agora**: `IAgoraRTCClient`, `ICameraVideoTrack`, `IMicrophoneAudioTrack`
- **阿里云**: `DingRTCClient`, `CameraVideoTrack`, `MicrophoneAudioTrack`

### 6. 轨道创建方式

- **Agora**: 静态方法 `AgoraRTC.createCameraVideoTrack()`
- **阿里云**: 静态方法 `DingRTC.createCameraVideoTrack()` (根据[官方文档](https://help.aliyun.com/document_detail/2674351.html))

### 7. 设备管理API

**Agora RTC**:

```typescript
// 获取设备列表
AgoraRTC.getMicrophones();
AgoraRTC.getCameras();
AgoraRTC.getPlaybackDevices();
```

**阿里云RTC**:

```typescript
// 获取设备列表
DingRTC.getMicrophones();
DingRTC.getCameras();
DingRTC.getPlaybackDevices();
DingRTC.getDevices(); // 获取所有设备
```

### 8. 系统检查API

**Agora RTC**:

```typescript
// 检查系统兼容性
AgoraRTC.checkSystemRequirements();
```

**阿里云RTC**:

```typescript
// 检查系统兼容性
DingRTC.checkSystemRequirements();
// 获取支持的编解码器
DingRTC.getSupportedCodec();
```

## 迁移要点

1. **更新依赖**: 将 `agora-rtc-sdk-ng` 替换为 `dingrtc`
2. **更新导入**: 修改所有 import 语句
3. **简化配置**: 删除客户端创建时的配置参数
4. **更新日志**: 修改日志级别设置方式
