package cn.ff26710.carsharingapp.vo.user;

import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实名认证信息（身份证号做脱敏处理）
 */
@Data
public class RealnameAuthVO {
    private Long id;
    private Long userId;
    private String realName;
    /** 已脱敏，如 440101********1234 */
    private String idCardNo;
    private String idCardFront;
    private String idCardBack;
    private AuthStatus status;
    private String rejectReason;
    private LocalDateTime submitTime;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}
