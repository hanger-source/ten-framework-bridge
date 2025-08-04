# 前端 Token 获取迁移细节

## 1. 当前 Token 获取流程分析

### 1.1 当前 API 调用

```typescript
// ai_agents/playground/src/common/request.ts
export const apiGenAgoraData = async (config: GenAgoraDataConfig) => {
  const url = `/api/token/generate`;
  const { userId, channel } = config;
  const data = {
    request_id: genUUID(),
    uid: userId,
    channel_name: channel,
  };
  let resp: any = await axios.post(url, data);
  resp = resp.data || {};
  return resp;
};
```

### 1.2 当前使用场景

```typescript
// ai_agents/playground/src/manager/rtc/rtc.ts
async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: any }) {
  if (!this._joined) {
    let appId: string;
    let finalToken: string;

    if (agentSettings?.token) {
      // 使用预设 token
      appId = agentSettings?.env?.AGORA_APP_ID || process.env.NEXT_PUBLIC_AGORA_APP_ID || '';
      finalToken = agentSettings.token;
    } else {
      // 调用 generate API
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
```

## 2. 阿里云 RTC Token 获取分析

### 2.1 阿里云 Demo 中的 Token 获取

```typescript
// alirtc_demo/reactVersion/src/utils/request.ts
export const getAppToken = async (
  userId: string,
  appId: string,
  channelId: string
): Promise<{ token: string; gslb?: string[] }> => {
  const loginParam = {
    channelId,
    appId,
    userId,
    appKey: configJson.appKey,
  };
  const result = await request(`${APP_SERVER_DOMAIN}/login`, loginParam);
  return result;
};
```

### 2.2 阿里云 Demo 中的使用

```typescript
// alirtc_demo/reactVersion/src/pages/Welcome/components/Join.tsx
const appTokenResult = await getAppToken(uid, app, channelName);
const result = await newClient.join({
  appId: loginParam.appId,
  token: loginParam.appToken,
  uid: loginParam.userId,
  channel: loginParam.channelName,
  userName: loginParam.userName,
});
```

## 3. 迁移实施步骤

### 3.1 修改 API 接口定义

**步骤 1: 扩展配置接口**

```typescript
// ai_agents/playground/src/common/request.ts
interface GenRtcDataConfig {
  userId: string | number;
  channel: string;
  rtcType?: string; // 新增字段
}

interface GenAgoraDataConfig {
  userId: string | number;
  channel: string;
}
```

### 3.2 创建统一的 Token 获取函数

**步骤 2: 实现统一的 API 调用**

```typescript
// ai_agents/playground/src/common/request.ts
export const apiGenRtcData = async (config: GenRtcDataConfig) => {
  const url = `/api/token/generate`;
  const { userId, channel, rtcType = "agora" } = config;
  const data = {
    request_id: genUUID(),
    uid: userId,
    channel_name: channel,
  };

  // 添加 RTC 类型参数
  const params = new URLSearchParams({ rtcType });
  let resp: any = await axios.post(`${url}?${params}`, data);
  resp = resp.data || {};
  return resp;
};

// 保持向后兼容
export const apiGenAgoraData = async (config: GenAgoraDataConfig) => {
  return apiGenRtcData({ ...config, rtcType: "agora" });
};
```

### 3.3 修改 RTC 管理器

**步骤 3: 更新 RTC 管理器使用**

