package cn.ff26710.carsharingapp.dto.rental;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RentalPickUpDTO {
    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
