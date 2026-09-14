package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.car.CarAttributesDTO;
import cn.ff26710.carsharingapp.entity.CarAttributes;
import cn.ff26710.carsharingapp.mapper.CarAttributesMapper;
import cn.ff26710.carsharingapp.service.CarAttributesService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CarAttributesServiceImpl extends ServiceImpl<CarAttributesMapper, CarAttributes> implements CarAttributesService {

    @Override
    public CarAttributes getByCarId(Long carId) {
        return lambdaQuery()
                .eq(CarAttributes::getCarId, carId)
                .one();
    }

    @Override
    public void saveOrUpdateByCarId(Long carId, CarAttributesDTO dto) {
        CarAttributes exist = getByCarId(carId);

        CarAttributes attributes = new CarAttributes();
        BeanUtils.copyProperties(dto, attributes);
        attributes.setCarId(carId);
        attributes.setUpdateTime(LocalDateTime.now());

        if (exist == null) {
            attributes.setCreateTime(LocalDateTime.now());
            save(attributes);
        } else {
            lambdaUpdate()
                    .eq(CarAttributes::getCarId, carId)
                    .set(CarAttributes::getBatteryCapacity, dto.getBatteryCapacity())
                    .set(CarAttributes::getFastCharge, dto.getFastCharge())
                    .set(CarAttributes::getMaxRange, dto.getMaxRange())
                    .set(CarAttributes::getReverseCamera, dto.getReverseCamera())
                    .set(CarAttributes::getRadar, dto.getRadar())
                    .set(CarAttributes::getBluetooth, dto.getBluetooth())
                    .set(CarAttributes::getAirCondition, dto.getAirCondition())
                    .set(CarAttributes::getCruiseControl, dto.getCruiseControl())
                    .set(CarAttributes::getSunroof, dto.getSunroof())
                    .set(CarAttributes::getLeatherSeat, dto.getLeatherSeat())
                    .set(CarAttributes::getFrontTrunkVolume, dto.getFrontTrunkVolume())
                    .set(CarAttributes::getTrunkVolume, dto.getTrunkVolume())
                    .set(CarAttributes::getUpdateTime, LocalDateTime.now())
                    .update();
        }
    }

    @Override
    public void removeByCarId(Long carId) {
        remove(Wrappers.<CarAttributes>lambdaQuery().eq(CarAttributes::getCarId, carId));
    }
}
