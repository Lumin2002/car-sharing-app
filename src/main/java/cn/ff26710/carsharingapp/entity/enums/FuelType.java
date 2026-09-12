package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FuelType {
    GASOLINE("GASOLINE", "汽油"),
    DIESEL("DIESEL", "柴油"),
    ELECTRIC("ELECTRIC", "新能源"),
    HYBRID("HYBRID", "混动");

    private final String code;
    private final String desc;
}
