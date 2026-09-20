package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.config.WeChatConfig;
import cn.ff26710.carsharingapp.dto.pay.WxPayCallbackDTO;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.WxPayNoticeProducer;
import cn.ff26710.carsharingapp.service.WeChatPayService;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import cn.hutool.json.JSONUtil;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatPayServiceImpl implements WeChatPayService {

    private final ObjectProvider<RSAAutoCertificateConfig> rsaAutoCertificateConfigProvider;
    private final ObjectProvider<JsapiService> jsapiServiceProvider;
    private final ObjectProvider<RefundService> refundServiceProvider;
    private final ObjectProvider<NotificationParser> notificationParserProvider;
    private final WxPayNoticeProducer wxPayNoticeProducer;
    private final WeChatConfig weChatConfig;

    @Override
    public Map<String, String> createJsapiPrepay(String paymentNo, BigDecimal amount,
                                                 String description, String openId) {
        JsapiService jsapiService = jsapiServiceProvider.getIfAvailable();
        RSAAutoCertificateConfig rsaAutoCertificateConfig = rsaAutoCertificateConfigProvider.getIfAvailable();
        if (jsapiService == null || rsaAutoCertificateConfig == null) {
            throw new BusinessException("微信支付未启用，请使用其它支付方式");
        }

        PrepayRequest request = new PrepayRequest();
        request.setAppid(weChatConfig.getAppId());
        request.setMchid(weChatConfig.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(paymentNo);
        request.setNotifyUrl(weChatConfig.getPaymentNotifyUrl());

        Amount amountObj = new Amount();
        amountObj.setTotal(AmountUtil.yuanToFenInt(amount));
        amountObj.setCurrency("CNY");
        request.setAmount(amountObj);

        Payer payer = new Payer();
        payer.setOpenid(openId);
        request.setPayer(payer);

        PrepayResponse response = jsapiService.prepay(request);

        return buildPayParams(response.getPrepayId(), rsaAutoCertificateConfig);
    }

    @Override
    public void refund(String paymentNo, String refundNo, String reason,
                       BigDecimal refundAmount, BigDecimal orderAmount) {
        RefundService refundService = refundServiceProvider.getIfAvailable();
        if (refundService == null) {
            throw new BusinessException("微信支付退款未启用，请使用其它支付方式");
        }
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(paymentNo);
        request.setOutRefundNo(refundNo);
        request.setReason(reason);
        request.setNotifyUrl(weChatConfig.getRefundNotifyUrl());

        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(AmountUtil.yuanToFenLong(refundAmount));
        amountReq.setTotal(AmountUtil.yuanToFenLong(orderAmount));
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);
        refundService.create(request);
    }

    @Override
    public Map<String, String> handleCallback(WxPayCallbackDTO dto) {
        try {
            NotificationParser notificationParser = notificationParserProvider.getIfAvailable();
            if (notificationParser == null) {
                throw new BusinessException("微信支付未启用，请使用其它支付方式");
            }
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(dto.getSerial())
                    .signature(dto.getSignature())
                    .timestamp(dto.getTimestamp())
                    .nonce(dto.getNonce())
                    .body(dto.getBody())
                    .build();

            if ("Payment".equals(dto.getRequestType())) {
                handlePaymentCallback(notificationParser, requestParam);
            } else if ("Refund".equals(dto.getRequestType())) {
                handleRefundCallback(notificationParser, requestParam);
            } else {
                throw new BusinessException("未知回调类型");
            }
        } catch (Exception e) {
            log.error("微信回调处理异常", e);
            return Map.of("code", "FAIL", "message", "回调解析失败");
        }
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    private void handlePaymentCallback(NotificationParser notificationParser, RequestParam requestParam) {
        Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
        String paymentNo = transaction.getOutTradeNo();
        String wxTradeNo = transaction.getTransactionId();
        String tradeState = transaction.getTradeState().name();
        Integer amountCent = transaction.getAmount().getTotal();
        String plainJson = JSONUtil.toJsonStr(transaction);

        log.info("微信支付回调 paymentNo:{}, tradeState:{}", paymentNo, tradeState);
        if ("SUCCESS".equals(tradeState)) {
            wxPayNoticeProducer.publishPaymentNotice(
                    WxPayNoticeEvent.of(paymentNo, wxTradeNo, tradeState, amountCent, plainJson));
        }
    }

    private void handleRefundCallback(NotificationParser notificationParser, RequestParam requestParam) {
        RefundNotification notification = notificationParser.parse(requestParam, RefundNotification.class);
        String refundStatus = notification.getRefundStatus().name();
        String refundNo = notification.getOutRefundNo();
        String wxRefundNo = notification.getRefundId();
        Long amountCent = notification.getAmount().getRefund();
        String plainJson = JSONUtil.toJsonStr(notification);

        log.info("微信退款回调 refundNo:{}, refundState:{}", refundNo, refundStatus);
        if ("SUCCESS".equals(refundStatus)) {
            wxPayNoticeProducer.publishRefundNotice(
                    WxPayRefundNoticeEvent.of(refundNo, wxRefundNo, amountCent, refundStatus, plainJson));
        }
    }

    private Map<String, String> buildPayParams(String prepayId, RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        String appId = weChatConfig.getAppId();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String packageVal = "prepay_id=" + prepayId;
        String signSource = appId + "\n" + timestamp + "\n" + nonceStr + "\n" + packageVal + "\n";
        String paySign = rsaAutoCertificateConfig.createSigner().sign(signSource).getSign();

        Map<String, String> result = new HashMap<>();
        result.put("appId", appId);
        result.put("timeStamp", timestamp);
        result.put("nonceStr", nonceStr);
        result.put("package", packageVal);
        result.put("signType", "RSA");
        result.put("paySign", paySign);
        return result;
    }
}
