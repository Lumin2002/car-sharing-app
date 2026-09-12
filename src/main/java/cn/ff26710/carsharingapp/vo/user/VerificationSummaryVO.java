package cn.ff26710.carsharingapp.vo.user;

import lombok.Data;

/**
 * 我的认证状态汇总
 */
@Data
public class VerificationSummaryVO {
    private RealnameAuthVO realname;
    private DriverLicenseVO license;

    /** 实名是否已通过 */
    private boolean realnamePassed;
    /** 驾照是否已通过 */
    private boolean licensePassed;
    /** 是否满足租车条件（实名 + 驾照都通过） */
    private boolean rentReady;
}
