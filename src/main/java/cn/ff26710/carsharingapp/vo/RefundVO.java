package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.RefundStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款 / 押金解冻记录（对外）。
 * 与 PaymentVO 同理，去掉 {@code rawCallback} 第三方回调原文。
 */
@Data
public class RefundVO {
    private Long id;
    private Long paymentId;
    private Long orderId;
    private String orderNo;

    private String refundNo;
    private String thirdRefundNo;

    private RefundType refundType;
    private BigDecimal amount;
    private RefundStatus status;
    private String reason;

    private LocalDateTime createTime;
    private LocalDateTime callbackTime;
}
