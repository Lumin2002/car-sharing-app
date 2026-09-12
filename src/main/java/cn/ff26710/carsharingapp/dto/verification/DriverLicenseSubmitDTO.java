package cn.ff26710.carsharingapp.dto.verification;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 驾照认证提交入参
 */
@Data
public class DriverLicenseSubmitDTO {
    @NotBlank(message = "驾驶证号不能为空")
    private String licenseNo;

    private String licenseClass;
    private String licenseFront;
    private String licenseBack;
    private LocalDate issueDate;
    private LocalDate expireDate;
}
