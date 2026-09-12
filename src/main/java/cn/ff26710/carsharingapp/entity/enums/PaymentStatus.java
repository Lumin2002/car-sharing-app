package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    INIT("INIT", "待处理"),
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败"),
    REFUNDED("REFUNDED", "已退款");

    private final String code;
    private final String desc;
}
