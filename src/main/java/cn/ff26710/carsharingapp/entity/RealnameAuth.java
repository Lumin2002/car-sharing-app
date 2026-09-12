package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_realname_auth")
public class RealnameAuth extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;

    private String realName;
    private String idCardNo;
    private String idCardFront;
    private String idCardBack;

    private AuthStatus status;
    private String rejectReason;
    private LocalDateTime submitTime;
    private LocalDateTime auditTime;
    private Long auditorId;
}
