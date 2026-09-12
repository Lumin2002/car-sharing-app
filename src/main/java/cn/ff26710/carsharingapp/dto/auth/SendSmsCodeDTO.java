package cn.ff26710.carsharingapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendSmsCodeDTO {
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String imageCode;
    private String imageUuid;
}
