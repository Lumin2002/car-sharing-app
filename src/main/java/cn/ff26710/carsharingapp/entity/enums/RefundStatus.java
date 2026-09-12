package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RefundStatus {
    APPLY("APPLY", "申请中"),
    SUCCESS("SUCCESS", "退款成功"),
    FAIL("FAIL", "退款失败");

    private final String code;
    private final String desc;
}
