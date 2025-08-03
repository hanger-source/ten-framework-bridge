# 实际代码迁移实施 - 3: 前端Token获取和配置迁移

## 当前Token获取分析

基于对现有代码的分析，发现：

1. **当前实现**: 前端调用后端API获取Agora RTC Token
2. **目标实现**: 支持获取阿里云RTC Token
3. **迁移策略**: 修改前端请求，支持指定RTC类型

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

**迁移为支持双RTC**:

```typescript
// ai_agents/playground/src/common/request.ts
export const requestToken = async (
  channel: string,
  uid: string,
  rtcType: "agora" | "ali" = "agora"
): Promise<string> => {
  const response = await fetch("/api/token/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      channel,
      uid,
      rtcType,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to get ${rtcType} token`);
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

**迁移为支持双RTC配置**:

```typescript
// ai_agents/playground/src/config/rtc.ts
export const rtcConfig = {
  agora: {
    appId: process.env.REACT_APP_AGORA_APP_ID,
    token: null, // 动态获取
    channel: null, // 动态设置
    uid: null, // 动态设置
  },
  ali: {
    appId: process.env.REACT_APP_ALI_APP_ID,
    token: null, // 动态获取
    channel: null, // 动态设置
    uid: null, // 动态设置
  },
  currentType: "agora" as "agora" | "ali",
};
```

### 3. RTC管理器更新

**当前Agora管理器**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
export class RTCManager {
  private client: IAgoraRTCClient;
  private config: typeof rtcConfig;

  constructor() {
    this.client = AgoraRTC.createClient({
      mode: "rtc",
      codec: "vp8",
    });
    this.config = rtcConfig;
  }

  async joinChannel(channel: string, uid: string) {
    // 获取Token
    const token = await requestToken(channel, uid);

    // 加入房间
    await this.client.join(token, channel, uid);
  }
}
```

**迁移为支持双RTC管理器**:

```typescript
// ai_agents/playground/src/components/RTCManager.ts
export class RTCManager {
  private client: IAgoraRTCClient | DingRTCClient;
  private config: typeof rtcConfig;
  private rtcType: "agora" | "ali";

  constructor(rtcType: "agora" | "ali" = "agora") {
    this.rtcType = rtcType;
    this.config = rtcConfig;

    if (rtcType === "ali") {
      this.client = DingRTC.createClient();
    } else {
      this.client = AgoraRTC.createClient({
        mode: "rtc",
        codec: "vp8",
      });
    }
  }

  async joinChannel(channel: string, uid: string) {
    // 获取Token
    const token = await requestToken(channel, uid, this.rtcType);

    // 加入房间
    await this.client.join(token, channel, uid);
  }
}
```

## 环境变量迁移

### 1. 环境变量配置

**当前环境变量**:

```bash
# .env
REACT_APP_AGORA_APP_ID=your_agora_app_id
```

**迁移后环境变量**:

```bash
# .env
REACT_APP_AGORA_APP_ID=your_agora_app_id
REACT_APP_ALI_APP_ID=your_ali_app_id
REACT_APP_DEFAULT_RTC_TYPE=agora
```

### 2. 环境变量读取

**当前读取方式**:

```typescript
// ai_agents/playground/src/config/env.ts
export const env = {
  AGORA_APP_ID: process.env.REACT_APP_AGORA_APP_ID,
};
```

**迁移后读取方式**:

```typescript
// ai_agents/playground/src/config/env.ts
export const env = {
  AGORA_APP_ID: process.env.REACT_APP_AGORA_APP_ID,
  ALI_APP_ID: process.env.REACT_APP_ALI_APP_ID,
  DEFAULT_RTC_TYPE: process.env.REACT_APP_DEFAULT_RTC_TYPE || "agora",
};
```

## 组件迁移

### 1. RTC连接组件

**当前Agora组件**:

```typescript
// ai_agents/playground/src/components/RTCConnection.tsx
import { useRTC } from '../hooks/useRTC';

