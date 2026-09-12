package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.car.CarAttributesDTO;
import cn.ff26710.carsharingapp.entity.CarAttributes;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CarAttributesService extends IService<CarAttributes> {
    CarAttributes getByCarId(Long carId);
    void saveOrUpdateByCarId(Long carId, CarAttributesDTO dto);
    void removeByCarId(Long carId);
}
