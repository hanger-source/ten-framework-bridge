# 阿里云 RTC 加入房间分析

## 1. 阿里云 RTC 加入房间流程

### 1.1 完整的加入房间流程

```typescript
// alirtc_demo/reactVersion/src/pages/Welcome/components/Join.tsx
const onJoin = useCallback(async () => {
  const {
    userId: uid,
    appId: app,
    channelName,
    userName: name,
  } = await form.getFieldsValue();

  if (!uid || !app || !channelName || !name) {
    Toast.error("请检查参数填写");
    return;
  }

  setJoining(true);
  try {
    // 1. 获取 Token
    const appTokenResult = await getAppToken(uid, app, channelName);

    // 2. 准备登录参数
    const loginParam = {
      appId: app,
      userId: uid,
      userName,
      channelName,
      appToken: appTokenResult.token,
    };

    // 3. 加入房间
    const result = await newClient.join({
      appId: loginParam.appId,
      token: loginParam.appToken,
      uid: loginParam.userId,
      channel: loginParam.channelName,
      userName: loginParam.userName,
    });

    // 注意：阿里云RTC的join方法参数结构与Agora不同
    // Agora: client.join(appId, channel, token, uid)
    // 阿里云: client.join({appId, token, uid, channel, userName})

    // 4. 处理远程用户
    setRemoteChannelInfo((prev) => ({
      ...prev,
      remoteUsers: result.remoteUsers,
    }));

    // 5. 批量订阅远程用户
    const subParams: SubscribeParam[] = [
      { uid: "mcu", mediaType: "audio", auxiliary: false },
    ];

    for (const user of result.remoteUsers) {
      if (user.hasAuxiliary) {
        subParams.push({
          uid: user.userId,
          mediaType: "video",
          auxiliary: true,
        });
      }
      if (user.hasVideo) {
        subParams.push({
          uid: user.userId,
          mediaType: "video",
          auxiliary: false,
        });
      }
    }

    // 6. 执行批量订阅
    const subTask = newClient
      .batchSubscribe(subParams)
      .then((batchSubscribeResult) => {
        for (const {
          error,
          track,
          uid: usrId,
          auxiliary,
        } of batchSubscribeResult) {
          if (error) {
            Toast.info(
              `subscribe user ${usrId} ${auxiliary ? "screenShare" : "camera"} failed: ${JSON.stringify(error)}`
            );
            continue;
          }
          if (track.trackMediaType === "audio") {
            const audioTrack = track as RemoteAudioTrack;
            setRemoteChannelInfo((prev) => ({
              ...prev,
              mcuAudioTrack: audioTrack,
              subscribeAudio: "mcu",
            }));
            audioTrack.play();
          } else {
            setRemoteChannelInfo((prev) => ({
              ...prev,
              remoteUsers: [...newClient.remoteUsers],
            }));
          }
        }
      });

    // 7. 发布本地轨道
    const localTracks = [clientInfo.cameraTrack, clientInfo.micTrack].filter(
      (item) => !!item
    );
    if (localTracks.length) {
      const pubTask = newClient?.publish(localTracks).then(() => {
        setLocalChannelInfo((prev) => ({
          ...prev,
          publishedTracks: [...newClient.localTracks],
        }));
      });
      tasks.push(pubTask);
    }

    // 8. 等待所有任务完成
    Promise.all(tasks).finally(() => {
      setGlobalData((pre) => ({ ...pre, joined: true }));
    });
  } catch (e: any) {
    setJoining(false);
    setGlobalData((pre) => ({ ...pre, joined: false }));
    Toast.error(`加入房间失败${e?.reason || e?.message || JSON.stringify(e)}`);
  }
}, [form, clientInfo]);
```

### 1.2 与 Agora RTC 加入房间对比

```typescript
// Agora RTC 加入房间
async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: any }) {
  if (!this._joined) {
    let appId: string;
    let finalToken: string;

    if (agentSettings?.token) {
      appId = agentSettings?.env?.AGORA_APP_ID || process.env.NEXT_PUBLIC_AGORA_APP_ID || '';
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
```