export const RTCConnection: React.FC = () => {
  const { joinChannel, leaveChannel } = useRTC();

  const handleJoin = async () => {
    try {
      await joinChannel('test-channel', 'user-123');
    } catch (error) {
      console.error('Failed to join channel:', error);
    }
  };

  return (
    <div>
      <button onClick={handleJoin}>Join Channel</button>
      <button onClick={leaveChannel}>Leave Channel</button>
    </div>
  );
};
```

**迁移为支持双RTC组件**:

```typescript
// ai_agents/playground/src/components/RTCConnection.tsx
import { useRTC } from '../hooks/useRTC';

interface RTCConnectionProps {
  rtcType?: 'agora' | 'ali';
}

export const RTCConnection: React.FC<RTCConnectionProps> = ({
  rtcType = 'agora'
}) => {
  const { joinChannel, leaveChannel } = useRTC(rtcType);

  const handleJoin = async () => {
    try {
      await joinChannel('test-channel', 'user-123');
    } catch (error) {
      console.error(`Failed to join ${rtcType} channel:`, error);
    }
  };

  return (
    <div>
      <div>Current RTC: {rtcType}</div>
      <button onClick={handleJoin}>Join {rtcType.toUpperCase()} Channel</button>
      <button onClick={leaveChannel}>Leave Channel</button>
    </div>
  );
};
```

### 2. RTC Hook更新

**当前Agora Hook**:

```typescript
// ai_agents/playground/src/hooks/useRTC.ts
export const useRTC = () => {
  const [client, setClient] = useState<IAgoraRTCClient | null>(null);

  const joinChannel = useCallback(async (channel: string, uid: string) => {
    const rtcClient = AgoraRTC.createClient({
      mode: "rtc",
      codec: "vp8",
    });

    const token = await requestToken(channel, uid);
    await rtcClient.join(token, channel, uid);

    setClient(rtcClient);
  }, []);

  return { joinChannel, leaveChannel: () => client?.leave() };
};
```

**迁移为支持双RTC Hook**:

```typescript
// ai_agents/playground/src/hooks/useRTC.ts
export const useRTC = (rtcType: "agora" | "ali" = "agora") => {
  const [client, setClient] = useState<IAgoraRTCClient | DingRTCClient | null>(
    null
  );

  const joinChannel = useCallback(
    async (channel: string, uid: string) => {
      let rtcClient;

      if (rtcType === "ali") {
        rtcClient = DingRTC.createClient();
      } else {
        rtcClient = AgoraRTC.createClient({
          mode: "rtc",
          codec: "vp8",
        });
      }

      const token = await requestToken(channel, uid, rtcType);
      await rtcClient.join(token, channel, uid);

      setClient(rtcClient);
    },
    [rtcType]
  );

  return { joinChannel, leaveChannel: () => client?.leave() };
};
```

## 错误处理迁移

### 1. Token获取错误处理

**当前错误处理**:

```typescript
try {
  const token = await requestToken(channel, uid);
  // 使用token
} catch (error) {
  console.error("Failed to get token:", error);
  // 显示错误信息
}
```

**迁移后错误处理**:

```typescript
try {
  const token = await requestToken(channel, uid, rtcType);
  // 使用token
} catch (error) {
  console.error(`Failed to get ${rtcType} token:`, error);
  // 显示错误信息
}
```

### 2. 连接错误处理

**当前连接错误处理**:

```typescript
try {
  await client.join(token, channel, uid);
} catch (error) {
  console.error("Failed to join Agora channel:", error);
}
```

**迁移后连接错误处理**:

```typescript
try {
  await client.join(token, channel, uid);
} catch (error) {
  console.error(`Failed to join ${rtcType} channel:`, error);
}
```

## 关键差异总结

### 1. Token请求

- **Agora**: 请求参数包含channel和uid
- **阿里云**: 请求参数包含channel、uid和rtcType

### 2. 客户端创建

- **Agora**: 需要配置参数
- **阿里云**: 无需配置参数

### 3. 环境变量

- **Agora**: 只需要AGORA_APP_ID
- **阿里云**: 需要ALI_APP_ID

### 4. 错误信息

- **Agora**: 错误信息不包含RTC类型
- **阿里云**: 错误信息包含RTC类型标识

### 5. 向后兼容性

- 保持原有接口不变
- 新增rtcType参数支持动态切换
- 默认使用Agora RTC
