package cn.ff26710.carsharingapp.dto.car;

import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.entity.enums.FuelType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CarSaveDTO {
    @NotBlank(message = "VIN码不能为空")
    private String vin;
    @NotBlank(message = "车牌号不能为空")
    private String plateNo;
    @NotBlank(message = "品牌不能为空")
    private String brand;
    private String model;
    private String color;
    private String coverImg;
    @Min(value = 1, message = "座位数不合法")
    private Integer seatNum;
    @Min(value = 1, message = "车门数不合法")
    private Integer doorNum;
    private FuelType fuelType;
    private Boolean automaticGear;
    private CarType type;
    private Long storeId;

    @NotNull(message = "日租金不能为空")
    @DecimalMin(value = "0", message = "日租金不能为负数")
    private BigDecimal dailyPrice;
    @DecimalMin(value = "0", message = "押金不能为负数")
    private BigDecimal deposit;
    private CarStatus status;
    @Min(value = 0, message = "里程数不合法")
    private Integer mileage;
    private LocalDateTime insuranceExpire;
}
