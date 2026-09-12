package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.Store;
import cn.ff26710.carsharingapp.vo.StoreVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface StoreConvert {

    StoreConvert INSTANCE = Mappers.getMapper(StoreConvert.class);

    @Mapping(target = "rentableCarCount", ignore = true)
    @Mapping(target = "totalCarCount", ignore = true)
    StoreVO toVO(Store store);
}
