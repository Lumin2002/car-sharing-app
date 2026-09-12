package cn.ff26710.carsharingapp.dto.admin;

import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserAddDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String avatar;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @NotNull(message = "身份不能为空")
    private UserRole role;
    @NotNull(message = "状态不能为空")
    private UserStatus status;
}
