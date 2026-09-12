package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private Integer userVersion;
    private String username;
    private String password;
    private String avatar;
    private String phone;

    private UserRole role;
    private UserStatus status;

    private String loginIp;
    private LocalDateTime loginTime;
}
