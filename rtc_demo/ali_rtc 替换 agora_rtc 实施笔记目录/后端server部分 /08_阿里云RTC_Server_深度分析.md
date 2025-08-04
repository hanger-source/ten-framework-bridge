# 阿里云RTC Server深度分析

## 参考文档

- [阿里云RTC Token生成文档](https://help.aliyun.com/document_detail/2864109.html)
- [阿里云RTC Token验证文档](https://help.aliyun.com/document_detail/159037.html)

## 1. 阿里云RTC Server架构分析

### 1.1 阿里云RTC Server目录结构

```
rtc_demo/AliRTCSample/Server/java/
├── src/main/java/com/company/apptoken/
│   ├── constant/
│   │   ├── AppTokenConstants.java      # Token常量定义
│   │   └── SecurityConstants.java      # 安全常量定义
│   ├── enums/
│   │   └── PrivilegeEnum.java         # 权限枚举
│   ├── model/
│   │   ├── AppToken.java              # 核心Token生成类
│   │   ├── AppTokenOptions.java       # Token选项配置
│   │   └── Service.java               # 服务配置类
│   └── util/
│       ├── CompressUtils.java         # 压缩工具
│       ├── EncodeUtils.java           # 编码工具
│       └── SignatureUtils.java        # 签名工具
├── pom.xml                            # Maven依赖配置
└── README.md                          # 说明文档
```

### 1.2 核心Token生成流程

**阿里云RTC Token生成算法**:

```java
// AppToken.java - 核心Token生成类
public class AppToken {
    private final String appId;
    private String appKey;
    private final int issueTimestamp;
    private final int salt;
    private final int timestamp;
    private Service service;
    private AppTokenOptions options;
    private byte[] signature;

    public String buildTokenString() throws Throwable {
        // 1. 验证参数
        if (StringUtils.isBlank(this.appKey)) {
            throw new IllegalArgumentException("missing secretKey");
        }
        if (Objects.isNull(this.service)) {
            throw new IllegalArgumentException("missing service");
        }

        // 2. 生成签名
        final byte[] signatureTemp = SignatureUtils.sign(this.appKey, this.issueTimestamp, this.salt);

        // 3. 构建Token数据
        final ByteBuf buf = Unpooled.buffer();
        final byte[] appId = this.appId.getBytes();
        buf.writeInt(appId.length);
        buf.writeBytes(appId);
        buf.writeInt(this.issueTimestamp);
        buf.writeInt(this.salt);
        buf.writeInt(this.timestamp);

        // 4. 添加服务配置
        this.service.pack(buf);
        if (Objects.isNull(this.options)) {
            this.options = new AppTokenOptions();
        }
        this.options.pack(buf);

        // 5. 生成最终签名
        final Mac mac = Mac.getInstance(SecurityConstants.ALGORITHM_HMAC_SHA256);
        mac.init(new SecretKeySpec(signatureTemp, SecurityConstants.ALGORITHM_HMAC_SHA256));
        final byte[] signature = mac.doFinal(buf.array());

        // 6. 构建最终Token
        final ByteBuf bufToken = Unpooled.buffer();
        bufToken.writeInt(signature.length);
        bufToken.writeBytes(signature);
        bufToken.writeBytes(buf.array());

        return AppTokenConstants.VERSION_0 + EncodeUtils.base64Encode(CompressUtils.compress(bufToken.array()));
    }
}
```

### 1.3 服务配置类

**Service.java - 服务配置**:

```java
public class Service {
    private final String channelId;
    private final String userId;
    private Integer privilege;

    public void addAudioPublishPrivilege() {
        if (Objects.isNull(this.privilege)) {
            this.privilege = 0;
            this.privilege = this.privilege | PrivilegeEnum.ENABLE_PRIVILEGE.getPrivilege();
        }
        this.privilege = this.privilege | PrivilegeEnum.ENABLE_AUDIO_PUBLISH.getPrivilege();
    }

    public void addVideoPublishPrivilege() {
        if (Objects.isNull(this.privilege)) {
            this.privilege = 0;
            this.privilege = this.privilege | PrivilegeEnum.ENABLE_PRIVILEGE.getPrivilege();
        }
        this.privilege = this.privilege | PrivilegeEnum.ENABLE_VIDEO_PUBLISH.getPrivilege();
    }

    public void addScreenPublishPrivilege() {
        if (Objects.isNull(this.privilege)) {
            this.privilege = 0;
            this.privilege = this.privilege | PrivilegeEnum.ENABLE_PRIVILEGE.getPrivilege();
        }
        this.privilege = this.privilege | PrivilegeEnum.ENABLE_SCREEN_PUBLISH.getPrivilege();
    }
}
```

## 2. 当前Agora RTC Server分析

### 2.1 当前TokenService结构

```java
// ai_agents/addons/server/src/main/java/com/agora/tenframework/service/TokenService.java
@Service
public class TokenService {
    @Autowired
    private ServerConfig serverConfig;

    public String generateToken(GenerateTokenRequest request) throws Exception {
        String appId = serverConfig.getAppId();
        String appCertificate = serverConfig.getAppCertificate();
        String channelName = request.getChannelName();
        Long uid = request.getUid() != null ? request.getUid() : 0L;

        // 使用Agora Java SDK生成Token
        String token = buildTokenWithRtm(appId, appCertificate, channelName, uid.toString(),
                Constants.TOKEN_EXPIRATION_IN_SECONDS, Constants.TOKEN_EXPIRATION_IN_SECONDS);

        return token;
    }

    private String buildTokenWithRtm(String appId, String appCertificate, String channelName,
            String uid, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        return tokenBuilder.buildTokenWithRtm(appId, appCertificate, channelName, uid,
                privilegeExpiredTs, serviceExpiredTs);
    }
}
```

## 3. 关键差异分析

### 3.1 Token生成算法差异

**Agora RTC Token**:

- 使用Agora官方SDK: `RtcTokenBuilder2`
- 参数: `appId`, `appCertificate`, `channelName`, `uid`
- 算法: 基于HMAC-SHA1的签名算法
- 输出: 单一Token字符串

**阿里云RTC Token**:

- 使用自定义算法: `AppToken.buildTokenString()`
- 参数: `appId`, `appKey`, `channelId`, `userId`
- 算法: 基于HMAC-SHA256的复杂签名算法
- 输出: 包含版本号、压缩、Base64编码的复合Token

### 3.2 权限控制差异

**Agora RTC**:

- 简单的角色控制: `Role.Publisher`, `Role.Subscriber`
- 统一的权限过期时间

**阿里云RTC**:

- 细粒度权限控制: 音频发布、视频发布、屏幕分享
- 独立的权限过期时间
- 支持权限组合

### 3.3 配置参数差异

**Agora RTC**:

```java
// 配置参数
String appId = serverConfig.getAppId();
String appCertificate = serverConfig.getAppCertificate();
```

**阿里云RTC**:

```java
// 配置参数
String appId = serverConfig.getAppId();
String appKey = serverConfig.getAppKey();  // 注意：是appKey不是appCertificate
```

## 4. 迁移实施策略

### 4.1 核心迁移点

1. **TokenService重构**:
   - 替换Agora SDK为阿里云Token生成算法
   - 修改配置参数从appCertificate改为appKey
   - 实现阿里云RTC的权限控制机制

2. **新增阿里云RTC依赖**:
   - 添加Netty依赖（用于ByteBuf操作）
   - 添加压缩和编码工具类
   - 添加阿里云RTC常量定义

3. **配置迁移**:
   - 修改application.yml配置
   - 更新环境变量
   - 添加阿里云RTC相关配置

### 4.2 具体迁移步骤

#### 步骤1: 添加阿里云RTC依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-buffer</artifactId>
    <version>4.1.86.Final</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.12.0</version>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.24</version>
</dependency>
```

#### 步骤2: 创建阿里云RTC模型类

```java
// com/agora/tenframework/model/ali/
public class AliAppToken {
    private final String appId;
    private String appKey;
    private final int issueTimestamp;
    private final int salt;
    private final int timestamp;
    private AliService service;
    private AliAppTokenOptions options;
    private byte[] signature;

    public AliAppToken(String appId, String appKey, int timestamp) {
        this.appId = appId;
        this.appKey = appKey;
        this.timestamp = timestamp;
        this.issueTimestamp = (int) (System.currentTimeMillis() / 1000);
        this.salt = new SecureRandom().nextInt();
    }

    public String buildTokenString() throws Throwable {
        // 实现阿里云RTC Token生成算法
        // 参考AliRTCSample的实现
    }
}
```

#### 步骤3: 重构TokenService

```java
@Service
public class TokenService {
    @Autowired
    private ServerConfig serverConfig;

    public String generateToken(GenerateTokenRequest request) throws Exception {
        String appId = serverConfig.getAppId();
        String appKey = serverConfig.getAppKey();  // 改为appKey
        String channelId = request.getChannelName();
        String userId = request.getUid().toString();

        // 使用阿里云RTC Token生成算法
        AliAppToken appToken = new AliAppToken(appId, appKey,
            (int) (System.currentTimeMillis() / 1000) + Constants.TOKEN_EXPIRATION_IN_SECONDS);

        AliService service = new AliService(channelId, userId);
        appToken.addService(service);

        return appToken.buildTokenString();
    }
}
```

#### 步骤4: 更新配置

```yaml
# application.yml
ali:
  app:
    id: ${ALI_APP_ID}
    key: ${ALI_APP_KEY} # 注意：是key不是certificate
```

## 5. 关键注意事项

### 5.1 算法复杂度

阿里云RTC的Token生成算法比Agora RTC更复杂：

- 需要处理ByteBuf操作
- 需要实现压缩和编码
- 需要处理权限位运算
- 需要实现复杂的签名算法

### 5.2 权限控制

阿里云RTC支持更细粒度的权限控制：

- 音频发布权限
- 视频发布权限
- 屏幕分享权限
- 权限组合控制

### 5.3 配置差异

- Agora使用appCertificate，阿里云使用appKey
- 阿里云需要更多的配置参数
- 阿里云支持更多的Token选项

### 5.4 测试验证

需要验证生成的Token：

- 在阿里云RTC控制台验证
- 在前端使用阿里云RTC SDK测试
- 确保权限控制正确工作
