package cn.ff26710.carsharingapp.security;

import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.service.UserService;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SmsCodeAuthenticationProvider implements AuthenticationProvider {
    private final StringRedisTemplate stringRedisTemplate;
    private final UserDetailsService userDetailsService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        SmsCodeAuthenticationToken token = (SmsCodeAuthenticationToken) authentication;
        String phone = token.getPhone();
        String smsCode = (String) token.getCredentials();

        String redisKey = "captcha:sms:" + phone;
        String realCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (realCode == null || !realCode.equals(smsCode)) {
            throw new BadCredentialsException("验证码错误或已过期");
        }
        stringRedisTemplate.delete(redisKey);

        Optional<User> userOpt = userService.getUserByPhone(phone);
        User user;
        if (userOpt.isEmpty()) {
            User newUser = new User();
            newUser.setUsername(phone);
            newUser.setPassword(passwordEncoder.encode(RandomUtil.randomString(10)));
            newUser.setPhone(phone);
            newUser.setStatus(UserStatus.ENABLED);
            newUser.setRole(UserRole.USER);
            newUser.setUserVersion(1);
            userService.save(newUser);
            user = newUser;
        } else {
            user = userOpt.get();
        }

        // 校验账号是否启用
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BadCredentialsException("账号已被封禁");
        }

        // 加载UserDetails
        UserDetails userDetails = userDetailsService.loadUserByUsername(phone);
        // 构建已认证的Token
        SmsCodeAuthenticationToken authenticatedToken =
                new SmsCodeAuthenticationToken(userDetails, userDetails.getAuthorities());
        return authenticatedToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return SmsCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
