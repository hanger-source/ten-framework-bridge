# Worker配置迁移 - 2: 后端Worker管理

## 当前 Worker 启动代码

```java
// ai_agents/addons/server/src/main/java/.../WorkerManager.java
private void startWorkerProcess(Worker worker, StartRequest request) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(WORKER_EXEC);
    command.add("--channel");
    command.add(worker.getChannelName());
    command.add("--port");
    command.add(String.valueOf(worker.getHttpServerPort()));

    ProcessBuilder pb = new ProcessBuilder(command);
    Process process = pb.start();
}
```

## 需要修改的具体代码

### 1. 添加 RTC 类型参数传递

```java
// 修改后的 Worker 启动代码
private void startWorkerProcess(Worker worker, StartRequest request) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(WORKER_EXEC);
    command.add("--channel");
    command.add(worker.getChannelName());
    command.add("--port");
    command.add(String.valueOf(worker.getHttpServerPort()));
    command.add("--rtc-type");
    command.add(request.getRtcType()); // 新增 RTC 类型参数

    ProcessBuilder pb = new ProcessBuilder(command);
    Process process = pb.start();
}
```

### 2. 修改 StartRequest 类

```java
// 修改 StartRequest 类
public class StartRequest {
    private String channelName;
    private String rtcType = "agora"; // 默认使用 Agora

    // 现有字段
    // private int port;
    // private String token;

    // getters and setters
    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getRtcType() {
        return rtcType;
    }

    public void setRtcType(String rtcType) {
        this.rtcType = rtcType;
    }
}
```

### 3. 修改 Worker 实体类

```java
// 修改 Worker 实体类
@Entity
public class Worker {
    // 现有字段
    private String channelName;
    private int httpServerPort;
    private String status;

    // 新增字段
    private String rtcType = "agora"; // 默认使用 Agora

    // getters and setters
    public String getRtcType() {
        return rtcType;
    }

    public void setRtcType(String rtcType) {
        this.rtcType = rtcType;
    }
}
```

## 实际文件修改清单

**需要修改的文件**:

- `ai_agents/addons/server/src/main/java/.../WorkerManager.java`
- `ai_agents/addons/server/src/main/java/.../StartRequest.java`
- `ai_agents/addons/server/src/main/java/.../Worker.java`

**需要新增的配置**:

- 在数据库中添加 `rtc_type` 字段
- 在配置文件中添加默认 RTC 类型设置
