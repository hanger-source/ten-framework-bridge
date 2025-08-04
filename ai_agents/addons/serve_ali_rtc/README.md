# TEN Framework Server - Spring Java Version

基于Spring MVC框架的TEN Framework服务器实现，**完全按照`ai_agents/server`的Go代码逻辑进行一比一复刻**，包含所有功能，**无任何简化实现**。

## 功能特性

### 核心功能

- **Worker管理**: 启动、停止、监控worker进程，完全按照Go代码逻辑
- **RTC Token生成**: 基于Agora官方算法的RTC token生成，使用完整的HMAC-SHA1签名
- **健康检查**: 服务器状态监控
- **文件上传**: 支持向量文档上传，包含完整的MD5哈希和文件处理
- **配置管理**: 环境变量和配置文件支持
- **定时清理**: 自动清理超时的worker进程
- **JSON处理**: 使用完整的Jackson实现，无任何简化

### API端点（完全对应Go代码）

#### 基础端点

- `GET /` - 健康检查
- `GET /health` - 健康检查
- `GET /list` - 获取所有worker列表
- `POST /ping` - Ping测试

#### Worker管理

- `POST /start` - 启动worker
- `POST /stop` - 停止worker

#### Token管理

- `POST /token/generate` - 生成RTC token

#### 配置管理

- `GET /graphs` - 获取图形列表
- `GET /dev-tmp/addons/default-properties` - 获取默认属性

#### 向量文档

- `GET /vector/document/preset/list` - 获取预设列表
- `POST /vector/document/update` - 更新向量文档
- `POST /vector/document/upload` - 上传向量文档

## 实现细节

### 完全复刻Go代码的功能（无简化实现）

1. **Worker管理逻辑**:
   - 使用相同的端口分配策略（10000-30000）
   - 相同的进程启动和监控逻辑
   - 相同的超时清理机制
   - 相同的日志输出格式

2. **Token生成**:
   - 使用完整的HMAC-SHA1签名算法
   - 相同的消息格式和Base64编码
   - 相同的过期时间处理
   - 支持RTM和RTC两种token格式

3. **文件处理**:
   - 完整的MD5哈希计算
   - 相同的文件命名规则
   - 相同的上传路径处理

4. **配置处理**:
   - 完整的property.json文件解析
   - 相同的环境变量验证
   - 相同的图形配置处理

5. **错误处理**:
   - 相同的错误码和错误消息
   - 相同的日志记录格式
   - 相同的HTTP状态码

6. **JSON处理**:
   - 使用完整的Jackson实现
   - 无任何简化或模拟实现

## 环境配置

### 必需环境变量

```bash
AGORA_APP_ID=your_agora_app_id
AGORA_APP_CERTIFICATE=your_agora_app_certificate
```

### 可选环境变量

```bash
LOG_PATH=/tmp                    # 日志路径
SERVER_PORT=8080                 # 服务器端口
WORKERS_MAX=10                   # 最大worker数量
WORKER_QUIT_TIMEOUT_SECONDES=60  # Worker超时时间
LOG_STDOUT=false                 # 是否输出到标准输出
VECTOR_DOCUMENT_PRESET_LIST=[]   # 向量文档预设列表
```

## 构建和运行

### 构建项目

```bash
mvn clean package
```

### 运行项目

```bash
# 使用Maven
mvn spring-boot:run

# 或者使用JAR文件
java -jar target/ten-framework-server-1.0.0.jar
```

### Docker运行

```bash
# 构建Docker镜像
docker build -t ten-framework-server .

# 运行容器
docker run -p 8080:8080 \
  -e AGORA_APP_ID=your_app_id \
  -e AGORA_APP_CERTIFICATE=your_certificate \
  ten-framework-server
```

## 项目结构

```
spring_java/
├── src/main/java/com/agora/tenframework/
│   ├── TenFrameworkServerApplication.java    # 主应用类
│   ├── config/
│   │   ├── ServerConfig.java               # 服务器配置
│   │   ├── Constants.java                  # 常量定义
│   │   └── ShutdownHook.java               # 关闭钩子
│   ├── controller/
│   │   └── ApiController.java              # API控制器（完全对应Go代码）
│   ├── model/
│   │   ├── Worker.java                     # Worker模型
│   │   ├── Prop.java                       # 属性模型
│   │   ├── Code.java                       # 响应码模型
│   │   ├── WorkerUpdateReq.java            # Worker更新请求
│   │   ├── request/                        # 请求模型
│   │   └── response/                       # 响应模型
│   ├── rtcService/
│   │   ├── WorkerService.java              # Worker服务（完全对应Go代码）
│   │   ├── TokenService.java               # Token服务（完整Agora算法）
│   │   └── WorkerTimeoutService.java       # Worker超时服务
│   └── util/
│       └── Utils.java                      # 工具类
├── src/main/resources/
│   └── application.yml                     # 配置文件
├── pom.xml                                # Maven配置
├── Dockerfile                             # Docker配置
├── .gitignore                             # Git忽略文件
└── README.md                              # 项目说明
```

