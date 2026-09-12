package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.entity.enums.FuelType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("car")
public class Car extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
    private Long carId;
    private String vin;
    private String plateNo;
    private String brand;
    private String model;
    private String color;
    private String coverImg;
    private Integer seatNum;
    private Integer doorNum;
    private FuelType fuelType;
    private Boolean automaticGear;
    private CarType type;
    private Long storeId;
    private Long supplierId;

    private BigDecimal dailyPrice;
    private BigDecimal deposit;
    private CarStatus status;
    private Long currentTenantId;

    private Integer mileage;

    private LocalDateTime insuranceExpire;
}
