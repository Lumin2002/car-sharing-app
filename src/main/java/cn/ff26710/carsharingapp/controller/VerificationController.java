package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.dto.verification.DriverLicenseSubmitDTO;
import cn.ff26710.carsharingapp.dto.verification.RealnameSubmitDTO;
import cn.ff26710.carsharingapp.dto.verification.VerificationPageDTO;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.DriverLicenseService;
import cn.ff26710.carsharingapp.service.RealnameAuthService;
import cn.ff26710.carsharingapp.vo.user.DriverLicenseVO;
import cn.ff26710.carsharingapp.vo.user.RealnameAuthVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import cn.ff26710.carsharingapp.vo.user.VerificationSummaryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户资质认证：实名认证 + 驾照认证
 */
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final RealnameAuthService realnameAuthService;
    private final DriverLicenseService driverLicenseService;

    /** 我的认证汇总（客户端「我的」页判断能否租车） */
    @GetMapping("/me")
    public ResultVO<VerificationSummaryVO> me() {
        RealnameAuthVO realname = realnameAuthService.getMine();
        DriverLicenseVO license = driverLicenseService.getMine();

        boolean realnamePassed = realname != null && realname.getStatus() == AuthStatus.APPROVED;
        boolean licensePassed = license != null && license.getStatus() == AuthStatus.APPROVED
                && !Boolean.TRUE.equals(license.getExpired());

        VerificationSummaryVO vo = new VerificationSummaryVO();
        vo.setRealname(realname);
        vo.setLicense(license);
        vo.setRealnamePassed(realnamePassed);
        vo.setLicensePassed(licensePassed);
        vo.setRentReady(realnamePassed && licensePassed);
        return ResultVO.success(vo);
    }

    // ------------------------------------------------------------------ 实名认证

    /** 提交/重新提交实名认证 */
    @OperLogAnnotation(operType = "VERIFY", operDesc = "提交实名认证")
    @PostMapping("/realname")
    public ResultVO<RealnameAuthVO> submitRealname(@Valid @RequestBody RealnameSubmitDTO dto) {
        return ResultVO.success(realnameAuthService.submit(dto));
    }

    /** 我的实名认证信息 */
    @GetMapping("/realname")
    public ResultVO<RealnameAuthVO> myRealname() {
        return ResultVO.success(realnameAuthService.getMine());
    }

    /** 实名认证分页（运维端审核） */
    @GetMapping("/realname/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<IPage<RealnameAuthVO>> realnamePage(@Valid @ModelAttribute VerificationPageDTO dto) {
        return ResultVO.success(realnameAuthService.pageAuth(
                dto.getPageNum(), dto.getPageSize(), dto.getStatus(), dto.getKeyword()));
    }

    /** 审核实名认证 */
    @OperLogAnnotation(operType = "VERIFY", operDesc = "审核实名认证")
    @PutMapping("/realname/{id}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> auditRealname(@PathVariable Long id,
                                        @RequestParam Boolean approved,
                                        @RequestParam(required = false) String reason) {
        if (approved == null) {
            throw new BusinessException("请指定审核结果");
        }
        realnameAuthService.audit(id, approved, reason);
        return ResultVO.success();
    }

    // ------------------------------------------------------------------ 驾照认证

    /** 提交/重新提交驾照认证 */
    @OperLogAnnotation(operType = "VERIFY", operDesc = "提交驾照认证")
    @PostMapping("/license")
    public ResultVO<DriverLicenseVO> submitLicense(@Valid @RequestBody DriverLicenseSubmitDTO dto) {
        return ResultVO.success(driverLicenseService.submit(dto));
    }

    /** 我的驾照认证信息 */
    @GetMapping("/license")
    public ResultVO<DriverLicenseVO> myLicense() {
        return ResultVO.success(driverLicenseService.getMine());
    }

    /** 驾照认证分页（运维端审核） */
    @GetMapping("/license/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<IPage<DriverLicenseVO>> licensePage(@Valid @ModelAttribute VerificationPageDTO dto) {
        return ResultVO.success(driverLicenseService.pageAuth(
                dto.getPageNum(), dto.getPageSize(), dto.getStatus(), dto.getKeyword()));
    }

    /** 审核驾照认证 */
    @OperLogAnnotation(operType = "VERIFY", operDesc = "审核驾照认证")
    @PutMapping("/license/{id}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<Void> auditLicense(@PathVariable Long id,
                                       @RequestParam Boolean approved,
                                       @RequestParam(required = false) String reason) {
        if (approved == null) {
            throw new BusinessException("请指定审核结果");
        }
        driverLicenseService.audit(id, approved, reason);
        return ResultVO.success();
    }
}
