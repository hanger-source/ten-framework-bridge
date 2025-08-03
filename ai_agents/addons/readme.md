# AI Agents Addons

基于 ten-framework 构建的 AI 代理扩展模块集合，提供完整的语音助手解决方案。

## 🚀 快速开始

### 前置要求

- **Java**: 17+
- **Maven**: 3.6+
- **Docker**: 20.10+ (可选，用于容器化部署)
- **Node.js**: 18+ (前端开发)
- **Bun**: 1.0+ (前端构建)

### 一键启动

```bash
# 启动完整系统（推荐）
cd ai_agents/addons/deploy
docker-compose up -d

# 访问前端界面
open http://localhost:3000
```

## 📁 项目结构

```
ai_agents/addons/
├── server/                    # Java Spring Boot 服务器
│   ├── src/main/java/        # Java 源代码
│   ├── src/main/resources/   # 配置文件
│   ├── target/               # 构建输出
│   ├── pom.xml              # Maven 配置
│   └── Dockerfile           # Docker 构建文件
├── deploy/                   # 部署相关文件
│   ├── Dockerfile.multi-stage # 多阶段 Docker 构建
│   ├── docker-compose.yml    # Docker Compose 配置
│   └── logs/                # 日志目录
├── docker/                   # Docker 构建环境
│   ├── Dockerfile.tenBuild_java    # Java 构建环境
│   └── Dockerfile.tenBuild_origin # 原始构建环境
└── README.md                # 本文档
```

## 🛠️ 开发环境

### 后端开发

```bash
# 启动后端开发容器
cd ai_agents
docker-compose up -d ten_agent_dev_addon

# 进入容器
docker exec -it ten-agent-dev-addon bash

# 在容器内构建和运行
cd /app/addons/server
mvn clean package -DskipTests
java -jar target/ten-framework-server-1.0.0.jar
```

### 前端开发

```bash
# 启动前端开发容器
cd ai_agents
docker-compose up -d ten_agent_playground_addon

# 本地开发（推荐）
cd ai_agents/playground
bun install
bun run dev
```

### 远程调试

```bash
# 启动调试模式
DEBUG_PORT=5005 docker-compose up -d ten_agent_dev_addon

# 在 IDE 中配置远程调试
# 主机: localhost
# 端口: 5005
# 传输: Socket
```

## 🐳 Docker 部署

### 构建镜像

```bash
# 构建完整镜像
cd ai_agents
docker build --platform linux/amd64 --no-cache \
  -f addons/deploy/Dockerfile.multi-stage \
  -t ten-agent-deploy:1.0 \
  ../..
```

### 启动服务

```bash
# 启动部署容器
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 服务端口

| 服务        | 端口  | 说明            |
| ----------- | ----- | --------------- |
| 前端界面    | 3000  | Playground 应用 |
| Java 服务器 | 8080  | 后端 API        |
| Go 服务器   | 49483 | 开发服务器      |
| 调试端口    | 5005  | 远程调试        |

## 🔧 配置说明

### 环境变量

```bash
# 服务器配置
SERVER_PORT=8080              # 服务器端口
# DEBUG_PORT=5005               # 调试端口
LOG_LEVEL=info               # 日志级别

# 前端配置
AGENT_SERVER_URL=http://localhost:8080
TEN_DEV_SERVER_URL=http://localhost:49483
NEXT_PUBLIC_EDIT_GRAPH_MODE=false

# 部署配置
DEFAULT_MODE=go              # 默认启动模式: go|java
AUTO_START=true              # 自动启动服务
```

### 配置文件

<!-- - **Java 配置**: `ai_agents/addons/server/src/main/resources/application.yml` -->

- **前端配置**: `ai_agents/playground/.env`
- **Docker 配置**: `ai_agents/addons/deploy/docker-compose.yml`

## 🔍 故障排除

### 常见问题

#### 1. 端口冲突

```bash
# 检查端口占用
lsof -i :8080
lsof -i :3000

# 修改端口配置
export SERVER_PORT=8081
export DEBUG_PORT=5006
```

#### 2. 内存不足

```bash
# 增加 JVM 堆内存
java -Xmx4g -jar target/ten-framework-server-1.0.0.jar

# 增加 Docker 容器内存
docker-compose up -d --memory=4g
```

#### 3. 构建失败

```bash
# 清理并重新构建
mvn clean compile package -DskipTests

# 清理 Docker 缓存
docker system prune -a
```

#### 4. 前端构建失败

```bash
# 清理依赖
rm -rf node_modules
bun install

# 重新构建
bun run build
```

### 日志查看

```bash
# 查看实时日志
docker-compose logs -f ten-agent-deploy

# 查看特定服务日志
docker-compose logs -f server
docker-compose logs -f playground

# 查看本地日志文件
tail -f ai_agents/addons/logs/server.log
```

## 📊 监控和调试

### 健康检查

```bash
# 检查服务状态
curl http://localhost:8080/health
curl http://localhost:3000/api/health

# 查看容器状态
docker-compose ps
```

### 性能监控

```bash
# 查看容器资源使用
docker stats

# 查看 JVM 内存使用
jstat -gc <pid>
```

## 🔄 更新和升级

### 更新代码

```bash
# 拉取最新代码
git pull origin main

# 重新构建镜像
docker-compose build --no-cache

# 重启服务
docker-compose up -d
```