**关键差异**:

- **阿里云**: 需要 `userName` 参数，支持批量订阅
- **Agora**: 只需要 `appId`, `channel`, `token`, `uid`

## 2. Token 获取流程

### 2.1 阿里云 RTC Token 获取

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

### 2.2 与 Agora RTC Token 获取对比

```typescript
// Agora RTC Token 获取
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

**关键差异**:

- **阿里云**: 需要 `appKey` 参数，返回包含 `gslb` 信息
- **Agora**: 需要 `appCertificate` 参数，返回包含 `appId` 信息

## 3. 批量订阅机制

### 3.1 阿里云 RTC 批量订阅

```typescript
// alirtc_demo/reactVersion/src/pages/Welcome/components/Join.tsx
const subParams: SubscribeParam[] = [
  { uid: "mcu", mediaType: "audio", auxiliary: false },
];

for (const user of result.remoteUsers) {
  if (user.hasAuxiliary) {
    subParams.push({ uid: user.userId, mediaType: "video", auxiliary: true });
  }
  if (user.hasVideo) {
    subParams.push({ uid: user.userId, mediaType: "video", auxiliary: false });
  }
}

const subTask = newClient
  .batchSubscribe(subParams)
  .then((batchSubscribeResult) => {
    for (const {
      error,
      track,
      uid: usrId,
      auxiliary,
    } of batchSubscribeResult) {
      if (error) {
        Toast.info(
          `subscribe user ${usrId} ${auxiliary ? "screenShare" : "camera"} failed: ${JSON.stringify(error)}`
        );
        continue;
      }
      if (track.trackMediaType === "audio") {
        const audioTrack = track as RemoteAudioTrack;
        audioTrack.play();
      }
    }
  });
