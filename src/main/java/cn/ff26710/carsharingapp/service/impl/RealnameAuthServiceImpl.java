package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.convert.RealnameAuthConvert;
import cn.ff26710.carsharingapp.dto.verification.RealnameSubmitDTO;
import cn.ff26710.carsharingapp.entity.RealnameAuth;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RealnameAuthMapper;
import cn.ff26710.carsharingapp.service.RealnameAuthService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.vo.user.RealnameAuthVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class RealnameAuthServiceImpl extends ServiceImpl<RealnameAuthMapper, RealnameAuth>
        implements RealnameAuthService {

    @Override
    public RealnameAuth getByUserId(Long userId) {
        return lambdaQuery().eq(RealnameAuth::getUserId, userId).one();
    }

    @Override
    public RealnameAuthVO submit(RealnameSubmitDTO dto) {
        Long userId = SecurityUtil.getUserId();
        RealnameAuth exist = getByUserId(userId);
        if (exist != null && exist.getStatus() == AuthStatus.APPROVED) {
            throw new BusinessException("实名认证已通过，无需重复提交");
        }

        RealnameAuth auth = exist == null ? new RealnameAuth() : exist;
        auth.setUserId(userId);
        auth.setRealName(dto.getRealName());
        auth.setIdCardNo(dto.getIdCardNo());
        auth.setIdCardFront(dto.getIdCardFront());
        auth.setIdCardBack(dto.getIdCardBack());
        auth.setStatus(AuthStatus.PENDING);
        auth.setRejectReason(null);
        auth.setSubmitTime(LocalDateTime.now());
        auth.setAuditTime(null);
        auth.setAuditorId(null);
        auth.setUpdateTime(LocalDateTime.now());

        if (exist == null) {
            auth.setCreateTime(LocalDateTime.now());
            save(auth);
        } else {
            updateById(auth);
        }
        return RealnameAuthConvert.INSTANCE.toVO(auth);
    }

    @Override
    public RealnameAuthVO getMine() {
        RealnameAuth auth = getByUserId(SecurityUtil.getUserId());
        return auth == null ? null : RealnameAuthConvert.INSTANCE.toVO(auth);
    }

    @Override
    public boolean isPassed(Long userId) {
        RealnameAuth auth = getByUserId(userId);
        return auth != null && auth.getStatus() == AuthStatus.APPROVED;
    }

    @Override
    public IPage<RealnameAuthVO> pageAuth(long pageNum, long pageSize, AuthStatus status, String keyword) {
        LambdaQueryWrapper<RealnameAuth> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RealnameAuth::getRealName, keyword)
                    .or().like(RealnameAuth::getIdCardNo, keyword));
        }
        wrapper.eq(status != null, RealnameAuth::getStatus, status).orderByDesc(RealnameAuth::getId);

        Page<RealnameAuth> page = page(new Page<>(pageNum, pageSize), wrapper);
        IPage<RealnameAuthVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(RealnameAuthConvert.INSTANCE::toVO).toList());
        return result;
    }

    @Override
    public void audit(Long id, boolean approved, String reason) {
        RealnameAuth auth = getById(id);
        if (auth == null) {
            throw new BusinessException("实名认证记录不存在");
        }
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException("驳回时必须填写原因");
        }
        auth.setStatus(approved ? AuthStatus.APPROVED : AuthStatus.REJECTED);
        auth.setRejectReason(approved ? null : reason);
        auth.setAuditTime(LocalDateTime.now());
        auth.setAuditorId(SecurityUtil.getUserId());
        auth.setUpdateTime(LocalDateTime.now());
        updateById(auth);
    }

}
