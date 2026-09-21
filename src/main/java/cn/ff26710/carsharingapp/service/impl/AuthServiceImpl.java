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

    private static final String REGISTER_SMS_CODE_KEY = "captcha:sms:register:";
    private static final String REGISTER_FAIL_COUNT_KEY = "register:fail:count:";
    private static final String REGISTER_LOCK_KEY = "register:lock:";

    private static final String LOGIN_FAIL_COUNT_KEY = "login:fail:count:";
    private static final String LOGIN_LOCK_KEY = "login:lock:";

    private static final String RESET_PASSWORD_SMS_CODE_KEY = "captcha:sms:reset-password:";
    private static final String RESET_PASSWORD_FAIL_COUNT_KEY = "reset-password:fail:count:";
    private static final String RESET_PASSWORD_LOCK_KEY = "reset-password:lock:";

    @Override
    public void register(RegisterDTO dto) {
        String phoneReg = "^1[3-9]\\d{9}$";
        String phone = dto.getPhone();
        if (!ReUtil.isMatch(phoneReg, phone)) {
            throw new BusinessException("手机号不正确");
        }
        if (!isValidPassword(dto.getPassword())) {
            throw new BusinessException("密码至少8位，且需同时包含大写字母、小写字母和数字");
        }
        if (!dto.getConfirmPassword().equals(dto.getPassword())) {
            throw new BusinessException("两次的密码不一致");
        }
        if (userService.existByPhone(phone)) {
            throw new BusinessException("该手机号已注册");
        }
        String lockKey = REGISTER_LOCK_KEY + phone;
        String countKey = REGISTER_FAIL_COUNT_KEY + phone;
        if (stringRedisTemplate.hasKey(lockKey)) {
            throw new BusinessException("注册失败次数过多，请10分钟后重试");
        }
        captchaService.verifyImageCaptcha(dto.getImageCode(), dto.getImageUuid());
        String smsKey = REGISTER_SMS_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(smsKey);
        if (cacheCode == null) {
            throw new BusinessException("短信验证码失效，请重新获取验证码");
        }
        if (!cacheCode.equals(dto.getSmsCode())) {
            Long count = stringRedisTemplate.opsForValue().increment(countKey, 1);
            if (count == 1) {
                stringRedisTemplate.expire(countKey, FAIL_COUNT_TTL, TimeUnit.SECONDS);
            }
            log.warn("手机号{}注册失败，当前失败次数：{}", phone, count);

            if (count >= MAX_FAIL_COUNT) {
                stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_SECONDS, TimeUnit.SECONDS);
                throw new BusinessException("连续输入错误验证码失败达到" + MAX_FAIL_COUNT + "次，注册操作临时锁定10分钟");
            }
            throw new BusinessException("短信验证码错误");
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
        stringRedisTemplate.delete(smsKey);
        stringRedisTemplate.delete(lockKey);
        stringRedisTemplate.delete(countKey);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        Authentication authentication;
        String phone = dto.getPhone();
        String lockKey = LOGIN_LOCK_KEY + phone;
        if (stringRedisTemplate.hasKey(lockKey)) {
            throw new BusinessException("登录失败次数过多，请10分钟后重试");
        }
        if ("PASSWORD".equals(dto.getLoginType())) {
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
                    throw new BusinessException("连续登录失败达到" + MAX_FAIL_COUNT + "次，登录操作临时锁定10分钟");
                }
                throw new BusinessException("手机号或密码错误，剩余尝试次数：" + (MAX_FAIL_COUNT - count));
            }

        } else if ("SMS_CODE".equals(dto.getLoginType())) {
            try {
                SmsCodeAuthenticationToken smsToken =
                        new SmsCodeAuthenticationToken(dto.getPhone(), dto.getSmsCode());
                authentication = authenticationManager.authenticate(smsToken);

                stringRedisTemplate.delete(LOGIN_FAIL_COUNT_KEY + phone);
            } catch (BadCredentialsException e) {
                String countKey = LOGIN_FAIL_COUNT_KEY + phone;
                Long count = stringRedisTemplate.opsForValue().increment(countKey, 1);
                if (count == 1) {
                    stringRedisTemplate.expire(countKey, FAIL_COUNT_TTL, TimeUnit.SECONDS);
                }
                log.warn("手机号{}验证码登录失败，当前失败次数：{}", phone, count);

                if (count >= MAX_FAIL_COUNT) {
                    stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_SECONDS, TimeUnit.SECONDS);
                    throw new BusinessException("连续登录失败达到" + MAX_FAIL_COUNT + "次，账号临时锁定10分钟");
                }
                throw new BusinessException("手机号或验证码错误，剩余尝试次数：" + (MAX_FAIL_COUNT - count));
            }
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
        refreshTokenService.revokeAllByUserId(user.getUserId());
        String refreshToken = refreshTokenService.create(user.getUserId());

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
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
    public LoginVO refresh(String refreshToken) {
        RefreshToken rt = refreshTokenService.rotate(refreshToken);

        User user = userService.getById(rt.getUserId());
        if (user == null || !user.getStatus().equals(UserStatus.ENABLED)) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        String newRefreshToken = refreshTokenService.create(user.getUserId());
        String newAccessToken = jwtUtil.generateToken(user.getUserId(), user.getRole(), user.getUserVersion());

        LoginVO vo = new LoginVO();
        vo.setAccessToken(newAccessToken);
        vo.setRefreshToken(newRefreshToken);
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
        User user = userService.lambdaQuery()
                .eq(User::getPhone, phone)
                .one();

        captchaService.verifyImageCaptcha(dto.getImageCode(), dto.getImageUuid());

        String smsKey = RESET_PASSWORD_SMS_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(smsKey);
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }

        if (!isValidPassword(dto.getNewPassword())) {
            throw new BusinessException("密码至少8位，且需同时包含大写字母、小写字母和数字");
        }
        if (cacheCode == null) {
            throw new BusinessException("短信验证码失效，请重新获取验证码");
        }

        String lockKey = RESET_PASSWORD_LOCK_KEY + phone;
        String countKey = RESET_PASSWORD_FAIL_COUNT_KEY + phone;
        if (!cacheCode.equals(dto.getSmsCode())) {
            Long count = stringRedisTemplate.opsForValue().increment(countKey, 1);
            if (count == 1) {
                stringRedisTemplate.expire(countKey, FAIL_COUNT_TTL, TimeUnit.SECONDS);
            }
            log.warn("手机号{}重置密码失败，当前失败次数：{}", phone, count);

            if (count >= MAX_FAIL_COUNT) {
                stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_SECONDS, TimeUnit.SECONDS);
                throw new BusinessException("连续输入错误验证码失败达到" + MAX_FAIL_COUNT + "次，重置操作临时锁定10分钟");
            }
            throw new BusinessException("短信验证码错误");
        }

        String encodePwd = passwordEncoder.encode(dto.getNewPassword());

        userService.lambdaUpdate()
                .eq(User::getUserId, user.getUserId())
                .set(User::getPassword, encodePwd)
                .set(User::getUserVersion, user.getUserVersion() + 1) // version+1，旧JWT失效
                .update();

        stringRedisTemplate.delete(smsKey);
        stringRedisTemplate.delete(lockKey);
        stringRedisTemplate.delete(countKey);
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
