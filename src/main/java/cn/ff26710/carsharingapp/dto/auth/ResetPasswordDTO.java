package cn.ff26710.carsharingapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordDTO {
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    private String imageCode;
    private String imageUuid;
}
