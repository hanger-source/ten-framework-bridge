# 实际代码迁移实施 - 2: 后端Token服务迁移

## 迁移概述

后端Token服务是核心迁移点之一。需要将Agora RTC Token生成服务替换为阿里云RTC Token生成服务。

## 迁移文件清单

### 需要修改的文件

- `ai_agents/addons/server/src/main/java/.../TokenService.java` - 主Token服务
- `ai_agents/addons/server/src/main/java/.../TokenController.java` - Token控制器
- `ai_agents/addons/server/src/main/resources/application.yml` - 配置文件

## 核心迁移文件

### 1. Token服务重构

**当前Agora实现**:

```java
// ai_agents/addons/server/src/main/java/com/agora/tenframework/service/TokenService.java
import io.agora.rtc.RtcTokenBuilder2;

@Service
public class TokenService {

    @Value("${agora.app.id}")
    private String appId;

    @Value("${agora.app.certificate}")
    private String appCertificate;

    public String generateToken(String channelName, String uid) {
        return RtcTokenBuilder2.buildTokenWithUid(
            appId,
            appCertificate,
            channelName,
            uid,
            RtcTokenBuilder2.Role.Role_Publisher,
            3600
        );
    }
}
```

**迁移为阿里云Token服务**:

```java
// ai_agents/addons/server/src/main/java/com/agora/tenframework/service/TokenService.java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class TokenService {

    @Value("${ali.app.id}")
    private String appId;

    @Value("${ali.app.key}")
    private String appKey;

    public String generateToken(String channelId, String userId) {
        return generateAliToken(appId, appKey, channelId, userId, 3600);
    }

    private String generateAliToken(String appId, String appKey,
                                  String channelId, String userId, int expireTime) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            long expireTimestamp = timestamp + expireTime;

            // 构建Token字符串
            String tokenString = String.format(
                "appId=%s&channelId=%s&userId=%s&timestamp=%d&expire=%d",
                appId, channelId, userId, timestamp, expireTimestamp
            );

            // 使用HMAC-SHA256签名
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(appKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(tokenString.getBytes());

            // Base64编码
            String signatureBase64 = Base64.getEncoder().encodeToString(signature);

            // 构建最终Token
            return String.format("%s&signature=%s", tokenString, signatureBase64);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Ali RTC token", e);
        }
    }
}
```

## 配置文件迁移

### 1. 应用配置

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

### 2. 环境变量

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

## API接口迁移

### 1. Token生成接口

**当前接口**:

```java
// TokenController.java
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

**迁移后接口**:

```java
// TokenController.java
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

### 2. 请求对象更新

**当前请求对象**:

```java
public class TokenRequest {
    private String channel;
    private String uid;
    // getters and setters
}
```

**迁移后请求对象**:

```java
public class TokenRequest {
    private String channel;
    private String uid;
    // getters and setters
}
```

## 错误处理迁移

### 1. 异常处理

**当前异常处理**:

```java
try {
    String token = tokenService.generateToken(channel, uid);
    return ResponseEntity.ok(token);
} catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Failed to generate token: " + e.getMessage());
}
```

**迁移后异常处理**:

```java
try {
    String token = tokenService.generateToken(channel, uid);
    return ResponseEntity.ok(token);
} catch (Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Failed to generate Ali RTC token: " + e.getMessage());
}
```

## 关键差异总结

### 1. Token生成算法

- **Agora**: 使用RtcTokenBuilder2，需要appId和appCertificate
- **阿里云**: 使用HMAC-SHA256签名，需要appId和appKey

### 2. Token结构

- **Agora**: 单一Token字符串
- **阿里云**: 包含参数和签名的复合字符串

### 3. 配置参数

- **Agora**: appId + appCertificate
- **阿里云**: appId + appKey

### 4. 错误处理

- **Agora**: "Failed to generate token"
- **阿里云**: "Failed to generate Ali RTC token"
