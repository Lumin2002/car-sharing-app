package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayType {
    RENT_PAY("RENT_PAY", "租金支付"),
    DEPOSIT_FROZEN("DEPOSIT_FROZEN", "押金预授权冻结");

    private final String code;
    private final String desc;
}