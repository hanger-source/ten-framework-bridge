# RTM消息传递迁移对比

## 1. 概述

根据[阿里云RTM官方文档](https://help.aliyun.com/document_detail/2879041.html)，阿里云RTC完全支持RTM功能，这大大简化了从Agora到阿里云的迁移工作。

## 2. SDK包对比

### 2.1 安装依赖

**Agora RTM**:

```bash
npm install agora-rtm
```

**阿里云RTM**:

```bash
npm install @dingrtc/rtm
```

### 2.2 导入方式

**Agora RTM**:

```typescript
import AgoraRTM from "agora-rtm";
```

**阿里云RTM**:

```typescript
import RTM from "@dingrtc/rtm";
```

## 3. 实例创建对比

### 3.1 Agora RTM实例创建

```typescript
// Agora RTM
const rtm = new AgoraRTM.RTM(appId, String(userId), {
  logLevel: "debug",
});
```

### 3.2 阿里云RTM实例创建

```typescript
// 阿里云RTM
const rtm = new RTM();
```

**关键差异**:

- **Agora**: 需要传入appId和userId参数
- **阿里云**: 无需参数，更简洁

## 4. 频道加入对比

### 4.1 Agora RTM频道加入

```typescript
// Agora RTM
await rtm.login({ token });
const subscribeResult = await rtm.subscribe(channel, {
  withMessage: true,
  withPresence: true,
  beQuiet: false,
  withMetadata: true,
  withLock: true,
});
```

### 4.2 阿里云RTM频道加入

```typescript
// 独立使用
await rtm.join({
  appId: "",
  userName: "",
  channel: "",
  uid: "",
  token: "",
});

// 配合DingRTC使用
const client = DingRTC.createClient();
const rtm = new RTM();
client.register(rtm);
await client.join({
  appId: "",
  userName: "",
  channel: "",
  uid: "",
  token: "",
});
```

**关键差异**:

- **Agora**: 需要先login，再subscribe
- **阿里云**: 直接join，支持与RTC共享连接

## 5. 消息发布对比

### 5.1 Agora RTM消息发布

```typescript
// 字符串消息
await rtm.publish(channel, JSON.stringify(msg), {
  customType: "PainTxt",
});

// 二进制消息
await rtm.publish(channel, binaryData, {
  customType: "BinaryData",
});
```

### 5.2 阿里云RTM消息发布

```typescript
// 广播消息
const message = "hello world";
const encoder = new TextEncoder();
rtm.publish(sessionId, encoder.encode(message));

// 点对点消息
const otherUserId = "user1";
rtm.publish(sessionId, encoder.encode(message), otherUserId);
```

**关键差异**:

- **Agora**: 使用channel和options参数
- **阿里云**: 使用sessionId和targetUid参数
- **消息格式**: Agora支持字符串，阿里云使用Uint8Array

## 6. 消息监听对比

### 6.1 Agora RTM消息监听

```typescript
rtm.addEventListener("message", (e) => {
  const { message, messageType } = e;
  if (messageType === "STRING") {
    const msg = JSON.parse(message as string);
    console.log("收到消息:", msg);
  }
  if (messageType === "BINARY") {
    const decoder = new TextDecoder("utf-8");
    const decodedMessage = decoder.decode(message as Uint8Array);
    const msg = JSON.parse(decodedMessage);
    console.log("收到消息:", msg);
  }
});
```

### 6.2 阿里云RTM消息监听

```typescript
rtm.on("message", (data) => {
  const { message, uid, sessionId, broadcast } = data;
  const decoder = new TextDecoder("utf-8");
  console.log("收到消息:", decoder.decode(message));
  console.log("发送者:", uid);
  console.log("会话ID:", sessionId);
  console.log("是否广播:", broadcast);
});
```

**关键差异**:

- **Agora**: 使用addEventListener，需要区分消息类型
- **阿里云**: 使用on方法，统一处理Uint8Array格式

## 7. 会话管理对比

### 7.1 Agora RTM会话管理

```typescript
// Agora RTM没有显式的会话管理
// 直接使用channel进行消息传递
```

### 7.2 阿里云RTM会话管理

```typescript
// 加入会话
await rtm.joinSession(sessionId);

// 离开会话
await rtm.leaveSession(sessionId);

// 关闭会话
await rtm.closeSession(sessionId);

// 监听会话事件
rtm.on("session-add", (session) => {
  console.log("新会话创建:", session);
});

rtm.on("session-remove", (session) => {
  console.log("会话关闭:", session);
});

rtm.on("session-user-join", (sessionId, uid) => {
  console.log("用户加入会话:", sessionId, uid);
});

rtm.on("session-user-left", (sessionId, uid) => {
  console.log("用户离开会话:", sessionId, uid);
});
```

**关键差异**:

- **Agora**: 基于channel的简单消息传递
- **阿里云**: 完整的会话管理系统

## 8. 连接状态管理对比

### 8.1 Agora RTM连接状态

```typescript
rtm.addEventListener("connection-state-change", (curState, prevState) => {
  console.log("连接状态变化:", prevState, "->", curState);
});
```

### 8.2 阿里云RTM连接状态

```typescript
rtm.on("connection-state-change", (curState, prevState, reason) => {
  console.log("连接状态变化:", prevState, "->", curState, reason);
});
```

## 9. 迁移实施要点

### 9.1 依赖更新

```json
// package.json
{
  "dependencies": {
    // 移除
    "agora-rtm": "^2.2.0",
    // 添加
    "@dingrtc/rtm": "^1.0.0"
  }
}
```

### 9.2 代码迁移示例

**迁移前 (Agora RTM)**:

```typescript
import AgoraRTM from "agora-rtm";

const rtm = new AgoraRTM.RTM(appId, String(userId));
await rtm.login({ token });
await rtm.subscribe(channel, { withMessage: true });

rtm.addEventListener("message", (e) => {
  const { message, messageType } = e;
  if (messageType === "STRING") {
    const msg = JSON.parse(message as string);
    console.log(msg);
  }
});

await rtm.publish(channel, JSON.stringify(msg), { customType: "PainTxt" });
```

**迁移后 (阿里云RTM)**:

```typescript
import RTM from "@dingrtc/rtm";

const rtm = new RTM();
await rtm.join({
  appId: appId,
  userName: userName,
  channel: channel,
  uid: uid,
  token: token,
});

rtm.on("message", (data) => {
  const { message, uid, sessionId, broadcast } = data;
  const decoder = new TextDecoder("utf-8");
  const msg = JSON.parse(decoder.decode(message));
  console.log(msg);
});

const encoder = new TextEncoder();
rtm.publish(sessionId, encoder.encode(JSON.stringify(msg)));
```

### 9.3 关键迁移点

1. **实例创建**: 简化参数
2. **频道加入**: 统一join方法
3. **消息格式**: 字符串 → Uint8Array
4. **事件监听**: addEventListener → on
5. **会话管理**: 新增会话概念
6. **消息发布**: 使用sessionId替代channel

## 10. 优势总结

### 10.1 阿里云RTM优势

1. **更简洁的API**: 无需复杂的参数配置
2. **完整的会话管理**: 支持会话的创建、加入、离开、关闭
3. **与RTC集成**: 可以与DingRTC共享连接
4. **统一的消息格式**: 统一使用Uint8Array格式
5. **更好的事件系统**: 使用on/off方法，更符合JavaScript习惯

### 10.2 迁移复杂度评估

- **低复杂度**: API差异较小
- **高兼容性**: 功能完全对应
- **简单实施**: 可以直接迁移，无需寻找替代方案

## 11. 注意事项

1. **消息格式转换**: 需要处理字符串和Uint8Array的转换
2. **会话管理**: 需要理解阿里云RTM的会话概念
3. **事件监听**: 需要更新事件监听方式
4. **依赖管理**: 需要更新package.json中的依赖
5. **测试验证**: 需要充分测试消息传递功能

## 12. 平滑过渡策略

### 12.1 消息格式适配

**实施要点**:

- 保持消息处理逻辑的一致性
- 适配消息格式差异（字符串 → Uint8Array）
- 保持消息分片和重组机制

**代码适配**:

```typescript
// 消息发送适配
function adaptMessageForSending(message: any): Uint8Array {
  const encoder = new TextEncoder();
  return encoder.encode(JSON.stringify(message));
}

// 消息接收适配
function adaptMessageForReceiving(data: any): any {
  const decoder = new TextDecoder("utf-8");
  const message = decoder.decode(data.message);
  return {
    ...data,
    message: JSON.parse(message),
  };
}
```

### 12.2 会话管理适配

**实施要点**:

- 理解阿里云RTM的会话概念
- 适配会话创建和管理逻辑
- 保持消息传递的可靠性

**代码适配**:

```typescript
// 会话管理适配
class SessionManager {
  private sessions: Map<string, any> = new Map();

  async joinSession(sessionId: string) {
    await this.rtm.joinSession(sessionId);
    this.sessions.set(sessionId, true);
  }

  async leaveSession(sessionId: string) {
    await this.rtm.leaveSession(sessionId);
    this.sessions.delete(sessionId);
  }

  isInSession(sessionId: string): boolean {
    return this.sessions.has(sessionId);
  }
}
```

### 12.3 事件监听适配

**实施要点**:

- 保持事件监听结构的一致性
- 适配事件参数差异
- 保持消息处理逻辑

**代码适配**:

```typescript
// 事件监听适配
function setupRTMEventListeners(rtm: any) {
  rtm.on("message", (data: any) => {
    const adaptedData = adaptMessageForReceiving(data);
    handleMessage(adaptedData);
  });

  rtm.on("session-add", (session: any) => {
    handleSessionAdd(session);
  });

  rtm.on("session-remove", (session: any) => {
    handleSessionRemove(session);
  });
}
```

### 12.4 渐进式迁移

**阶段一**: 基础消息传递

- 实现基本的消息发送和接收
- 适配消息格式差异
- 测试基本功能

**阶段二**: 高级功能

- 实现会话管理
- 添加消息分片和重组
- 优化性能

**阶段三**: 完整集成

- 与RTC功能集成
- 完善错误处理
- 性能优化和测试
