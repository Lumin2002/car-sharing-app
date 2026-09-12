package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String PREFIX = "jwt:blacklist:";

    public void addToBlacklist(String jti, long remainSeconds) {
        try {
            if (remainSeconds > 0) {
                String key = PREFIX + jti;
                stringRedisTemplate.opsForValue().set(key, "1", remainSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new BusinessException("当前Redis失效，请联系管理员");
        }
    }

    public boolean isInBlacklist(String jti) {
        String key = PREFIX + jti;
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            return false;
        }
    }
}
