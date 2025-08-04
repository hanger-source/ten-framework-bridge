# 实际代码迁移实施 - 3: 前端Token获取和配置迁移

## 当前Token获取分析

基于对现有代码的分析，发现：

1. **当前实现**: 前端调用后端API获取Agora RTC Token
2. **目标实现**: 支持获取阿里云RTC Token
3. **迁移策略**: 修改前端请求，替换为阿里云RTC

## 核心迁移文件

### 1. Token请求服务

**当前Agora实现**:

```typescript
// ai_agents/playground/src/common/request.ts
export const requestToken = async (
  channel: string,
  uid: string
): Promise<string> => {
  const response = await fetch("/api/token/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      channel,
      uid,
    }),
  });

  if (!response.ok) {
    throw new Error("Failed to get token");
  }

  return response.text();
};
```

**迁移为阿里云RTC**:

```typescript
// ai_agents/playground/src/common/request.ts
export const requestToken = async (
  channel: string,
  uid: string
): Promise<string> => {
  const response = await fetch("/api/token/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      channel,
      uid,
    }),
  });

  if (!response.ok) {
    throw new Error("Failed to get Ali RTC token");
  }

  return response.text();
};
```

### 2. RTC客户端配置

**当前Agora配置**:

```typescript
// ai_agents/playground/src/config/rtc.ts
export const rtcConfig = {
  appId: process.env.REACT_APP_AGORA_APP_ID,
  token: null, // 动态获取
  channel: null, // 动态设置
  uid: null, // 动态设置
};
```

**迁移为阿里云RTC配置**:

```typescript
// ai_agents/playground/src/config/rtc.ts
export const rtcConfig = {
  appId: process.env.REACT_APP_ALI_APP_ID,
  token: null, // 动态获取
  channel: null, // 动态设置
  uid: null, // 动态设置
};
```

### 3. RTC客户端管理器

**当前Agora实现**:

```typescript
// ai_agents/playground/src/manager/rtc/rtc.ts
export class RtcManager extends AGEventEmitter<RtcEvents> {
  client: IAgoraRTCClient;

  constructor() {
    this.client = AgoraRTC.createClient({ mode: "rtc", codec: "vp8" });
  }

  async join({
    channel,
    userId,
    agentSettings,
  }: {
    channel: string;
    userId: number;
    agentSettings?: any;
  }) {
    if (!this._joined) {
      let appId: string;
      let finalToken: string;

      if (agentSettings?.token) {
        appId =
          agentSettings?.env?.AGORA_APP_ID ||
          process.env.NEXT_PUBLIC_AGORA_APP_ID ||
          "";
        finalToken = agentSettings.token;
      } else {
        const res = await apiGenAgoraData({ channel, userId });
        const { code, data } = res;
        if (code != 0) {
          throw new Error("Failed to get Agora token");
        }
        appId = data.appId;
        finalToken = data.token;
      }

      await this.client?.join(appId, channel, finalToken, userId);
      this._joined = true;
    }
  }
}
```

**迁移为阿里云RTC**:

```typescript
// ai_agents/playground/src/manager/rtc/rtc.ts
export class RtcManager extends AGEventEmitter<RtcEvents> {
  client: DingRTCClient;

  constructor() {
    this.client = DingRTC.createClient();
  }

  async join({
    channel,
    userId,
    agentSettings,
  }: {
    channel: string;
    userId: number;
    agentSettings?: any;
  }) {
    if (!this._joined) {
      let appId: string;
      let finalToken: string;

      if (agentSettings?.token) {
        appId =
          agentSettings?.env?.ALI_APP_ID ||
          process.env.NEXT_PUBLIC_ALI_APP_ID ||
          "";
        finalToken = agentSettings.token;
      } else {
        const res = await apiGenAliData({ channel, userId });
        const { code, data } = res;
        if (code != 0) {
          throw new Error("Failed to get Ali RTC token");
        }
        appId = data.appId;
        finalToken = data.token;
      }

      await this.client?.join({
        appId,
        token: finalToken,
        uid: userId,
        channel,
        userName: `user_${userId}`,
      });
      this._joined = true;
    }
  }
}
```

### 4. RTC组件

**当前Agora实现**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
export const RTCManager: React.FC<RTCManagerProps> = ({ channel, userId }) => {
  const [client, setClient] = useState<IAgoraRTCClient | null>(null);

  const joinChannel = useCallback(async () => {
    const rtcClient = AgoraRTC.createClient({ mode: "rtc", codec: "vp8" });
    const token = await requestToken(channel, userId.toString());
    await rtcClient.join(process.env.REACT_APP_AGORA_APP_ID!, channel, token, userId);
    setClient(rtcClient);
  }, [channel, userId]);

  return (
    <div>
      <button onClick={joinChannel}>Join Channel</button>
    </div>
  );
};
```

**迁移为阿里云RTC**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
export const RTCManager: React.FC<RTCManagerProps> = ({ channel, userId }) => {
  const [client, setClient] = useState<DingRTCClient | null>(null);

  const joinChannel = useCallback(async () => {
    const rtcClient = DingRTC.createClient();
    const token = await requestToken(channel, userId.toString());
    await rtcClient.join({
      appId: process.env.REACT_APP_ALI_APP_ID!,
      token,
      uid: userId,
      channel,
      userName: `user_${userId}`
    });
    setClient(rtcClient);
  }, [channel, userId]);

  return (
    <div>
      <button onClick={joinChannel}>Join Channel</button>
    </div>
  );
};
```

### 5. RTC Hook

**当前Agora实现**:

```typescript
// ai_agents/playground/src/hooks/useRTC.ts
export const useRTC = () => {
  const [client, setClient] = useState<IAgoraRTCClient | null>(null);

  const joinChannel = useCallback(async (channel: string, uid: string) => {
    const rtcClient = AgoraRTC.createClient({ mode: "rtc", codec: "vp8" });
    const token = await requestToken(channel, uid);
    await rtcClient.join(
      process.env.REACT_APP_AGORA_APP_ID!,
      channel,
      token,
      uid
    );
    setClient(rtcClient);
  }, []);

  return { joinChannel, leaveChannel: () => client?.leave() };
};
```

**迁移为阿里云RTC**:

```typescript
// ai_agents/playground/src/hooks/useRTC.ts
export const useRTC = () => {
  const [client, setClient] = useState<DingRTCClient | null>(null);

  const joinChannel = useCallback(async (channel: string, uid: string) => {
    const rtcClient = DingRTC.createClient();
    const token = await requestToken(channel, uid);
    await rtcClient.join({
      appId: process.env.REACT_APP_ALI_APP_ID!,
      token,
      uid,
      channel,
      userName: `user_${uid}`,
    });
    setClient(rtcClient);
  }, []);

  return { joinChannel, leaveChannel: () => client?.leave() };
};
```

## 关键差异总结

### 1. Token请求

- **Agora**: 使用Agora Token生成算法
- **阿里云**: 使用阿里云Token生成算法

### 2. 客户端创建

- **Agora**: `AgoraRTC.createClient({ mode: "rtc", codec: "vp8" })`
- **阿里云**: `DingRTC.createClient()`

### 3. 房间加入

- **Agora**: `client.join(appId, channel, token, uid)`
- **阿里云**: `client.join({appId, token, uid, channel, userName})`

### 4. 环境变量

- **Agora**: `REACT_APP_AGORA_APP_ID`
- **阿里云**: `REACT_APP_ALI_APP_ID`

### 5. 错误处理

- **Agora**: "Failed to get Agora token"
- **阿里云**: "Failed to get Ali RTC token"
