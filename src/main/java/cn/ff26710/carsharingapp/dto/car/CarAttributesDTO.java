package cn.ff26710.carsharingapp.dto.car;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CarAttributesDTO {
    @Min(value = 0, message = "电池容量不合法")
    private Integer batteryCapacity;
    private Boolean fastCharge;
    @Min(value = 0, message = "最大续航不合法")
    private Integer maxRange;

    private Boolean reverseCamera;
    private Boolean radar;
    private Boolean bluetooth;
    private Boolean airCondition;
    private Boolean cruiseControl;

    private Boolean sunroof;
    private Boolean leatherSeat;

    @Min(value = 0, message = "前备箱容积不合法")
    private Integer frontTrunkVolume;
    @Min(value = 0, message = "后备箱容积不合法")
    private Integer trunkVolume;
}
