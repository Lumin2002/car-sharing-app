package cn.ff26710.carsharingapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度必须为8-32位")
    private String password;
    @Size(min = 8, max = 32, message = "密码长度必须为8-32位")
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
    @NotBlank(message = "手机号不能为空")
    private String phone;
}
