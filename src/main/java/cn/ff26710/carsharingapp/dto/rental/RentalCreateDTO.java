package cn.ff26710.carsharingapp.dto.rental;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RentalCreateDTO {
    @NotNull(message = "车辆ID不能为空")
    private Long carId;

    @NotNull(message = "预计还车时间不能为空")
    @Future(message = "预计还车时间必须晚于当前时间")
    private LocalDateTime endTime;

    private Long returnStoreId;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
