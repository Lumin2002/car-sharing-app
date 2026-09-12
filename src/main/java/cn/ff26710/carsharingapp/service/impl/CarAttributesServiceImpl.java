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
            // 用显式 set 覆盖全部字段，而不是 updateById。
            // updateById 会跳过 null 字段，导致「把某项属性清空」保存不生效 ——
            // 而这里是整表单提交（PUT 语义就是把资源替换成提交的内容），null 也要写进去。
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
