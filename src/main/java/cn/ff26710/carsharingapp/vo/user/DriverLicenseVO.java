package cn.ff26710.carsharingapp.vo.user;

import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 驾照认证信息（驾驶证号做脱敏处理）
 */
@Data
public class DriverLicenseVO {
    private Long id;
    private Long userId;
    /** 已脱敏 */
    private String licenseNo;
    private String licenseClass;
    private String licenseFront;
    private String licenseBack;
    private LocalDate issueDate;
    private LocalDate expireDate;
    /** 是否已过期 */
    private Boolean expired;
    private AuthStatus status;
    private String rejectReason;
    private LocalDateTime submitTime;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}
