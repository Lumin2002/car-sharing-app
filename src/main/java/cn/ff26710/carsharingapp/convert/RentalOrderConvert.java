package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.vo.RentalOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * RentalOrder -> RentalOrderVO（对外视图，屏蔽 deleted）
 */
@Mapper
public interface RentalOrderConvert {

    RentalOrderConvert INSTANCE = Mappers.getMapper(RentalOrderConvert.class);

    RentalOrderVO toVO(RentalOrder order);
}
