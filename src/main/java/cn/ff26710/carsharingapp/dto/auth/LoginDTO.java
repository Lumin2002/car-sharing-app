package cn.ff26710.carsharingapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "登录类型不能为空")
    private String loginType;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @Size(min = 8, max = 32, message = "密码长度必须为8-32位")
    private String password;
    private String smsCode;
    private String ip;

    private String imageCode;
    private String imageUuid;
}
