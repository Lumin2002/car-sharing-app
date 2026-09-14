package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.WxPayNoticeProducer;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
public class PayController {
    private final NotificationParser notificationParser;
    private final WxPayNoticeProducer wxPayNoticeProducer;

    @PostMapping("/wx/payment-notify")
    public Object wxPaymentNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .signature(signature)
                    .timestamp(timestamp)
                    .nonce(nonce)
                    .body(body)
                    .build();
            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
            String paymentNo = transaction.getOutTradeNo();
            String wxTradeNo = transaction.getTransactionId();
            String tradeState = transaction.getTradeState().name();
            Integer amountCent = transaction.getAmount().getTotal();
            String plainJson = JSONUtil.toJsonStr(transaction);

            log.info("微信回调 paymentNo:{},tradeState:{}", paymentNo, tradeState);
            if (tradeState.equals("SUCCESS")) {
                wxPayNoticeProducer.publishPaymentNotice(WxPayNoticeEvent.of(paymentNo,wxTradeNo,tradeState,amountCent,plainJson));
            }
            // 微信要求成功返回 {}
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("微信回调处理异常", e);
            // 返回失败，微信会重试推送
            return Map.of("code", "FAIL", "message", "回调解析失败");
        }
    }

    @PostMapping("/wx/refund-notify")
    public Object wxRefundNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .signature(signature)
                    .timestamp(timestamp)
                    .nonce(nonce)
                    .body(body)
                    .build();
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
            // 微信要求成功返回 {}
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("微信退款回调处理异常", e);
            // 返回失败，微信会重试推送
            return Map.of("code", "FAIL", "message", "回调解析失败");
        }
    }

    private String readBody(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

}
