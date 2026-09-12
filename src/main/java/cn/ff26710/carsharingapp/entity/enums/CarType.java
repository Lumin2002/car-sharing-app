package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarType {
    ECONOMY("ECONOMY", "经济型"),
    COMFORT("COMFORT", "舒适型"),
    PREMIUM("PREMIUM", "高端型");

    private final String code;
    private final String desc;
}
