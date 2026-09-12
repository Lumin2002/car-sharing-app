package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户驾照认证（一个用户一条记录）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_driver_license")
public class DriverLicense extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;

    private String licenseNo;
    private String licenseClass;
    private String licenseFront;
    private String licenseBack;
    private LocalDate issueDate;
    private LocalDate expireDate;

    private AuthStatus status;
    private String rejectReason;
    private LocalDateTime submitTime;
    private LocalDateTime auditTime;
    private Long auditorId;
}
