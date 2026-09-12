package cn.ff26710.carsharingapp.dto.car;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.CarType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CarPageDTO extends PageDTO {
    private String keyword;
    private CarStatus status;
    private CarType type;
    private Long storeId;
}
