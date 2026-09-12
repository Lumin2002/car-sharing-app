package cn.ff26710.carsharingapp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("car_attributes")
public class CarAttributes extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
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
}
