package cn.ff26710.carsharingapp.vo.user;

import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long userId;
    private String username;
    private String avatar;
    private String phone;

    private UserRole role;
    private UserStatus status;

    /** 与 User.loginIp 同名，MapStruct 才能自动映射 */
    private String loginIp;
    private LocalDateTime createTime;
    /** 与 User.loginTime 同名，MapStruct 才能自动映射 */
    private LocalDateTime loginTime;
}
