package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import lombok.Data;

@Data
public class PayResultVO {
    private String paymentNo;
    private String codeUrl;
    private PaymentStatus paymentStatus;
}
