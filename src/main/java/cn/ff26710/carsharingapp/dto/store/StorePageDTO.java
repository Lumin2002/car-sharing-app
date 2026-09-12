package cn.ff26710.carsharingapp.dto.store;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StorePageDTO extends PageDTO {
    private String keyword;
    private StoreStatus status;
}
