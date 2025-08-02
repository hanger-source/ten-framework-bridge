# Build.sh 使用说明

## 基本用法

```bash
# 构建所有项目（默认）
./build.sh

# 只构建Go项目（agent + server）
./build.sh --go

# 只构建Java项目（addon server）
./build.sh --java

# 构建所有项目（显式指定）
./build.sh --all

# 显示帮助信息
./build.sh --help
```

## 构建类型说明

### 1. `--go` - 只构建Go项目

构建以下Go项目：

- **Agent项目**：`ai_agents/agents/` (agent组件)
- **Server项目**：`ai_agents/server/` (主服务器)

```bash
./build.sh --go
```

### 2. `--java` - 构建Java addon服务器和agent

构建以下项目：

- **Addon Server项目**：`ai_agents/addons/server/` (addon服务器)
- **Agent项目**：`ai_agents/agents/` (agent组件)

```bash
./build.sh --java
```

### 3. `--all` - 构建所有项目（默认）

构建所有项目：

- Java Addon Server
- Go Agent
- Go Server

```bash
./build.sh --all
# 或者直接运行
./build.sh
```

## 构建结果

### Go项目构建结果

- `ai_agents/agents/bin/worker` - Agent二进制文件
- `ai_agents/server/bin/api` - Server二进制文件

### Java项目构建结果

- `ai_agents/addons/server/target/ten-framework-server-1.0.0.jar` - Addon服务器JAR文件
- `ai_agents/agents/bin/worker` - Agent二进制文件

## 环境要求

### Go项目要求

- Go 1.16 或更高版本
- 相关Go依赖

### Java项目要求

- Java 8 或更高版本
- Maven 3.6 或更高版本

## 使用场景

### 开发Java Addon时

```bash
# 构建Java addon服务器和agent，快速迭代
./build.sh --java
```

### 开发Go组件时

```bash
# 只构建Go项目，避免Java构建时间
./build.sh --go
```

### 完整构建

```bash
# 构建所有项目，用于完整测试
./build.sh --all
```

## 故障排除

### 构建失败

1. 检查环境要求是否满足
2. 运行 `./clean.sh` 清理后重新构建
3. 检查网络连接（Maven依赖下载）

### 权限问题

```bash
chmod +x build.sh
```

### 路径问题

确保在正确的目录下运行脚本：

```bash
cd ai_agents/addons
./build.sh
```
