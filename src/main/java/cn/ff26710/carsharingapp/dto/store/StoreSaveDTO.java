package cn.ff26710.carsharingapp.dto.store;

import cn.ff26710.carsharingapp.entity.enums.StoreStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店新增/修改入参
 */
@Data
public class StoreSaveDTO {
    @NotBlank(message = "门店名称不能为空")
    private String name;

    private String address;

    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    private String phone;
    private String businessHours;
    private StoreStatus status;
    private String remark;
}
