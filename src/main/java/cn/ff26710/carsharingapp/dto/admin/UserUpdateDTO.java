package cn.ff26710.carsharingapp.dto.admin;

import cn.ff26710.carsharingapp.entity.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateDTO {
    @NotNull(message = "用户名不能为空")
    private String username;
    private String avatar;
    @NotNull(message = "身份不能为空")
    private UserRole role;
}
