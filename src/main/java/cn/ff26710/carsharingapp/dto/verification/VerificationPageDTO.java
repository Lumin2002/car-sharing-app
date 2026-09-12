package cn.ff26710.carsharingapp.dto.verification;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 认证审核分页条件（实名认证与驾照认证共用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VerificationPageDTO extends PageDTO {
    private AuthStatus status;
    private String keyword;
}
