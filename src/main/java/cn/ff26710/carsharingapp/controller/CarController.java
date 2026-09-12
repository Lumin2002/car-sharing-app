package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.CarAttributesConvert;
import cn.ff26710.carsharingapp.convert.CarConvert;
import cn.ff26710.carsharingapp.dto.car.CarAttributesDTO;
import cn.ff26710.carsharingapp.dto.car.CarPageDTO;
import cn.ff26710.carsharingapp.dto.car.CarSaveDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.CarAttributes;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.CarAttributesService;
import cn.ff26710.carsharingapp.service.CarService;
import cn.ff26710.carsharingapp.vo.CarAttributesVO;
import cn.ff26710.carsharingapp.vo.CarVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/car")
@RequiredArgsConstructor
public class CarController {

    private final CarService carService;
    private final CarAttributesService carAttributesService;

    @GetMapping("/page")
    public ResultVO<IPage<CarVO>> page(@Valid @ModelAttribute CarPageDTO dto) {
        IPage<Car> page = carService.pageCars(dto.getPageNum(),
                dto.getPageSize(), dto.getKeyword(), dto.getStatus(),
                dto.getType(), dto.getStoreId());
        return ResultVO.success(page.convert(CarConvert.INSTANCE::toVO));
    }

    @GetMapping("/{id}")
    public ResultVO<CarVO> detail(@PathVariable Long id) {
        Car car = carService.getById(id);
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }
        return ResultVO.success(CarConvert.INSTANCE.toVO(car));
    }

    @OperLogAnnotation(operType = "CAR", operDesc = "新增车辆")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Long> add(@Valid @RequestBody CarSaveDTO dto) {
        return ResultVO.success(carService.addCar(dto));
    }

    @OperLogAnnotation(operType = "CAR", operDesc = "修改车辆")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> update(@PathVariable Long id, @Valid @RequestBody CarSaveDTO dto) {
        carService.updateCar(id, dto);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "CAR", operDesc = "删除车辆")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> remove(@PathVariable Long id) {
        carService.removeCar(id);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "CAR", operDesc = "变更车辆状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> changeStatus(@PathVariable Long id, @RequestParam CarStatus status) {
        carService.changeStatus(id, status);
        return ResultVO.success();
    }

    @GetMapping("/{id}/attributes")
    public ResultVO<CarAttributesVO> attributes(@PathVariable Long id) {
        return ResultVO.success(CarAttributesConvert.INSTANCE.toVO(
                carAttributesService.getByCarId(id)));
    }

    @OperLogAnnotation(operType = "CAR", operDesc = "保存车辆属性")
    @PutMapping("/{id}/attributes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> saveAttributes(@PathVariable Long id, @Valid @RequestBody CarAttributesDTO dto) {
        carAttributesService.saveOrUpdateByCarId(id, dto);
        return ResultVO.success();
    }
}
