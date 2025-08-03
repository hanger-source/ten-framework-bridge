# Worker配置迁移 - 1: 现有Worker代码

## 当前 Worker 启动代码

```go
// ai_agents/agents/main.go
package main

import (
    "flag"
    "log"
)

func main() {
    channelName := flag.String("channel", "", "Channel name")
    port := flag.Int("port", 0, "HTTP server port")
    flag.Parse()

    if *channelName == "" {
        log.Fatal("Channel name is required")
    }

    if *port == 0 {
        log.Fatal("Port is required")
    }

    // 启动 HTTP 服务器
    startHTTPServer(*port)
}
```

## 需要修改的具体代码

### 1. 添加 RTC 类型参数

```go
// 修改后的 main.go
func main() {
    channelName := flag.String("channel", "", "Channel name")
    port := flag.Int("port", 0, "HTTP server port")
    rtcType := flag.String("rtc-type", "agora", "RTC type: agora or ali")
    flag.Parse()

    if *channelName == "" {
        log.Fatal("Channel name is required")
    }

    if *port == 0 {
        log.Fatal("Port is required")
    }

    // 根据 RTC 类型初始化不同的客户端
    initRTCClient(*rtcType)

    // 启动 HTTP 服务器
    startHTTPServer(*port)
}
```

### 2. 添加 RTC 客户端初始化

```go
// 新增的 RTC 客户端初始化函数
func initRTCClient(rtcType string) {
    switch rtcType {
    case "ali":
        log.Printf("Initializing Ali RTC client for channel")
        // 初始化阿里云 RTC 客户端
    case "agora":
        log.Printf("Initializing Agora RTC client for channel")
        // 初始化 Agora RTC 客户端
    default:
        log.Printf("Unknown RTC type: %s, using Agora", rtcType)
        // 默认使用 Agora
    }
}
```

## 实际文件修改清单

**需要修改的文件**:

- `ai_agents/agents/main.go`
- `ai_agents/addons/server/src/main/java/.../WorkerManager.java`

**需要新增的文件**:

- `ai_agents/agents/rtc/agora_client.go`
- `ai_agents/agents/rtc/ali_client.go`
- `ai_agents/agents/rtc/client.go` (接口定义)
