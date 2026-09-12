package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementStatus {
    PENDING("PENDING", "待结算"),
    CONFIRMED("CONFIRMED", "已确认"),
    FINISHED("FINISHED", "结算完成");

    private final String code;
    private final String desc;
}
