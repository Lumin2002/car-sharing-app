package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录（对外）。
 * 去掉了 {@code rawCallback} —— 那是第三方支付回调的原文，
 * 真实对接网关后里面会包含签名、商户号等敏感内容，不能透给前端。
 */
@Data
public class PaymentVO {
    private Long id;
    private Long orderId;
    private String orderNo;

    private String paymentNo;
    private String thirdTradeNo;

    private PayType payType;
    private PayMethod payMethod;
    private BigDecimal amount;
    private PaymentStatus status;

    private LocalDateTime createTime;
    private LocalDateTime callbackTime;
}