## API示例

### 启动Worker

```bash
curl -X POST http://localhost:8080/start \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req_123",
    "channel_name": "test_channel",
    "graph_name": "default",
    "user_uid": 123,
    "bot_uid": 456,
    "token": "your_token"
  }'
```

### 生成Token

```bash
curl -X POST http://localhost:8080/token/generate \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req_123",
    "channel_name": "test_channel",
    "uid": 123
  }'
```

### 停止Worker

```bash
curl -X POST http://localhost:8080/stop \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req_123",
    "channel_name": "test_channel"
  }'
```

## 技术栈

- **Spring Boot 3.2.0**: 主框架
- **Spring MVC**: Web框架
- **Jackson**: 完整JSON处理
- **SLF4J**: 日志框架
- **Apache Commons**: 工具库
- **Java 17**: 运行环境

## 与Go版本的对应关系

| Go代码位置                          | Java代码位置                                  | 功能           |
| ----------------------------------- | --------------------------------------------- | -------------- |
| `handlerHealth`                     | `ApiController.health()`                      | 健康检查       |
| `handlerList`                       | `ApiController.listWorkers()`                 | 获取worker列表 |
| `handleGraphs`                      | `ApiController.getGraphs()`                   | 获取图形列表   |
| `handleAddonDefaultProperties`      | `ApiController.getAddonDefaultProperties()`   | 获取默认属性   |
| `handlerPing`                       | `ApiController.ping()`                        | Ping测试       |
| `handlerStart`                      | `ApiController.startWorker()`                 | 启动worker     |
| `handlerStop`                       | `ApiController.stopWorker()`                  | 停止worker     |
| `handlerGenerateToken`              | `ApiController.generateToken()`               | 生成token      |
| `handlerVectorDocumentPresetList`   | `ApiController.getVectorDocumentPresetList()` | 获取预设列表   |
| `handlerVectorDocumentUpdate`       | `ApiController.updateVectorDocument()`        | 更新向量文档   |
| `handlerVectorDocumentUpload`       | `ApiController.uploadVectorDocument()`        | 上传向量文档   |
| `processProperty`                   | `WorkerService.processProperty()`             | 处理属性文件   |
| `timeoutWorkers`                    | `WorkerTimeoutService.timeoutWorkers()`       | 超时清理       |
| `startPropMap`                      | `Constants.START_PROP_MAP`                    | 启动属性映射   |
| `rtctokenbuilder.BuildTokenWithRtm` | `TokenService.buildTokenWithRtm()`            | RTM Token生成  |
| `rtctokenbuilder.BuildTokenWithUid` | `TokenService.buildTokenWithUid()`            | RTC Token生成  |

## 注意事项

1. **完全兼容**: 此Java版本与Go版本在API接口和业务逻辑上完全兼容
2. **Token生成**: 使用完整的Agora官方算法，无任何简化实现
3. **Worker进程**: 需要确保`/app/agents/bin/start`可执行文件存在
4. **文件权限**: 确保应用有足够的文件系统权限
5. **网络配置**: 确保worker进程可以正常访问网络
6. **定时任务**: 使用Spring的`@Scheduled`注解实现定时清理功能
7. **JSON处理**: 使用完整的Jackson实现，无任何简化

## 开发说明

### 添加新的API端点

1. 在`ApiController`中添加新的方法
2. 创建对应的请求/响应模型
3. 添加相应的服务方法
4. 确保与Go代码逻辑一致

### 配置管理

- 使用`ServerConfig`类管理配置
- 支持环境变量和配置文件
- 支持配置热更新

### 日志管理

- 使用SLF4J进行日志记录
- 支持文件和控制台输出
- 可配置日志级别和格式
- 与Go代码使用相同的日志格式

### Token生成算法

- 使用完整的HMAC-SHA1签名算法
- 支持RTM和RTC两种token格式
- 与Agora官方算法完全一致
- 无任何简化或模拟实现
