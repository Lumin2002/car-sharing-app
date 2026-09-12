package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.verification.DriverLicenseSubmitDTO;
import cn.ff26710.carsharingapp.entity.DriverLicense;
import cn.ff26710.carsharingapp.entity.enums.AuthStatus;
import cn.ff26710.carsharingapp.vo.user.DriverLicenseVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface DriverLicenseService extends IService<DriverLicense> {
    DriverLicense getByUserId(Long userId);
    DriverLicenseVO submit(DriverLicenseSubmitDTO dto);
    DriverLicenseVO getMine();
    boolean isPassed(Long userId);
    IPage<DriverLicenseVO> pageAuth(long pageNum, long pageSize, AuthStatus status, String keyword);
    void audit(Long id, boolean approved, String reason);
}
