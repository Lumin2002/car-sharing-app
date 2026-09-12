package cn.ff26710.carsharingapp.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageStatus {
    UNREAD(0, "未读"),
    READ(1, "已读");
    @EnumValue
    private final Integer code;
    private final String desc;
}
