package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.config.WeChatConfig;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.WeChatOAuthService;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatOAuthServiceImpl implements WeChatOAuthService {

    private static final int HTTP_TIMEOUT_MILLIS = 5000;

    private final WeChatConfig weChatConfig;

    @Override
    public String getOpenId(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("微信授权 code 不能为空");
        }

        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + weChatConfig.getAppId()
                + "&secret=" + weChatConfig.getAppSecret()
                + "&code=" + URLUtil.encode(code)
                + "&grant_type=authorization_code";

        String body;
        try {
            body = HttpRequest.get(url).timeout(HTTP_TIMEOUT_MILLIS).execute().body();
        } catch (Exception e) {
            log.error("调用微信网页授权接口失败", e);
            throw new BusinessException("获取openid失败，请稍后重试");
        }

        JSONObject json;
        try {
            json = JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.error("解析微信网页授权响应失败", e);
            throw new BusinessException("获取openid失败，请稍后重试");
        }
        if (json.containsKey("errcode")) {
            throw new BusinessException("获取openid失败:" + json.getStr("errmsg"));
        }
        String openId = json.getStr("openid");
        if (!StringUtils.hasText(openId)) {
            throw new BusinessException("获取openid失败");
        }
        return openId;
    }
}
