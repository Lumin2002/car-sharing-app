package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.convert.DriverLicenseConvert;
import cn.ff26710.carsharingapp.dto.verification.DriverLicenseSubmitDTO;
import cn.ff26710.carsharingapp.entity.DriverLicense;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.DriverLicenseMapper;
import cn.ff26710.carsharingapp.service.DriverLicenseService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.vo.user.DriverLicenseVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DriverLicenseServiceImpl extends ServiceImpl<DriverLicenseMapper, DriverLicense>
        implements DriverLicenseService {

    @Override
    public DriverLicense getByUserId(Long userId) {
        return lambdaQuery().eq(DriverLicense::getUserId, userId).one();
    }

    @Override
    public DriverLicenseVO submit(DriverLicenseSubmitDTO dto) {
        Long userId = SecurityUtil.getUserId();

        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(LocalDate.now())) {
            throw new BusinessException("驾驶证已过期，请更换后再提交");
        }

        DriverLicense exist = getByUserId(userId);
        if (exist != null && exist.getStatus() == AuthStatus.APPROVED) {
            throw new BusinessException("驾照认证已通过，无需重复提交");
        }

        DriverLicense license = exist == null ? new DriverLicense() : exist;
        license.setUserId(userId);
        license.setLicenseNo(dto.getLicenseNo());
        license.setLicenseClass(dto.getLicenseClass());
        license.setLicenseFront(dto.getLicenseFront());
        license.setLicenseBack(dto.getLicenseBack());
        license.setIssueDate(dto.getIssueDate());
        license.setExpireDate(dto.getExpireDate());
        license.setStatus(AuthStatus.PENDING);
        license.setRejectReason(null);
        license.setSubmitTime(LocalDateTime.now());
        license.setAuditTime(null);
        license.setAuditorId(null);
        license.setUpdateTime(LocalDateTime.now());

        if (exist == null) {
            license.setCreateTime(LocalDateTime.now());
            save(license);
        } else {
            updateById(license);
        }
        return DriverLicenseConvert.INSTANCE.toVO(license);
    }

    @Override
    public DriverLicenseVO getMine() {
        DriverLicense license = getByUserId(SecurityUtil.getUserId());
        return license == null ? null : DriverLicenseConvert.INSTANCE.toVO(license);
    }

    @Override
    public boolean isPassed(Long userId) {
        DriverLicense license = getByUserId(userId);
        if (license == null || license.getStatus() != AuthStatus.APPROVED) {
            return false;
        }
        // 驾照过期也算未通过
        return license.getExpireDate() == null || !license.getExpireDate().isBefore(LocalDate.now());
    }

    @Override
    public IPage<DriverLicenseVO> pageAuth(long pageNum, long pageSize, AuthStatus status, String keyword) {
        LambdaQueryWrapper<DriverLicense> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DriverLicense::getLicenseNo, keyword)
                    .or().like(DriverLicense::getLicenseClass, keyword));
        }
        wrapper.eq(status != null, DriverLicense::getStatus, status).orderByDesc(DriverLicense::getId);

        Page<DriverLicense> page = page(new Page<>(pageNum, pageSize), wrapper);
        IPage<DriverLicenseVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(DriverLicenseConvert.INSTANCE::toVO).toList());
        return result;
    }

    @Override
    public void audit(Long id, boolean approved, String reason) {
        DriverLicense license = getById(id);
        if (license == null) {
            throw new BusinessException("驾照认证记录不存在");
        }
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException("驳回时必须填写原因");
        }
        license.setStatus(approved ? AuthStatus.APPROVED : AuthStatus.REJECTED);
        license.setRejectReason(approved ? null : reason);
        license.setAuditTime(LocalDateTime.now());
        license.setAuditorId(SecurityUtil.getUserId());
        license.setUpdateTime(LocalDateTime.now());
        updateById(license);
    }

}
