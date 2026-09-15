package cn.ff26710.carsharingapp.dto.payment;

import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayOrderDTO {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "支付方式不能为空")
    private PayMethod payMethod;

    @NotNull(message = "支付类型不能为空")
    private PayType payType;

    private String code;
}
