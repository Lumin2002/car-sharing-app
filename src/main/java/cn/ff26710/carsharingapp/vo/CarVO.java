package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import cn.ff26710.carsharingapp.entity.enums.FuelType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆信息（对外）。
 *
 * <p>相比 Car 实体去掉了三类不该外泄的内容：
 * <ul>
 *   <li>{@code currentTenantId} —— 当前承租人ID。/api/car/** 是匿名可访问的，
 *       直接返回实体会让任何人都能查到「谁正在租这辆车」；</li>
 *   <li>{@code supplierId} —— 内部供应商归属，前端用不到；</li>
 *   <li>{@code deleted} —— 逻辑删除标记，属于存储细节。</li>
 * </ul>
 * 另外顺带去掉了 createTime/updateTime 之外的审计噪声。
 */
@Data
public class CarVO {
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
    private BigDecimal dailyPrice;
    private BigDecimal deposit;

    private CarStatus status;
    private Integer mileage;
    private LocalDateTime insuranceExpire;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
