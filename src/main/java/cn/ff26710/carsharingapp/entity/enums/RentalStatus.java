package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RentalStatus {
    PENDING("PENDING", "待取车"),
    RENTING("RENTING", "租用中"),
    OVERDUE("OVERDUE", "已逾期"),
    RETURNED("RETURNED", "已还车"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;
}
