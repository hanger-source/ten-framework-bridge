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
 * Token Service for RTC Token Generation - Using Agora Java SDK
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
     * Generate RTC Token - equivalent to Go's handlerGenerateToken
     */
    public String generateToken(GenerateTokenRequest request) throws Exception {
        String appId = serverConfig.getAppId();
        String appCertificate = serverConfig.getAppCertificate();
        String channelName = request.getChannelName();
        Long uid = request.getUid() != null ? request.getUid() : 0L;

        if (appId == null || appId.length() != 32) {
            throw new RuntimeException("Invalid AGORA_APP_ID");
        }

        if (appCertificate == null || appCertificate.isEmpty()) {
            throw new RuntimeException("AGORA_APP_CERTIFICATE is required");
        }

        // Use Agora Java SDK to generate token
        return buildTokenWithRtm(appId, appCertificate, channelName, uid.toString(),
                Constants.TOKEN_EXPIRATION_IN_SECONDS, Constants.TOKEN_EXPIRATION_IN_SECONDS);
    }

    /**
     * Build token with RTM - equivalent to Go's rtctokenbuilder.BuildTokenWithRtm
     * Using Agora Java SDK
     */
    private String buildTokenWithRtm(String appId, String appCertificate, String channelName,
            String uid, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // Build RTM token using Agora Java SDK
        String token = tokenBuilder.buildTokenWithRtm(appId, appCertificate, channelName, uid,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * Build token with UID - equivalent to Go's rtctokenbuilder.BuildTokenWithUid
     * Using Agora Java SDK
     */
    private String buildTokenWithUid(String appId, String appCertificate, String channelName,
            int uid, int role, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // Build RTC token with UID using Agora Java SDK
        String token = tokenBuilder.buildTokenWithUid(appId, appCertificate, channelName, uid,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * Build token with user account - Using Agora Java SDK
     */
    private String buildTokenWithUserAccount(String appId, String appCertificate, String channelName,
            String account, int role, int privilegeExpiredTs, int serviceExpiredTs) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // Build RTC token with user account using Agora Java SDK
        String token = tokenBuilder.buildTokenWithUserAccount(appId, appCertificate, channelName, account,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, serviceExpiredTs);

        return token;
    }

    /**
     * Build token with RTM2 - Using Agora Java SDK
     */
    private String buildTokenWithRtm2(String appId, String appCertificate, String channelName,
            String account, int role, int privilegeExpiredTs, int joinChannelPrivilegeExpireInSeconds,
            int pubAudioPrivilegeExpireInSeconds, int pubVideoPrivilegeExpireInSeconds,
            int pubDataStreamPrivilegeExpireInSeconds, String uid, int tokenExpirationInSeconds) throws Exception {

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        // Build RTM2 token using Agora Java SDK
        String token = tokenBuilder.buildTokenWithRtm2(appId, appCertificate, channelName, account,
                Role.ROLE_PUBLISHER, privilegeExpiredTs, joinChannelPrivilegeExpireInSeconds,
                pubAudioPrivilegeExpireInSeconds, pubVideoPrivilegeExpireInSeconds,
                pubDataStreamPrivilegeExpireInSeconds, uid, tokenExpirationInSeconds);

        return token;
    }
}