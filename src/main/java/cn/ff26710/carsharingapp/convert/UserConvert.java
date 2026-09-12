package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConvert {
    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);
    UserVO toVO(User user);
}
