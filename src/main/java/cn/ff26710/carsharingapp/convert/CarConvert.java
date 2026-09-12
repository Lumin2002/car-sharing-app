package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.vo.CarVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Car -> CarVO（对外视图，屏蔽 currentTenantId / supplierId / deleted）
 */
@Mapper
public interface CarConvert {

    CarConvert INSTANCE = Mappers.getMapper(CarConvert.class);

    CarVO toVO(Car car);
}
