package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import lombok.Data;

import java.util.Map;

@Data
public class PayResultVO {
    private String paymentNo;
    private PaymentStatus paymentStatus;
    private Map<String,String> payParamMap;
}
