package cn.ff26710.carsharingapp.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {
    @Value("${jwt.secret}")
    private final String key;
    @Value("${jwt.expire-hours:2}")
    private final Long expireHours;
    private SecretKey signingKey;
    @PostConstruct
    public void init() {
        // 全部安全校验写在这里
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET 不能为空，请配置环境变量");
        }
        if (key.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET长度至少32位");
        }
        signingKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }
    @Bean
    public SecretKey jwtSecretKey() {
        return signingKey;
    }

    @Bean
    public Long jwtExpireHours() {
        return expireHours;
    }
}
