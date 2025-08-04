# 实际代码迁移实施 - 2: 后端Token服务迁移

## 参考文档

- [阿里云RTC Token生成文档](https://help.aliyun.com/document_detail/2864109.html)
- [阿里云RTC Token验证文档](https://help.aliyun.com/document_detail/159037.html)

## 迁移概述

后端Token服务是核心迁移点之一。需要将Agora RTC Token生成服务完全替换为阿里云RTC Token生成服务。

## 迁移文件清单

### 需要修改的文件

- `ai_agents/addons/server/src/main/java/.../TokenService.java` - 主Token服务
- `ai_agents/addons/server/src/main/java/.../TokenController.java` - Token控制器
- `ai_agents/addons/server/src/main/resources/application.yml` - 配置文件

## 核心迁移步骤

### 1. 配置文件迁移

**当前配置**:

```yaml
# application.yml
agora:
  app:
    id: ${AGORA_APP_ID}
    certificate: ${AGORA_APP_CERTIFICATE}
```

**迁移后配置**:

```yaml
# application.yml
ali:
  app:
    id: ${ALI_APP_ID}
    key: ${ALI_APP_KEY}
```

### 2. 环境变量迁移

**当前环境变量**:

```bash
export AGORA_APP_ID="your_agora_app_id"
export AGORA_APP_CERTIFICATE="your_agora_app_certificate"
```

**迁移后环境变量**:

```bash
export ALI_APP_ID="your_ali_app_id"
export ALI_APP_KEY="your_ali_app_key"
```

### 3. TokenService完全替换

**当前Agora实现**:

```java
@Service
public class TokenService {
    @Value("${agora.app.id}")
    private String appId;

    @Value("${agora.app.certificate}")
    private String appCertificate;

    public String generateToken(String channelName, String uid) {
        return RtcTokenBuilder2.buildTokenWithUid(
            appId, appCertificate, channelName, uid,
            RtcTokenBuilder2.Role.Role_Publisher, 3600
        );
    }
}
```

**迁移为阿里云Token服务**:

```java
@Service
public class TokenService {
    @Value("${ali.app.id}")
    private String appId;

    @Value("${ali.app.key}")
    private String appKey;

    @Autowired
    private AliRtcTokenGenerator tokenGenerator;

    public String generateToken(String channelId, String userId) {
        try {
            return tokenGenerator.generateDefaultToken(appId, appKey, channelId, userId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Ali RTC token", e);
        }
    }
}
```

### 4. API接口保持不变

```java
@RestController
@RequestMapping("/api/token")
public class TokenController {
    @Autowired
    private TokenService tokenService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateToken(@RequestBody TokenRequest request) {
        String token = tokenService.generateToken(request.getChannel(), request.getUid());
        return ResponseEntity.ok(token);
    }
}
```

## 关键差异总结

### 1. Token生成算法

- **Agora**: 使用RtcTokenBuilder2，需要appId和appCertificate
- **阿里云**: 使用HMAC-SHA256签名，需要appId和appKey

### 2. 配置参数

- **Agora**: appId + appCertificate
- **阿里云**: appId + appKey

### 3. 错误处理

- **Agora**: "Failed to generate token"
- **阿里云**: "Failed to generate Ali RTC token"

## 详细实现

详细的阿里云RTC Token实现请参考：

- [09*阿里云RTC_Token*官方实现补充.md](./09_阿里云RTC_Token_官方实现补充.md)

## 迁移验证

1. 更新配置文件
2. 重启服务
3. 测试Token生成接口
4. 验证Token在阿里云RTC控制台的有效性
