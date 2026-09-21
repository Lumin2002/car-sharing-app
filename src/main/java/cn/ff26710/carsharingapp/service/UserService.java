package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.admin.UserAddDTO;
import cn.ff26710.carsharingapp.dto.admin.UserUpdateDTO;
import cn.ff26710.carsharingapp.dto.user.UserChangePwDTO;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Optional;

public interface UserService extends IService<User> {
    void addUser(UserAddDTO dto);
    void updateUser(Long id, UserUpdateDTO dto);
    void updateAvatar(String avatarUrl);
    void banUser(Long userId, UserStatus status);
    Optional<User> getUserByPhone(String phone);
    boolean existByPhone(String phone);
    void changePassword(UserChangePwDTO dto);
    IPage<UserVO> pageUsers(long pageNum, long pageSize, String keyword, UserRole role, UserStatus status);
}
