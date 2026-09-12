package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarStatus {
    FREE("FREE","空闲"),
    RENTED("RENTED","租用"),
    BOOKED("BOOKED","维修"),
    MAINTENANCE("MAINTENANCE","预订"),
    DISABLED("DISABLED","停用");

    private final String code;
    private final String desc;
}
