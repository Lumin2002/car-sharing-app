package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.RealnameAuth;
import cn.ff26710.carsharingapp.vo.user.RealnameAuthVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * RealnameAuth -> RealnameAuthVO。
 * 身份证号必须脱敏后再返回给前端，脱敏规则写在转换器里，避免每个调用点各写一遍。
 */
@Mapper
public interface RealnameAuthConvert {

    RealnameAuthConvert INSTANCE = Mappers.getMapper(RealnameAuthConvert.class);

    String MASK_EXPRESSION = "java(cn.ff26710.carsharingapp.utils.MaskUtil.mask(auth.getIdCardNo(), 6, 4))";

    @Mapping(target = "idCardNo", expression = MASK_EXPRESSION)
    RealnameAuthVO toVO(RealnameAuth auth);
}
