package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.car.CarSaveDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CarService extends IService<Car> {
    Long addCar(CarSaveDTO dto);
    void updateCar(Long carId, CarSaveDTO dto);
    void removeCar(Long carId);
    void changeStatus(Long carId, CarStatus status);
    IPage<Car> pageCars(long pageNum, long pageSize, String keyword, CarStatus status, CarType type, Long storeId);
}
