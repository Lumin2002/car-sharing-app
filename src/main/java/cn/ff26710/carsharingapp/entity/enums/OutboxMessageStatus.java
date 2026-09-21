package cn.ff26710.carsharingapp.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxMessageStatus {
    PENDING("PENDING", "待投递"),
    PROCESSING("PROCESSING", "处理中"),
    SENT("SENT", "已投递"),
    FAIL("FAIL", "失效"),
    UNKNOWN("UNKNOWN", "未知"),
    SENT_BUT_DEAD("SENT_BUT_DEAD", "投递成功但消费失败进入死信");


    private final String code;
    private final String desc;
}
