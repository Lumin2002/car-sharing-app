package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.convert.UserConvert;
import cn.ff26710.carsharingapp.dto.auth.LoginDTO;
import cn.ff26710.carsharingapp.dto.auth.RegisterDTO;
import cn.ff26710.carsharingapp.dto.auth.ResetPasswordDTO;
import cn.ff26710.carsharingapp.entity.RefreshToken;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.security.SmsCodeAuthenticationToken;
import cn.ff26710.carsharingapp.service.*;
import cn.ff26710.carsharingapp.utils.JwtUtil;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.vo.LoginVO;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import cn.hutool.core.util.ReUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final JwtBlacklistService jwtBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final CaptchaService captchaService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_SECONDS = 600;
    private static final long FAIL_COUNT_TTL = 600;

    private static final String SMS_CAPTCHA_KEY = "captcha:sms:";
    private static final String LOGIN_FAIL_COUNT_KEY = "login:fail:count:";
    private static final String LOGIN_LOCK_KEY = "login:lock:";

    @Override
    public void register(RegisterDTO dto) {
        String phoneReg = "^1[3-9]\\d{9}$";
        if (!ReUtil.isMatch(phoneReg, dto.getPhone())) {
            throw new BusinessException("手机号不正确");
        }
        if (!isValidPassword(dto.getPassword())) {
            throw new BusinessException("密码至少8位，且需同时包含大写字母、小写字母和数字");
        }
        if (!dto.getConfirmPassword().equals(dto.getPassword())) {
            throw new BusinessException("两次的密码不一致");
        }
        if (userService.existByPhone(dto.getPhone())) {
            throw new BusinessException("该手机号已注册");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUserVersion(1);
        userService.save(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        Authentication authentication;
        if ("PASSWORD".equals(dto.getLoginType())) {
            String phone = dto.getPhone();
            String lockKey = LOGIN_LOCK_KEY + phone;
            if (stringRedisTemplate.hasKey(lockKey)) {
                throw new BusinessException("登录失败次数过多，请10分钟后重试");
            }

            captchaService.verifyImageCaptcha(dto.getImageCode(), dto.getImageUuid());
            try {
                UsernamePasswordAuthenticationToken token =
                        new UsernamePasswordAuthenticationToken(phone, dto.getPassword());
                authentication = authenticationManager.authenticate(token);

                stringRedisTemplate.delete(LOGIN_FAIL_COUNT_KEY + phone);
            } catch (BadCredentialsException e) {
                String countKey = LOGIN_FAIL_COUNT_KEY + phone;
                Long count = stringRedisTemplate.opsForValue().increment(countKey, 1);
                if (count == 1) {
                    stringRedisTemplate.expire(countKey, FAIL_COUNT_TTL, TimeUnit.SECONDS);
                }
                log.warn("手机号{}密码登录失败，当前失败次数：{}", phone, count);

                if (count >= MAX_FAIL_COUNT) {
                    stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_SECONDS, TimeUnit.SECONDS);
                    throw new BusinessException("连续登录失败达到" + MAX_FAIL_COUNT + "次，账号临时锁定10分钟");
                }
                throw new BusinessException("手机号或密码错误，剩余尝试次数：" + (MAX_FAIL_COUNT - count));
            }

        } else if ("SMS_CODE".equals(dto.getLoginType())) {
            SmsCodeAuthenticationToken smsToken =
                    new SmsCodeAuthenticationToken(dto.getPhone(), dto.getSmsCode());
            authentication = authenticationManager.authenticate(smsToken);
        } else {
            throw new BusinessException("不支持的登录类型");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userService.getUserByPhone(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!userDetails.isEnabled()) {
            throw new BusinessException("该用户已被封禁");
        }

        userService.lambdaUpdate()
                .eq(User::getUserId, user.getUserId())
                .set(User::getLoginIp, dto.getIp())
                .set(User::getLoginTime, LocalDateTime.now())
                .update();

        String accessToken = jwtUtil.generateToken(user.getUserId(), user.getRole(), user.getUserVersion());
        RefreshToken rt = refreshTokenService.create(user.getUserId());

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(rt.getToken());
        return loginVO;
    }

    @Override
    public UserVO currentUser() {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return UserConvert.INSTANCE.toVO(loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(String refreshToken) {
        RefreshToken rt = refreshTokenService.getValidByToken(refreshToken);
        if (rt == null) {
            throw new BusinessException("refreshToken无效或已过期，请重新登录");
        }

        User user = userService.getById(rt.getUserId());
        if (user == null || !user.getStatus().equals(UserStatus.ENABLED)) {
            refreshTokenService.revoke(refreshToken);
            throw new BusinessException("用户不存在或已被禁用");
        }

        refreshTokenService.revoke(refreshToken);
        RefreshToken newRt = refreshTokenService.create(user.getUserId());
        String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getRole(), user.getUserVersion());

        LoginVO vo = new LoginVO();
        vo.setAccessToken(newAccessToken);
        vo.setRefreshToken(newRt.getToken());
        return vo;
    }

    @Override
    public void logout(String jti, Long remainSeconds, String refreshToken, Long userId) {
        if (remainSeconds > 0) {
            jwtBlacklistService.addToBlacklist(jti, remainSeconds);
        }
        if (refreshToken != null) {
            refreshTokenService.deleteCurrentToken(userId, refreshToken);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        String phone = dto.getPhone();

        captchaService.verifyImageCaptcha(dto.getImageCode(), dto.getImageUuid());

        String smsKey = SMS_CAPTCHA_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(smsKey);
        if (cacheCode == null || !cacheCode.equals(dto.getSmsCode())) {
            throw new BusinessException("短信验证码错误或已过期");
        }
        stringRedisTemplate.delete(smsKey);

        User user = userService.lambdaQuery()
                .eq(User::getPhone, phone)
                .one();
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }

        if (!isValidPassword(dto.getNewPassword())) {
            throw new BusinessException("密码至少8位，且需同时包含大写字母、小写字母和数字");
        }

        String encodePwd = passwordEncoder.encode(dto.getNewPassword());

        userService.lambdaUpdate()
                .eq(User::getUserId, user.getUserId())
                .set(User::getPassword, encodePwd)
                .set(User::getUserVersion, user.getUserVersion() + 1) // version+1，旧JWT失效
                .update();

        stringRedisTemplate.delete(LOGIN_FAIL_COUNT_KEY + phone);
        stringRedisTemplate.delete(LOGIN_LOCK_KEY + phone);
        refreshTokenService.revokeAllByUserId(user.getUserId());
    }

    public boolean isValidPassword(String pwd){
        if(StringUtils.isBlank(pwd)){
            return false;
        }
        String regex = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,}$";
        return ReUtil.isMatch(regex, pwd);
    }
}
