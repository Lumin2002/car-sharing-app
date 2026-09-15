package cn.ff26710.carsharingapp.service;

/**
 * 微信网页授权：用前端拿到的 code 换取 openid。
 *
 * <p>只负责 OAuth，不掺支付逻辑。
 */
public interface WeChatOAuthService {

    String getOpenId(String code);
}
