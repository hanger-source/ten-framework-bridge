# Agora Java SDK 使用指南

## 依赖配置

在 `pom.xml` 中添加 Agora Authentication SDK 依赖：

```xml
<dependency>
    <groupId>io.agora</groupId>
    <artifactId>authentication</artifactId>
    <version>2.1.3</version>
</dependency>
```

## 环境变量配置

设置以下环境变量：

```bash
export AGORA_APP_ID="your_app_id"
export AGORA_APP_CERTIFICATE="your_app_certificate"
```

## 使用方法

### 1. 基本用法

```java
import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;

public class TokenExample {
    public static void main(String[] args) {
        String appId = System.getenv("AGORA_APP_ID");
        String appCertificate = System.getenv("AGORA_APP_CERTIFICATE");
        String channelName = "test_channel";
        String uid = "12345";

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 生成 RTM Token
        String rtmToken = tokenBuilder.buildTokenWithRtm(
            appId, appCertificate, channelName, uid,
            Role.ROLE_PUBLISHER, 3600, 3600);

        System.out.println("RTM Token: " + rtmToken);
    }
}
```

### 2. 在 Spring Boot 中使用

参考 `TokenService.java` 中的实现：

```java
@Service
public class TokenService {

    @Autowired
    private ServerConfig serverConfig;

    public String generateToken(GenerateTokenRequest request) throws Exception {
        String appId = serverConfig.getAppId();
        String appCertificate = serverConfig.getAppCertificate();
        String channelName = request.getChannelName();
        Long uid = request.getUid() != null ? request.getUid() : 0L;

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 生成 RTM Token，对应 Go 代码中的 BuildTokenWithRtm
        String token = tokenBuilder.buildTokenWithRtm(
            appId, appCertificate, channelName, uid.toString(),
            Role.ROLE_PUBLISHER, 86400, 86400);

        return token;
    }
}
```

### 3. 与 Go 代码的对应关系

| Go 代码                             | Java 代码                            | 说明                  |
| ----------------------------------- | ------------------------------------ | --------------------- |
| `rtctokenbuilder.BuildTokenWithRtm` | `RtcTokenBuilder2.buildTokenWithRtm` | RTM Token 生成        |
| `rtctokenbuilder.BuildTokenWithUid` | `RtcTokenBuilder2.buildTokenWithUid` | RTC Token 生成（UID） |
| `rtctokenbuilder.RolePublisher`     | `Role.ROLE_PUBLISHER`                | 发布者角色            |
| `rtctokenbuilder.RoleSubscriber`    | `Role.ROLE_SUBSCRIBER`               | 订阅者角色            |

### 4. 完整示例

参考 `RtcTokenBuilder2Sample.java` 中的完整示例，展示了所有可用的 token 生成方法。

## 注意事项

1. **App ID 验证**: 确保 App ID 是 32 位字符串
2. **App Certificate**: 必须设置有效的 App Certificate
3. **Token 过期时间**: 建议设置为 86400 秒（24小时）
4. **角色选择**: 根据实际需求选择 ROLE_PUBLISHER 或 ROLE_SUBSCRIBER

## 错误处理

```java
try {
    String token = tokenBuilder.buildTokenWithRtm(appId, appCertificate, channelName, uid,
        Role.ROLE_PUBLISHER, 3600, 3600);
    return token;
} catch (Exception e) {
    logger.error("Token generation failed", e);
    throw new RuntimeException("Failed to generate token", e);
}
```

## 测试

运行示例代码：

```bash
# 设置环境变量
export AGORA_APP_ID="your_app_id"
export AGORA_APP_CERTIFICATE="your_app_certificate"

# 运行示例
mvn exec:java -Dexec.mainClass="com.agora.tenframework.example.RtcTokenBuilder2Sample"
```
