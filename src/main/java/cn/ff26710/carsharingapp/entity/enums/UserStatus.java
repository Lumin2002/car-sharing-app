package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum UserStatus {
    ENABLED("ENABLED","正常"),
    DISABLED("DISABLED","禁用");

    private final String code;
    private final String desc;
}
