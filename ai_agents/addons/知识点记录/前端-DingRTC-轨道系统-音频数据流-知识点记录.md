## DingRTC 轨道系统

### 1. 各种 Track 的关系

#### **浏览器原生 MediaStreamTrack**

```javascript
// 浏览器获取设备信息
navigator.mediaDevices.getUserMedia({ audio: true, video: true });
// 返回 MediaStream，包含 MediaStreamTrack
```

#### **DingRTC 的 Track 类型**

```javascript
// 本地轨道 - 从本地设备创建
CameraVideoTrack; // 摄像头视频轨道
MicrophoneAudioTrack; // 麦克风音频轨道
LocalVideoTrack; // 本地视频轨道（如屏幕共享）

// 远程轨道 - 从其他用户接收
RemoteVideoTrack; // 远程视频轨道
RemoteAudioTrack; // 远程音频轨道
```

#### **关系图**

```
浏览器设备 → MediaStreamTrack → DingRTC Track → 发布到服务器
     ↓              ↓              ↓
摄像头/麦克风 → 原生轨道 → 封装轨道 → 网络传输
```

### 2. 音频数据流

#### **完整的数据流图**

```
麦克风设备 → MediaStreamTrack → MicrophoneAudioTrack → 发布到服务器
     ↓              ↓                    ↓              ↓
物理音频 → 浏览器原生轨道 → DingRTC封装轨道 → 网络传输
```

#### **视频数据流**

```
摄像头设备 → MediaStreamTrack → CameraVideoTrack → 发布到服务器
     ↓              ↓                    ↓              ↓
物理视频 → 浏览器原生轨道 → DingRTC封装轨道 → 网络传输
```

### 3. 具体实现流程

#### **步骤1: 获取设备权限**

```javascript
// 浏览器原生API
navigator.mediaDevices.getUserMedia({ audio: true, video: true });
// 返回 MediaStream，包含 MediaStreamTrack
```

#### **步骤2: 创建 DingRTC 轨道**

```javascript
// DingRTC 封装原生轨道
const audioTrack = await DingRTC.createMicrophoneAudioTrack();
const videoTrack = await DingRTC.createCameraVideoTrack();
```

#### **步骤3: 加入频道**

```javascript
await this.client.join({
  appId: appId,
  token: finalToken,
  uid: String(userId),
  channel: channel,
  userName: `user_${userId}`,
});
```

#### **步骤4: 发布轨道**

```javascript
// 将轨道发布到服务器
await this.client.publish(tracks);
```

### 4. 轨道类型说明

#### **Local Tracks (本地轨道)**

- `MicrophoneAudioTrack` - 麦克风音频轨道
- `CameraVideoTrack` - 摄像头视频轨道
- `LocalVideoTrack` - 本地视频轨道（屏幕共享）

#### **Remote Tracks (远程轨道)**

- `RemoteAudioTrack` - 远程音频轨道（其他用户的音频）
- `RemoteVideoTrack` - 远程视频轨道（其他用户的视频）

### 5. 设备信息获取

```javascript
// 获取所有设备
navigator.mediaDevices.enumerateDevices();
// 返回设备列表：摄像头、麦克风、扬声器

// 获取特定设备
navigator.mediaDevices.getUserMedia({
  audio: { deviceId: "specific-device-id" },
  video: { deviceId: "specific-device-id" },
});
```

### 6. 音频数据如何被发上去

1. **设备采集** - 麦克风采集音频数据
2. **浏览器处理** - 浏览器将音频数据封装成 MediaStreamTrack
3. **DingRTC 封装** - DingRTC 将 MediaStreamTrack 封装成 MicrophoneAudioTrack
4. **网络编码** - DingRTC 对音频数据进行编码（如 Opus）
5. **网络传输** - 通过 WebRTC 协议发送到服务器
6. **服务器转发** - 服务器将音频数据转发给其他用户

### 7. 关键区别

- **MediaStreamTrack** - 浏览器原生，包含原始音频/视频数据
- **DingRTC Track** - 封装了 MediaStreamTrack，添加了网络传输能力
- **Local/Remote** - 本地轨道用于发送，远程轨道用于接收

## 音轨图可视化

### 音频可视化原理

```javascript
// 使用 Web Audio API 分析音频数据
const ctx = new AudioContext();
const source = ctx.createMediaStreamSource(mediaStream);
const analyser = ctx.createAnalyser();
analyser.getFloatFrequencyData(dataArray); // 获取频率数据
```

### 音轨图数据流

```
音频轨道 → MediaStream → AudioContext → Analyser → 频率数据 → 可视化
```

## 组件生命周期

### RTCCard 组件状态管理

- **组件挂载** - 创建音频轨道，设置事件监听
- **加入频道** - 创建视频轨道，发布轨道到服务器
- **退出频道** - 保持音频轨道活跃，清理其他轨道
- **组件卸载** - 清理所有事件监听

### 音频轨道持久化

- 退出频道时不清除音频轨道
- 确保音轨图在退出频道后继续工作
- 定期检查音频轨道状态

## 网络传输

### WebRTC 协议

- **P2P 连接** - 点对点直接通信
- **STUN/TURN 服务器** - NAT 穿透和媒体中继
- **SDP 协商** - 会话描述协议，协商媒体能力

### 音频编码

- **Opus 编码** - 高效的音频编码格式
- **自适应比特率** - 根据网络状况调整编码参数
- **丢包补偿** - 网络丢包时的音频恢复机制
