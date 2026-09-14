package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
