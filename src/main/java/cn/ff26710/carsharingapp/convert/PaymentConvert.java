package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.vo.PaymentVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Payment -> PaymentVO（对外视图，屏蔽 rawCallback）
 */
@Mapper
public interface PaymentConvert {

    PaymentConvert INSTANCE = Mappers.getMapper(PaymentConvert.class);

    PaymentVO toVO(Payment payment);
}
