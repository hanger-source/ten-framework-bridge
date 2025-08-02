package com.agora.tenframework.service;

import com.agora.tenframework.config.Constants;
import com.agora.tenframework.config.ServerConfig;
import com.agora.tenframework.model.request.GenerateTokenRequest;
import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agora Token服务类
 *
 * 该类负责生成Agora RTC/RTM服务的访问Token，包括：
 *
 * 核心功能：
 * 1. RTC Token生成 - 用于实时音视频通话
 * 2. RTM Token生成 - 用于实时消息传输
 * 3. Token验证 - 验证AppId和AppCertificate的有效性
 * 4. 多种Token类型支持 - 支持UID、用户账户、RTM2等不同Token类型
 *
 * 技术实现：
 * - 使用Agora Java SDK (RtcTokenBuilder2)
 * - 支持多种Token生成方式
 * - 统一的Token过期时间管理
 *
 * 与Go版本的对应关系：
 * - 对应Go版本的rtctokenbuilder包
 * - 使用Java SDK替代Go的CGO调用
 * - 保持相同的Token生成逻辑和参数
 *
 * @author Agora IO
 * @version 1.0.0
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 生成RTC Token
     *
     * 生成用于Agora RTC服务的访问Token
     * 对应Go版本的handlerGenerateToken方法
     *
     * @param request Token生成请求对象
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    public String generateToken(GenerateTokenRequest request) throws Exception {
        String appId = serverConfig.getAppId();
        String appCertificate = serverConfig.getAppCertificate();
        String channelName = request.getChannelName();
        Long uid = request.getUid() != null ? request.getUid() : 0L;

        logger.info("开始生成Token - channelName: {}, uid: {}", channelName, uid);

        if (appId == null || appId.length() != 32) {
            logger.error("生成Token失败 - 无效的AGORA_APP_ID");
            throw new RuntimeException("无效的AGORA_APP_ID");
        }

        if (appCertificate == null || appCertificate.isEmpty()) {
            logger.error("生成Token失败 - AGORA_APP_CERTIFICATE是必需的");
            throw new RuntimeException("AGORA_APP_CERTIFICATE是必需的");
        }

        // 使用Agora Java SDK生成Token
        String token = buildTokenWithRtm(appId, appCertificate, channelName, uid.toString(),
                Constants.TOKEN_EXPIRATION_IN_SECONDS, Constants.TOKEN_EXPIRATION_IN_SECONDS);

        logger.info("Token生成成功 - channelName: {}, uid: {}", channelName, uid);
        return token;
    }

    /**
     * 使用RTM方式构建Token
     *
     * 对应Go版本的rtctokenbuilder.BuildTokenWithRtm方法
     * 使用Agora Java SDK
     *
     * @param appId              Agora应用ID
     * @param appCertificate     Agora应用证书
     * @param channelName        频道名称
     * @param uid                用户ID
     * @param privilegeExpiredTs 权限过期时间
     * @param serviceExpiredTs   服务过期时间
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    private String buildTokenWithRtm(String appId, String appCertificate, String channelName,
            String uid, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 使用Agora Java SDK构建RTM Token
        String token = tokenBuilder.buildTokenWithRtm(appId, appCertificate, channelName, uid,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * 使用UID方式构建Token
     *
     * 对应Go版本的rtctokenbuilder.BuildTokenWithUid方法
     * 使用Agora Java SDK
     *
     * @param appId              Agora应用ID
     * @param appCertificate     Agora应用证书
     * @param channelName        频道名称
     * @param uid                用户ID
     * @param role               用户角色
     * @param privilegeExpiredTs 权限过期时间
     * @param serviceExpiredTs   服务过期时间
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    private String buildTokenWithUid(String appId, String appCertificate, String channelName,
            int uid, int role, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 使用Agora Java SDK构建带UID的RTC Token
        String token = tokenBuilder.buildTokenWithUid(appId, appCertificate, channelName, uid,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * 使用用户账户方式构建Token
     *
     * 使用Agora Java SDK
     *
     * @param appId              Agora应用ID
     * @param appCertificate     Agora应用证书
     * @param channelName        频道名称
     * @param account            用户账户
     * @param role               用户角色
     * @param privilegeExpiredTs 权限过期时间
     * @param serviceExpiredTs   服务过期时间
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    private String buildTokenWithUserAccount(String appId, String appCertificate, String channelName,
            String account, int role, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 使用Agora Java SDK构建带用户账户的RTC Token
        String token = tokenBuilder.buildTokenWithUserAccount(appId, appCertificate, channelName, account,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * 构建RTM2 Token
     *
     * 使用Agora Java SDK
     *
     * @param appId                                 Agora应用ID
     * @param appCertificate                        Agora应用证书
     * @param channelName                           频道名称
     * @param account                               用户账户
     * @param role                                  用户角色
     * @param privilegeExpiredTs                    权限过期时间
     * @param joinChannelPrivilegeExpireInSeconds   加入频道权限过期时间
     * @param pubAudioPrivilegeExpireInSeconds      发布音频权限过期时间
     * @param pubVideoPrivilegeExpireInSeconds      发布视频权限过期时间
     * @param pubDataStreamPrivilegeExpireInSeconds 发布数据流权限过期时间
     * @param uid                                   用户ID
     * @param tokenExpirationInSeconds              Token过期时间
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    private String buildTokenWithRtm2(String appId, String appCertificate, String channelName,
            String account, int role, int privilegeExpiredTs, int joinChannelPrivilegeExpireInSeconds,
            int pubAudioPrivilegeExpireInSeconds, int pubVideoPrivilegeExpireInSeconds,
            int pubDataStreamPrivilegeExpireInSeconds, String uid, int tokenExpirationInSeconds) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // 使用Agora Java SDK构建RTM2 Token
        String token = tokenBuilder.buildTokenWithRtm2(appId, appCertificate, channelName, account,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, joinChannelPrivilegeExpireInSeconds,
                pubAudioPrivilegeExpireInSeconds, pubVideoPrivilegeExpireInSeconds,
                pubDataStreamPrivilegeExpireInSeconds, uid, tokenExpirationInSeconds);

        return token;
    }
}