```typescript
// ai_agents/playground/src/manager/rtc/rtc.ts
async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: any }) {
  if (!this._joined) {
    let appId: string;
    let finalToken: string;
    const rtcType = agentSettings?.rtcType || 'agora';

    if (agentSettings?.token) {
      // 使用预设 token
      if (rtcType === 'ali') {
        appId = agentSettings?.env?.ALI_RTC_APP_ID || process.env.NEXT_PUBLIC_ALI_RTC_APP_ID || '';
      } else {
        appId = agentSettings?.env?.AGORA_APP_ID || process.env.NEXT_PUBLIC_AGORA_APP_ID || '';
      }
      finalToken = agentSettings.token;
    } else {
      // 调用 generate API
      const res = await apiGenRtcData({ channel, userId, rtcType });
      const { code, data } = res;
      if (code != 0) {
        throw new Error(`Failed to get ${rtcType} token`);
      }
      appId = data.appId;
      finalToken = data.token;
    }

    // 验证 App ID 不为空
    if (!appId || appId.trim() === '') {
      const rtcName = rtcType === 'ali' ? '阿里云 RTC' : '声网';
      throw new Error(`${rtcName} App ID 不能为空，请在设置中配置 ${rtcName} App ID`);
    }

    this.appId = appId;
    this.token = finalToken;
    this.userId = userId;
    await this.client?.join(appId, channel, finalToken, userId);
    this._joined = true;
  }
}
```

### 3.4 添加配置管理

**步骤 4: 更新 Agent 设置**

```typescript
// ai_agents/playground/src/hooks/useAgentSettings.ts
export interface IAgentSettings {
  rtcType?: "agora" | "ali";
  token?: string;
  env?: {
    AGORA_APP_ID?: string;
    ALI_RTC_APP_ID?: string;
    ALI_RTC_APP_KEY?: string;
  };
}

export const getAgentSettings = (): IAgentSettings => {
  return {
    rtcType: (process.env.NEXT_PUBLIC_RTC_TYPE as "agora" | "ali") || "agora",
    env: {
      AGORA_APP_ID: process.env.NEXT_PUBLIC_AGORA_APP_ID,
      ALI_RTC_APP_ID: process.env.NEXT_PUBLIC_ALI_RTC_APP_ID,
      ALI_RTC_APP_KEY: process.env.NEXT_PUBLIC_ALI_RTC_APP_KEY,
    },
  };
};
```

### 3.5 添加错误处理

**步骤 5: 增强错误处理**

```typescript
// ai_agents/playground/src/manager/rtc/rtc.ts
async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: any }) {
  try {
    // ... 现有逻辑
  } catch (error) {
    const rtcType = agentSettings?.rtcType || 'agora';
    const rtcName = rtcType === 'ali' ? '阿里云 RTC' : '声网';

    if (error.message.includes('Failed to get')) {
      throw new Error(`${rtcName} Token 获取失败，请检查网络连接和配置`);
    } else if (error.message.includes('App ID')) {
      throw new Error(`${rtcName} App ID 配置错误，请检查环境变量设置`);
    } else {
      throw new Error(`${rtcName} 连接失败: ${error.message}`);
    }
  }
}
```

## 4. 环境变量配置

### 4.1 前端环境变量

```bash
# .env.local
NEXT_PUBLIC_RTC_TYPE=agora  # 或 ali
NEXT_PUBLIC_AGORA_APP_ID=your_agora_app_id
NEXT_PUBLIC_ALI_RTC_APP_ID=your_ali_rtc_app_id
NEXT_PUBLIC_ALI_RTC_APP_KEY=your_ali_rtc_app_key
```

### 4.2 后端环境变量

```properties
# ai_agents/addons/server/application.properties
# 阿里云 RTC 配置
ali.rtc.app.id=your_ali_rtc_app_id
ali.rtc.app.key=your_ali_rtc_app_key
rtc.type=agora
```

## 6. 向后兼容性

### 6.1 保持现有 API

```typescript
// 保持现有的 apiGenAgoraData 函数
export const apiGenAgoraData = async (config: GenAgoraDataConfig) => {
  return apiGenRtcData({ ...config, rtcType: "agora" });
};
```

### 6.2 默认行为

```typescript
// 默认使用 Agora RTC
const rtcType = agentSettings?.rtcType || "agora";
```

## 7. 注意事项

1. **向后兼容**: 保持现有的 `apiGenAgoraData` 函数不变
2. **错误处理**: 添加 RTC 类型相关的错误信息
3. **配置验证**: 确保环境变量正确配置

4. **安全考虑**: 确保敏感配置信息安全存储
