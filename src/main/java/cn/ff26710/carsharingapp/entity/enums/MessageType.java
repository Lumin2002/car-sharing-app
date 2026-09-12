package cn.ff26710.carsharingapp.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {
    SYSTEM_NOTICE(101, "系统公告"),
    SYSTEM_ALERT(102, "系统告警"),

    ORDER_CREATE(201, "订单创建成功"),
    ORDER_PAID(202, "订单支付成功"),
    ORDER_RENT_START(203, "租车已开始，请按时还车"),
    ORDER_SOON_EXPIRE(204, "订单即将到期预警"),
    ORDER_OVERDUE(205, "订单逾期提醒"),
    ORDER_SETTLED(206, "订单结算完成通知"),
    ORDER_CANCEL(207, "订单已取消"),

    ACCOUNT_RECHARGE(301, "账户充值成功"),
    DEPOSIT_FROZEN(302, "押金冻结通知"),
    DEPOSIT_UNFREEZE(303, "押金解冻通知"),
    FEE_DEDUCT(304, "费用扣除通知"),
    REFUND_SUCCESS(305, "退款到账通知"),

    CAR_MAINTENANCE(401, "车辆维护通知");

    @EnumValue
    private final Integer code;
    private final String desc;
}
