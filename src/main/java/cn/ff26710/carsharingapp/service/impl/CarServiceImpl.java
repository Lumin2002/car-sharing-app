package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.car.CarSaveDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.CarMapper;
import cn.ff26710.carsharingapp.service.CarAttributesService;
import cn.ff26710.carsharingapp.service.CarService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CarServiceImpl extends ServiceImpl<CarMapper, Car> implements CarService {
    private final CarAttributesService carAttributesService;

    @Override
    public Long addCar(CarSaveDTO dto) {
        if (lambdaQuery().eq(Car::getVin, dto.getVin()).exists()) {
            throw new BusinessException("该VIN码已存在");
        }
        if (lambdaQuery().eq(Car::getPlateNo, dto.getPlateNo()).exists()) {
            throw new BusinessException("该车牌号已存在");
        }

        Car car = new Car();
        BeanUtils.copyProperties(dto, car);
        // 未指定状态时默认空闲
        if (car.getStatus() == null) {
            car.setStatus(CarStatus.FREE);
        }
        // 新车没有承租人
        car.setCurrentTenantId(null);
        car.setCreateTime(LocalDateTime.now());
        car.setUpdateTime(LocalDateTime.now());
        save(car);
        return car.getCarId();
    }

    @Override
    public void updateCar(Long carId, CarSaveDTO dto) {
        Car exist = getById(carId);
        if (exist == null) {
            throw new BusinessException("车辆不存在");
        }
        if (StringUtils.hasText(dto.getVin()) && !dto.getVin().equals(exist.getVin())
                && lambdaQuery().eq(Car::getVin, dto.getVin()).exists()) {
            throw new BusinessException("该VIN码已存在");
        }
        if (StringUtils.hasText(dto.getPlateNo()) && !dto.getPlateNo().equals(exist.getPlateNo())
                && lambdaQuery().eq(Car::getPlateNo, dto.getPlateNo()).exists()) {
            throw new BusinessException("该车牌号已存在");
        }

        Car car = new Car();
        BeanUtils.copyProperties(dto, car);
        car.setCarId(carId);
        car.setUpdateTime(LocalDateTime.now());
        updateById(car);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCar(Long carId) {
        if (getById(carId) == null) {
            throw new BusinessException("车辆不存在");
        }
        Car car = getById(carId);
        if (car.getStatus() == CarStatus.RENTED) {
            throw new BusinessException("车辆正在被租用，无法删除该车辆");
        }
        if (car.getStatus() == CarStatus.DISABLED) {
            removeById(carId);
            carAttributesService.removeByCarId(carId);
        } else {
            throw new BusinessException("车辆状态必须为停用才能被删除");
        }
    }

    @Override
    public void changeStatus(Long carId, CarStatus status) {
        boolean update = lambdaUpdate()
                .eq(Car::getCarId, carId)
                .set(Car::getStatus, status)
                .set(Car::getUpdateTime, LocalDateTime.now())
                .update();
        if (!update) {
            throw new BusinessException("车辆不存在");
        }
    }

    @Override
    public IPage<Car> pageCars(long pageNum, long pageSize, String keyword, CarStatus status, CarType type, Long storeId) {
        LambdaQueryWrapper<Car> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Car::getBrand, keyword)
                    .or().like(Car::getModel, keyword)
                    .or().like(Car::getPlateNo, keyword)
                    .or().like(Car::getVin, keyword));
        }
        wrapper.eq(status != null, Car::getStatus, status)
                .eq(type != null, Car::getType, type)
                .eq(storeId != null, Car::getStoreId, storeId)
                .orderByDesc(Car::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
