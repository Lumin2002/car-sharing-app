package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.CarAttributes;
import cn.ff26710.carsharingapp.vo.CarAttributesVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * CarAttributes -> CarAttributesVO（对外视图，屏蔽 deleted）
 */
@Mapper
public interface CarAttributesConvert {

    CarAttributesConvert INSTANCE = Mappers.getMapper(CarAttributesConvert.class);

    CarAttributesVO toVO(CarAttributes attributes);
}
