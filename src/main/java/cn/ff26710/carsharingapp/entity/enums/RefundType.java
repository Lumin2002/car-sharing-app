package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RefundType {
    RENT_REFUND("RENT_REFUND", "租金退款"),
    DEPOSIT_UNFREEZE("DEPOSIT_UNFREEZE", "押金解冻"),
    DEPOSIT_DEDUCT("DEPOSIT_DEDUCT", "押金扣罚");

    private final String code;
    private final String desc;
}
