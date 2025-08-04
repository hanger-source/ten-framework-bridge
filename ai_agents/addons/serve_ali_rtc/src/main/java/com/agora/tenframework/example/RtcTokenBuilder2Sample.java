package com.agora.tenframework.example;

import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;

/**
 * RTC Token Builder2 示例类
 *
 * 该类演示了如何使用Agora Java SDK生成各种类型的Token，包括：
 *
 * 核心功能：
 * 1. UID Token生成 - 使用用户ID生成RTC Token
 * 2. 账户Token生成 - 使用用户账户生成RTC Token
 * 3. RTM Token生成 - 生成实时消息Token
 * 4. RTM2 Token生成 - 生成RTM2 Token
 * 5. 权限控制Token - 生成带权限控制的Token
 *
 * 环境变量配置：
 * - AGORA_APP_ID: Agora应用ID
 * - AGORA_APP_CERTIFICATE: Agora应用证书
 *
 * 使用场景：
 * - 开发测试 - 验证Token生成功能
 * - 示例参考 - 为开发者提供使用示例
 * - 功能验证 - 验证Agora SDK集成
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的RTC Token生成示例
 * - 使用Java SDK替代Go的CGO调用
 * - 保持相同的Token生成逻辑和参数
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class RtcTokenBuilder2Sample {

        /**
         * Agora应用ID
         * 需要设置环境变量AGORA_APP_ID
         */
        static String appId = System.getenv("AGORA_APP_ID");

        /**
         * Agora应用证书
         * 需要设置环境变量AGORA_APP_CERTIFICATE
         */
        static String appCertificate = System.getenv("AGORA_APP_CERTIFICATE");

        /**
         * 频道名称
         */
        static String channelName = "7d72365eb983485397e3e3f9d460bdda";

        /**
         * 用户账户
         */
        static String account = "2082341273";

        /**
         * 用户ID
         */
        static int uid = 2082341273;

        /**
         * Token过期时间（秒）
         */
        static int tokenExpirationInSeconds = 3600;

        /**
         * 权限过期时间（秒）
         */
        static int privilegeExpirationInSeconds = 3600;

        /**
         * 加入频道权限过期时间（秒）
         */
        static int joinChannelPrivilegeExpireInSeconds = 3600;

        /**
         * 发布音频权限过期时间（秒）
         */
        static int pubAudioPrivilegeExpireInSeconds = 3600;

        /**
         * 发布视频权限过期时间（秒）
         */
        static int pubVideoPrivilegeExpireInSeconds = 3600;

        /**
         * 发布数据流权限过期时间（秒）
         */
        static int pubDataStreamPrivilegeExpireInSeconds = 3600;

        /**
         * 主方法
         *
         * 演示各种Token生成方法
         *
         * @param args 命令行参数
         */
        public static void main(String[] args) {
                System.out.printf("应用ID: %s\n", appId);
                System.out.printf("应用证书: %s\n", appCertificate);
                if (appId == null || appId.isEmpty() || appCertificate == null || appCertificate.isEmpty()) {
                        System.out.printf("需要设置环境变量AGORA_APP_ID和AGORA_APP_CERTIFICATE\n");
                        return;
                }

                RtcTokenBuilder2 token = new RtcTokenBuilder2();

                // 使用UID生成Token
                String result = token.buildTokenWithUid(appId, appCertificate, channelName, uid, Role.ROLE_PUBLISHER,
                                tokenExpirationInSeconds, privilegeExpirationInSeconds);
                System.out.printf("使用UID生成的Token: %s\n", result);

                // 使用账户生成Token
                result = token.buildTokenWithUserAccount(appId, appCertificate, channelName, account,
                                Role.ROLE_PUBLISHER,
                                tokenExpirationInSeconds,
                                privilegeExpirationInSeconds);
                System.out.printf("使用账户生成的Token: %s\n", result);

                // 使用UID生成带权限控制的Token
                result = token.buildTokenWithUid(appId, appCertificate, channelName, uid, tokenExpirationInSeconds,
                                joinChannelPrivilegeExpireInSeconds,
                                pubAudioPrivilegeExpireInSeconds, pubVideoPrivilegeExpireInSeconds,
                                pubDataStreamPrivilegeExpireInSeconds);
                System.out.printf("使用UID生成的带权限控制Token: %s\n", result);

                // 使用账户生成带权限控制的Token
                result = token.buildTokenWithUserAccount(appId, appCertificate, channelName, account,
                                tokenExpirationInSeconds,
                                joinChannelPrivilegeExpireInSeconds,
                                pubAudioPrivilegeExpireInSeconds, pubVideoPrivilegeExpireInSeconds,
                                pubDataStreamPrivilegeExpireInSeconds);
                System.out.printf("使用账户生成的带权限控制Token: %s\n", result);

                // 生成RTM Token
                result = token.buildTokenWithRtm(appId, appCertificate, channelName, account, Role.ROLE_PUBLISHER,
                                tokenExpirationInSeconds,
                                privilegeExpirationInSeconds);
                System.out.printf("RTM Token: %s\n", result);

                // 生成RTM2 Token
                result = token.buildTokenWithRtm2(appId, appCertificate, channelName, account, Role.ROLE_PUBLISHER,
                                tokenExpirationInSeconds,
                                joinChannelPrivilegeExpireInSeconds, pubAudioPrivilegeExpireInSeconds,
                                pubVideoPrivilegeExpireInSeconds,
                                pubDataStreamPrivilegeExpireInSeconds,
                                account, tokenExpirationInSeconds);
                System.out.printf("RTM2 Token: %s\n", result);
        }
}