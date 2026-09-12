package cn.ff26710.carsharingapp.dto.rental;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RentalReturnDTO {
    @Min(value = 0, message = "还车里程不合法")
    private Integer mileageAfter;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
