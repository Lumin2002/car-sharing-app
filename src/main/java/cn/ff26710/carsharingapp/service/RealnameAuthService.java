package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.verification.RealnameSubmitDTO;
import cn.ff26710.carsharingapp.entity.RealnameAuth;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.vo.user.RealnameAuthVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RealnameAuthService extends IService<RealnameAuth> {
    RealnameAuth getByUserId(Long userId);
    RealnameAuthVO submit(RealnameSubmitDTO dto);
    RealnameAuthVO getMine();
    boolean isPassed(Long userId);
    IPage<RealnameAuthVO> pageAuth(long pageNum, long pageSize, AuthStatus status, String keyword);
    void audit(Long id, boolean approved, String reason);
}
