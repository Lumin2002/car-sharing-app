package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayMethod {
    ALIPAY("ALIPAY", "支付宝"),
    WECHAT("WECHAT", "微信支付"),
    BANK("BANK", "银行卡"),
    CASH("CASH", "现金"),
    BALANCE("BALANCE", "账户余额");

    private final String code;
    private final String desc;
}
