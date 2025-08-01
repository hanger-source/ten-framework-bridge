# AI Agents Addons

这是基于 ten-framework 构建的 AI 代理扩展模块集合。

## 目录结构

```
ai_agents/addons/
├── server/           # Java Spring Boot 服务器
│   ├── src/         # Java 源代码
│   ├── target/      # 构建输出目录
│   ├── pom.xml      # Maven 配置文件
│   ├── Dockerfile   # Docker 构建文件
│   └── README.md    # 服务器详细文档
├── docker/          # Docker 相关文件
│   ├── Dockerfile.tenBuild_java    # Java 构建环境 Dockerfile
│   └── Dockerfile.tenBuild_origin  # 原始构建环境 Dockerfile
├── logs/            # 日志文件目录
└── readme.md        # 本文件
```

## 快速开始

### 前置要求

- Java 17+
- Maven 3.6+
- Docker (可选，用于容器化部署)

### 构建和运行

#### 1. 构建 Java 服务器

**推荐：本地构建**

```bash
# 在本地构建（推荐）
cd ai_agents/addons/server
mvn clean compile package -DskipTests
```

**或者使用 Task（如果容器 Maven 可用）**：

```bash
# 在项目根目录执行
task build-addon
```

**注意**：如果容器中的 Maven 有问题，建议在本地构建 JAR 文件，然后复制到容器中运行。

#### 2. 运行服务器

```bash
# 使用 Task 运行
task run-addon
```

或者手动运行：

```bash
cd ai_agents/addons/server
java -jar target/ten-framework-server-1.0.0.jar
```

#### 3. 远程调试

```bash
# 使用默认端口 5005 进行远程调试
task run-addon-debug
```

或者指定自定义调试端口：

```bash
# 使用自定义端口进行远程调试
DEBUG_PORT=5006 task run-addon-debug
```

调试配置说明：

- **默认调试端口**: 5005
- **传输协议**: dt_socket
- **挂起模式**: n (不挂起，立即启动)
- **监听地址**: \* (监听所有网络接口)

在 IDE 中配置远程调试：

- **主机**: localhost (如果在本地) 或容器 IP
- **端口**: 5005 (默认) 或自定义端口
- **传输**: Socket

#### 3. 构建和运行完整项目

```bash
# 构建所有组件
task build-addon

# 运行完整系统
task run
```

## Docker 构建

### 构建 Java 构建环境镜像

```bash
docker build --platform linux/amd64 --no-cache -t ten-framework-build-base-java:1.0 -f ai_agents/addons/docker/Dockerfile.tenBuild_java .
```

### 构建原始构建环境镜像

```bash
docker build --platform linux/amd64 --no-cache -t ten-framework-build-base-origin:1.0 -f ai_agents/addons/docker/Dockerfile.tenBuild_origin .
```

## 开发指南

### 添加新的扩展模块

1. 在 `ai_agents/addons/` 下创建新的目录
2. 实现相应的功能
3. 在 `ai_agents/Taskfile.yml` 中添加相应的构建和运行任务
4. 更新本 README 文档

### 日志管理

- 日志文件存储在 `ai_agents/addons/logs/` 目录
- 建议使用统一的日志格式和级别

### 测试

```bash
# 运行服务器测试
cd ai_agents/addons/server
mvn test
```

## 配置说明

### 服务器配置

服务器配置文件位于 `ai_agents/addons/server/src/main/resources/application.yml`

主要配置项：

- 服务器端口
- 数据库连接
- 日志级别
- 外部服务集成

### 环境变量

可以通过环境变量覆盖配置：

```bash
export SERVER_PORT=8080
export LOG_LEVEL=DEBUG
```

## 故障排除

### 常见问题

1. **端口冲突**
   - 检查端口是否被占用
   - 修改配置文件中的端口设置

2. **内存不足**
   - 增加 JVM 堆内存：`java -Xmx2g -jar target/ten-framework-server-1.0.0.jar`
   - 如果遇到 Maven 构建内存不足，检查 Docker 容器内存配置

3. **Maven 构建失败 - 内存问题**
   - 错误信息：`[Too many errors, abort]` 或 `out of memory`
   - 解决方案：
     - 确保 Docker 容器有足够内存（建议 4GB+）
     - 在 `docker-compose.yml` 中配置内存限制
     - 使用 `MAVEN_OPTS='-Xmx2g'` 限制 Maven 内存使用

4. **构建失败**
   - 检查 Java 版本是否为 17+
   - 清理并重新构建：`mvn clean compile package`

### 日志查看

```bash
# 查看实时日志
tail -f ai_agents/addons/logs/server.log
```

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目遵循项目的整体许可证。

## 联系方式

如有问题，请通过项目 Issues 页面提交问题。

## Docker 开发环境

### 启动开发容器

```bash
# 启动 ten_agent_dev_addon 容器
docker compose up -d ten_agent_dev_addon
```

如果遇到孤儿容器警告，可以使用 `--remove-orphans` 标志清理：

```bash
docker compose up -d ten_agent_dev_addon --remove-orphans
```

### 进入开发容器

```bash
# 进入容器 bash 环境
docker exec -it ten_agent_dev_addon bash
```

### 在容器内开发

进入容器后，你可以在 `/app` 目录下进行开发：

```bash
root@c40bbf4aebc8:/app#
# 在这里可以执行各种开发命令
```

### 在容器中进行远程调试

1. **配置调试端口**：

在 `ai_agents/docker-compose.yml` 中已经配置了调试端口映射：

```yaml
ports:
  - "${DEBUG_PORT:-5005}:${DEBUG_PORT:-5005}"
```

2. **启动容器**：

```bash
# 使用默认调试端口 5005
docker compose up -d ten_agent_dev_addon

# 或指定自定义调试端口
DEBUG_PORT=5006 docker compose up -d ten_agent_dev_addon
```

3. **在容器内启动调试模式**：

```bash
docker exec -it ten_agent_dev_addon bash
cd /app/ai_agents/addons/server
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar target/ten-framework-server-1.0.0.jar
```

4. **在 IDE 中连接调试器**：
   - 主机：localhost
   - 端口：5005 (默认) 或自定义端口

### 容器管理

```bash
# 查看容器状态
docker compose ps

# 停止容器
docker compose down ten_agent_dev_addon

# 查看容器日志
docker compose logs ten_agent_dev_addon

# 重启容器
docker compose restart ten_agent_dev_addon
```