```

### 3.2 与 Agora RTC 订阅对比

```typescript
// Agora RTC 订阅
this.client.on("user-published", async (user, mediaType) => {
  await this.client.subscribe(user, mediaType);
  if (mediaType === "audio") {
    this._playAudio(user.audioTrack);
  }
  this.emit("remoteUserChanged", {
    userId: user.uid,
    audioTrack: user.audioTrack,
    videoTrack: user.videoTrack,
  });
});
```

**关键差异**:

- **阿里云**: 使用批量订阅，一次性订阅多个用户
- **Agora**: 使用事件驱动，逐个订阅用户

## 4. 轨道发布机制

### 4.1 阿里云 RTC 轨道发布

```typescript
// alirtc_demo/reactVersion/src/pages/Welcome/components/Join.tsx
const localTracks = [clientInfo.cameraTrack, clientInfo.micTrack].filter(
  (item) => !!item
);
if (localTracks.length) {
  const pubTask = newClient
    ?.publish(localTracks)
    .then(() => {
      setLocalChannelInfo((prev) => ({
        ...prev,
        publishedTracks: [...newClient.localTracks],
      }));
    })
    .catch((e) => {
      Toast.info(
        `publish ${localTracks.map((item) => item.trackMediaType)} tracks failed: ${JSON.stringify(e)}`
      );
      throw e;
    });
  tasks.push(pubTask);
}
```

### 4.2 与 Agora RTC 轨道发布对比

```typescript
// Agora RTC 轨道发布
async publish() {
  const tracks = [];
  if (this.localTracks.videoTrack) {
    tracks.push(this.localTracks.videoTrack);
  }
  if (this.localTracks.audioTrack) {
    tracks.push(this.localTracks.audioTrack);
  }
  if (tracks.length) {
    await this.client.publish(tracks);
  }
}
```

**关键差异**:

- **阿里云**: 支持 Promise 链式调用，错误处理更详细
- **Agora**: 简单的同步发布

## 5. 错误处理机制

### 5.1 阿里云 RTC 错误处理

```typescript
// alirtc_demo/reactVersion/src/pages/Welcome/components/Join.tsx
try {
  const result = await newClient.join({
    appId: loginParam.appId,
    token: loginParam.appToken,
    uid: loginParam.userId,
    channel: loginParam.channelName,
    userName: loginParam.userName,
  });
} catch (e: any) {
  setJoining(false);
  setGlobalData((pre) => ({ ...pre, joined: false }));
  Toast.error(`加入房间失败${e?.reason || e?.message || JSON.stringify(e)}`);
}
```

### 5.2 与 Agora RTC 错误处理对比

```typescript
// Agora RTC 错误处理
try {
  await this.client?.join(appId, channel, finalToken, userId);
  this._joined = true;
} catch (error) {
  console.error("Failed to join channel:", error);
  throw error;
}
```

**关键差异**:

- **阿里云**: 错误信息更详细，包含 `reason` 和 `message`
- **Agora**: 错误信息相对简单

## 6. 迁移实施要点

### 6.1 参数适配

```typescript
// 迁移后的加入房间
async join({ channel, userId, agentSettings }: { channel: string; userId: number; agentSettings?: any }) {
  const rtcType = agentSettings?.rtcType || 'agora';

  if (rtcType === 'ali') {
    // 阿里云 RTC 加入房间
    const result = await this.client.join({
      appId: this.appId,
      token: this.token,
      uid: userId,
      channel,
      userName: `user-${userId}`,
    });

    // 处理远程用户
    this._handleRemoteUsers(result.remoteUsers);
  } else {
    // Agora RTC 加入房间
    await this.client?.join(this.appId, channel, this.token, userId);
  }
}
```

### 6.2 订阅机制适配

```typescript
// 迁移后的订阅机制
private _handleRemoteUsers(remoteUsers: any[]) {
  if (this.rtcType === 'ali') {
    // 阿里云 RTC 批量订阅
    const subParams: SubscribeParam[] = [
      { uid: 'mcu', mediaType: 'audio', auxiliary: false }
    ];

    for (const user of remoteUsers) {
      if (user.hasAuxiliary) {
        subParams.push({ uid: user.userId, mediaType: 'video', auxiliary: true });
      }
      if (user.hasVideo) {
        subParams.push({ uid: user.userId, mediaType: 'video', auxiliary: false });
      }
    }

    this.client.batchSubscribe(subParams);
  } else {
    // Agora RTC 事件驱动订阅
    // 通过事件监听处理
  }
}
```

### 6.3 错误处理适配

```typescript
// 迁移后的错误处理
private _handleError(error: any, rtcType: string) {
  const rtcName = rtcType === 'ali' ? '阿里云 RTC' : '声网';

  if (error?.reason) {
    throw new Error(`${rtcName} 加入房间失败: ${error.reason}`);
  } else if (error?.message) {
    throw new Error(`${rtcName} 加入房间失败: ${error.message}`);
  } else {
    throw new Error(`${rtcName} 加入房间失败: ${JSON.stringify(error)}`);
  }
}
```

## 7. 关键发现

### 7.1 架构差异

- **阿里云**: 批量操作，一次性处理多个用户
- **Agora**: 事件驱动，逐个处理用户

### 7.2 参数差异

- **阿里云**: 需要 `userName` 参数
- **Agora**: 不需要 `userName` 参数

### 7.3 错误处理差异

- **阿里云**: 错误信息更详细
- **Agora**: 错误信息相对简单

## 8. 迁移策略

### 8.1 渐进式迁移

1. 保持现有的 Agora RTC 加入房间逻辑
2. 添加阿里云 RTC 特定的加入房间逻辑
3. 实现参数映射和转换

### 8.2 订阅机制适配

1. 统一订阅接口
2. 支持批量订阅和事件驱动订阅
3. 保持向后兼容性

### 8.3 错误处理适配

1. 统一错误处理接口
2. 支持不同 RTC 服务的错误信息
3. 提供友好的错误提示
