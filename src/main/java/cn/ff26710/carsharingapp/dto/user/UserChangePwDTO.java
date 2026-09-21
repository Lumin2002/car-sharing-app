package cn.ff26710.carsharingapp.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserChangePwDTO {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码至少8位，且需同时包含大写字母、小写字母和数字")
    private String newPassword;
}
