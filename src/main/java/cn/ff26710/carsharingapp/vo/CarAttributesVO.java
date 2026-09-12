package cn.ff26710.carsharingapp.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆属性（对外）。
 * 只去掉继承自 BaseLogicEntity 的 deleted 存储标记。
 */
@Data
public class CarAttributesVO {
    private Long attrId;
    private Long carId;

    private Integer batteryCapacity;
    private Boolean fastCharge;
    private Integer maxRange;

    private Boolean reverseCamera;
    private Boolean radar;
    private Boolean bluetooth;
    private Boolean airCondition;
    private Boolean cruiseControl;

    private Boolean sunroof;
    private Boolean leatherSeat;

    private Integer frontTrunkVolume;
    private Integer trunkVolume;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
