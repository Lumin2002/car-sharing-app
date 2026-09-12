package cn.ff26710.carsharingapp.dto.payment;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的支付记录分页条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentPageDTO extends PageDTO {
    private PaymentStatus status;
}
