package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.vo.RefundVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Refund -> RefundVO（对外视图，屏蔽 rawCallback）
 */
@Mapper
public interface RefundConvert {

    RefundConvert INSTANCE = Mappers.getMapper(RefundConvert.class);

    RefundVO toVO(Refund refund);
}
