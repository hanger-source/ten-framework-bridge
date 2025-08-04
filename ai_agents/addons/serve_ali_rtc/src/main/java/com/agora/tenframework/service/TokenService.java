package com.agora.tenframework.service;

import com.agora.tenframework.model.request.GenerateTokenRequest;
import com.agora.tenframework.rtc.model.AppToken;
import com.agora.tenframework.rtc.model.AppTokenOptions;
import com.agora.tenframework.rtc.model.RtcService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;

/**
 * 阿里云RTC Token服务类
 *
 * 使用官方示例的AppToken实现
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    /**
     * -- GETTER --
     * 获取应用ID
     */
    @Getter
    @Value("${ali.app.id}")
    private String appId;

    @Value("${ali.app.key}")
    private String appKey;

    /**
     * 生成RTC Token
     *
     * @param request Token生成请求对象
     * @return 生成的Token字符串
     * @throws Exception Token生成失败时抛出异常
     */
    public String generateToken(GenerateTokenRequest request) throws Exception {
        try {
            String channelId = request.getChannelName();
            Long userId = request.getUid() != null ? request.getUid() : 0L;

            logger.info("开始生成Token - channelName: {}, uid: {}", channelId, userId);

            // 验证阿里云RTC配置
            if (appId == null || appId.isEmpty()) {
                logger.error("生成Token失败 - ALI_APP_ID是必需的");
                throw new RuntimeException("ALI_APP_ID是必需的");
            }

            if (appKey == null || appKey.isEmpty()) {
                logger.error("生成Token失败 - ALI_APP_KEY是必需的");
                throw new RuntimeException("ALI_APP_KEY是必需的");
            }

            // Token有效期设置为12小时
            int expiredTs = (int) (System.currentTimeMillis() / 1000) + 12 * 60 * 60;

            // 使用官方示例的AppToken
            final AppToken appToken = new AppToken(appId, appKey, expiredTs);

            // 默认允许所有权限
            final RtcService rtcService = new RtcService(channelId, userId.toString());
            appToken.addService(rtcService);

            // 添加Token options配置
            final AppTokenOptions appTokenOptions = new AppTokenOptions();
            Map<String, String> engineOptions = new TreeMap<>();
            // 设置频道最大时长为86400秒（24小时）
            engineOptions.put("duration_per_channel", "86400");
            // 设置频道中没有人时保留1秒后结束
            engineOptions.put("delay_close_per_channel", "1");
            appTokenOptions.addEngineOptions(engineOptions);
            appToken.addOptions(appTokenOptions);

            final String appTokenStr = appToken.buildTokenString();

            logger.info(
                    "Token生成成功 - \nappId: {} \nappKey: {} \nchannelId: {} \nuserId: {} \ntimestamp: {} \ntoken: {} \noptions: {}",
                    appId, appKey,
                    channelId, userId, expiredTs, appTokenStr, engineOptions);
            return appTokenStr;
        } catch (Throwable e) {
            logger.error("Token生成失败 - error: {}", e.getMessage());
            throw new RuntimeException("Token生成失败: " + e.getMessage(), e);
        }
    }

}