package cn.ff26710.carsharingapp.dto.rental;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RentalPageDTO extends PageDTO {
    private Long userId;
    private Long carId;
    private RentalStatus status;
}
