package cn.ff26710.carsharingapp.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LogResults {
    SUCCESS(0, "成功"),
    FAIL(1, "失败");
    @EnumValue
    private final Integer code;
    private final String desc;
}
