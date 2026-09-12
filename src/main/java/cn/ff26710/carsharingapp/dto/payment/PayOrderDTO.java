package cn.ff26710.carsharingapp.dto.payment;

import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单支付入参：一次性支付「租金」并「冻结押金」
 */
@Data
public class PayOrderDTO {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "支付方式不能为空")
    private PayMethod payMethod;
}
