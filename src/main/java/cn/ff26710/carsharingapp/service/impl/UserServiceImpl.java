package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.convert.UserConvert;
import cn.ff26710.carsharingapp.dto.admin.UserAddDTO;
import cn.ff26710.carsharingapp.dto.admin.UserUpdateDTO;
import cn.ff26710.carsharingapp.dto.user.UserChangePwDTO;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.UserMapper;
import cn.ff26710.carsharingapp.service.RefreshTokenService;
import cn.ff26710.carsharingapp.service.UserService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import cn.hutool.core.util.ReUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    @Override
    public void addUser(UserAddDTO dto) {
        if (existByPhone(dto.getPhone())) {
            throw new BusinessException("该手机号已存在");
        }
        User user = new User();
        user.setUserVersion(1);
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setAvatar(dto.getAvatar());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus());
        user.setCreateTime(LocalDateTime.now());
        save(user);
    }

    @Override
    public void updateUser(Long id, UserUpdateDTO dto) {
        boolean success = lambdaUpdate()
                .eq(User::getUserId, id)
                .set(User::getUsername, dto.getUsername())
                .set(User::getAvatar, dto.getAvatar())
                .set(User::getRole, dto.getRole())
                .update();
        if (!success) {
            throw new BusinessException("用户不存在");
        }
    }

    @Override
    public void updateAvatar(String avatarUrl) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        boolean success = lambdaUpdate()
                .eq(User::getUserId, loginUser.getUserId())
                .set(User::getAvatar, avatarUrl)
                .update();
        if (!success) {
            throw new BusinessException("头像更新失败");
        }
    }

    @Override
    public void banUser(Long userId, UserStatus status) {
        if (status != UserStatus.ENABLED && status != UserStatus.DISABLED){
            throw new BusinessException("状态参数有问题");
        }
        boolean update = lambdaUpdate()
                .eq(User::getUserId, userId)
                .set(User::getStatus, status)
                .update();
        if (!update){
            throw new BusinessException("用户不存在");
        }
    }

    @Override
    public Optional<User> getUserByPhone(String phone) {
        return lambdaQuery()
                .eq(User::getPhone, phone)
                .oneOpt();
    }

    @Override
    public boolean existByPhone(String phone) {
        return lambdaQuery()
                .eq(User::getPhone, phone)
                .exists();
    }

    @Override
    public IPage<UserVO> pageUsers(long pageNum, long pageSize, String keyword, UserRole role, UserStatus status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword));
        }
        wrapper.eq(role != null, User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getCreateTime);

        Page<User> result = page(new Page<>(pageNum, pageSize), wrapper);
        return result.convert(UserConvert.INSTANCE::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UserChangePwDTO dto) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException("未登录");
        }
        if (!isValidPassword(dto.getNewPassword())) {
            throw new BusinessException("密码至少8位，且需同时包含大写字母、小写字母和数字");
        }
        if (dto.getNewPassword().equals(dto.getOldPassword())) {
            throw new BusinessException("新密码与旧密码一致");
        }

        User dbUser = lambdaQuery()
                .eq(User::getUserId, loginUser.getUserId())
                .select(User::getPassword)
                .one();

        if (dbUser == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), dbUser.getPassword())) {
            throw new BusinessException("旧密码校验失败");
        }

        boolean update = lambdaUpdate().eq(User::getUserId, loginUser.getUserId())
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword()))
                .set(User::getUpdateTime, LocalDateTime.now())
                .setSql("user_version = user_version + 1")
                .update();
        if (!update){
            throw new BusinessException("密码修改失败");
        }
        refreshTokenService.revokeAllByUserId(loginUser.getUserId());
    }
    public boolean isValidPassword(String pwd){
        if(org.apache.commons.lang3.StringUtils.isBlank(pwd)){
            return false;
        }
        String regex = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$";
        return ReUtil.isMatch(regex, pwd);
    }
}
