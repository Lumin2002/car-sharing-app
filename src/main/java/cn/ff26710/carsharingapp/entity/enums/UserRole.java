package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum UserRole {
    USER("USER", "普通用户"),
    SUPPLIER("SUPPLIER", "供应商"),
    OPERATIONS("OPERATIONS", "运维人员"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String desc;

}
