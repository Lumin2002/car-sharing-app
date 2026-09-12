package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.DriverLicense;
import cn.ff26710.carsharingapp.vo.user.DriverLicenseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * DriverLicense -> DriverLicenseVO。
 * 除了驾驶证号脱敏，还要算出「是否已过期」，逻辑收敛在这里，调用方不用关心。
 */
@Mapper
public interface DriverLicenseConvert {

    DriverLicenseConvert INSTANCE = Mappers.getMapper(DriverLicenseConvert.class);

    String MASK_EXPRESSION = "java(cn.ff26710.carsharingapp.utils.MaskUtil.mask(license.getLicenseNo(), 4, 4))";
    String EXPIRED_EXPRESSION =
            "java(license.getExpireDate() != null && license.getExpireDate().isBefore(java.time.LocalDate.now()))";

    @Mapping(target = "licenseNo", expression = MASK_EXPRESSION)
    @Mapping(target = "expired", expression = EXPIRED_EXPRESSION)
    DriverLicenseVO toVO(DriverLicense license);
}
