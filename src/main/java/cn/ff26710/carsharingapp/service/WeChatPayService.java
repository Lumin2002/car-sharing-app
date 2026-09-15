package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.pay.WxPayCallbackDTO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 微信支付 APIv3 网关：JSAPI 预下单、退款、回调解析。
 *
 * <p>只负责与微信通信，不碰本地支付单状态（那部分在
 * {@link PaymentRecordService} 里以短事务完成）。
 */
public interface WeChatPayService {

    /** JSAPI 下单，返回前端发起支付所需的签名参数。 */
    Map<String, String> createJsapiPrepay(String paymentNo, BigDecimal amount,
                                          String description, String openId);

    /** 发起微信退款。 */
    void refund(String paymentNo, String refundNo, String reason,
                BigDecimal refundAmount, BigDecimal orderAmount);

    /** 解析并分发微信支付/退款回调，返回微信要求的应答体。 */
    Map<String, String> handleCallback(WxPayCallbackDTO dto);
}
