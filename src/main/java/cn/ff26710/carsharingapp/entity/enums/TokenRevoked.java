package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenRevoked {
    VALID("VALID", "有效"),
    REVOKED("REVOKED", "作废");

    private final String code;
    private final String desc;

}