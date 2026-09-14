package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.config.WxPayV3Config;
import cn.ff26710.carsharingapp.dto.pay.WxPayCallbackDTO;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.WxPayNoticeProducer;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import cn.hutool.json.JSONUtil;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WxPayService {

    private final ObjectProvider<NativePayService> nativePayServiceProvider;
    private final ObjectProvider<RefundService> refundServiceProvider;
    private final ObjectProvider<NotificationParser> notificationParserProvider;
    private final WxPayNoticeProducer wxPayNoticeProducer;
    private final WxPayV3Config wxPayV3Config;

    public String createNativePay(String paymentNo, BigDecimal amount, String description) {
        NativePayService nativePayService = nativePayServiceProvider.getIfAvailable();
        if (nativePayService == null) {
            throw new BusinessException("微信支付未启用，请使用其它支付方式");
        }

        PrepayRequest request = new PrepayRequest();
        Amount amountObj = new Amount();
        amountObj.setTotal(AmountUtil.yuanToFenInt(amount));
        request.setAmount(amountObj);
        request.setAppid(wxPayV3Config.getAppId());
        request.setMchid(wxPayV3Config.getMerchantId());
        request.setOutTradeNo(paymentNo);
        request.setDescription(description);
        request.setNotifyUrl(wxPayV3Config.getPaymentNotifyUrl());
        // 调用下单方法，得到应答
        PrepayResponse response = nativePayService.prepay(request);
        log.info("微信预支付下单成功 paymentNo={}, codeUrl={}", paymentNo, response.getCodeUrl());
        return response.getCodeUrl();
    }

    public Map<String,String> handlerCallback(WxPayCallbackDTO dto){
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
                Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
                String paymentNo = transaction.getOutTradeNo();
                String wxTradeNo = transaction.getTransactionId();
                String tradeState = transaction.getTradeState().name();
                Integer amountCent = transaction.getAmount().getTotal();
                String plainJson = JSONUtil.toJsonStr(transaction);

                log.info("微信支付回调 paymentNo:{}, tradeState:{}", paymentNo, tradeState);
                if (tradeState.equals("SUCCESS")) {
                    wxPayNoticeProducer.publishPaymentNotice(WxPayNoticeEvent.of(paymentNo, wxTradeNo, tradeState, amountCent, plainJson));
                }
            } else if ("Refund".equals(dto.getRequestType())) {
                RefundNotification notification = notificationParser.parse(requestParam, RefundNotification.class);
                String refundStatus = notification.getRefundStatus().name();
                String refundNo = notification.getOutRefundNo();
                String wxRefundNo = notification.getRefundId();
                Long amountCent = notification.getAmount().getTotal();
                String plainJson = JSONUtil.toJsonStr(notification);
                log.info("微信退款回调 refundNo:{}, RefundState:{}", refundNo, refundStatus);
                if (refundStatus.equals("SUCCESS")) {
                    wxPayNoticeProducer.publishRefundNotice(WxPayRefundNoticeEvent.of(refundNo, wxRefundNo, amountCent, refundStatus, plainJson));
                }
            } else {
                throw new BusinessException("未知回调类型");
            }
        } catch (Exception e) {
            log.error("微信回调处理异常", e);
            return Map.of("code", "FAIL", "message", "回调解析失败");
        }
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    public void createRefundRequest(String paymentNo, String refundNo, String reason,
                                    BigDecimal refundAmount, BigDecimal orderAmount){
        RefundService refundService = refundServiceProvider.getIfAvailable();
        if (refundService == null) {
            throw new BusinessException("微信支付退款未启用，请使用其它支付方式");
        }
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(paymentNo);
        request.setOutRefundNo(refundNo);
        request.setReason(reason);
        request.setNotifyUrl(wxPayV3Config.getRefundNotifyUrl());

        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(AmountUtil.yuanToFenLong(refundAmount));
        amountReq.setTotal(AmountUtil.yuanToFenLong(orderAmount));
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);
        refundService.create(request);
    }
}
