# 阿里云RTC模块拆分说明

## 文件结构

### 核心文件

- `ali-rtc.ts` - 主管理器类，包含核心RTC功能
- `ali-types.ts` - 类型定义文件

### 拆分模块

- `ali-rtc-types.ts` - 枚举和接口定义
- `ali-rtc-error.ts` - 错误处理适配器
- `ali-rtc-interceptor.ts` - 网络请求拦截器
- `ali-rtc-message.ts` - 消息处理逻辑
- `ali-rtc-devices.ts` - 设备管理功能
- `ali-rtc-events.ts` - RTC事件监听处理
- `ali-rtc-network.ts` - 网络质量监控

## 模块职责

### AliRtcManager (主管理器)

- 频道连接管理
- 音视频轨道管理
- 事件监听和分发
- 网络质量监控

### AliRtcMessageHandler (消息处理)

- 消息分片重组
- 数据解析
- 消息缓存管理

### AliRtcInterceptor (网络拦截)

- 拦截阿里云RTC日志上报
- 避免ERR_BLOCKED_BY_CLIENT错误

### AliRtcDeviceManager (设备管理)

- 摄像头、麦克风、扬声器枚举
- 设备轨道创建和管理
- 视频源切换（摄像头/屏幕共享）
- 设备权限检查
- 轨道状态监控

### AliRtcEventHandler (事件处理)

- RTC事件监听和分发
- 连接状态变化处理
- 用户加入/离开事件
- 错误事件统一处理

### AliRtcNetworkMonitor (网络监控)

- 网络质量统计
- 实时监控上行/下行质量
- 本地/远程音视频状态监控

### ErrorAdapter (错误处理)

- 统一错误格式适配
- 错误类型转换

## 使用方式

```typescript
import { aliRtcManager } from "./ali-rtc";

// 加入频道
await aliRtcManager.join({ channel: "test", userId: 123 });

// 创建设备轨道
await aliRtcManager.createCameraTracks();
await aliRtcManager.createMicrophoneAudioTrack();

// 发布轨道
await aliRtcManager.publish();

// 销毁连接
await aliRtcManager.destroy();
```

## 优势

1. **代码组织更清晰** - 每个模块职责单一
2. **维护性更好** - 修改某个功能只需要关注对应模块
3. **可测试性更强** - 可以独立测试每个模块
4. **可复用性更高** - 模块可以在其他地方复用
