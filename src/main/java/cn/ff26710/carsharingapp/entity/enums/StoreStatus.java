package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StoreStatus {
    OPEN("OPEN", "营业中"),
    CLOSED("CLOSED", "已打烊");

    private final String code;
    private final String desc;
}
