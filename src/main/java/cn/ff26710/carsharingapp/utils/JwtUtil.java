package cn.ff26710.carsharingapp.utils;

import cn.ff26710.carsharingapp.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expire-hours:2}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, UserRole role, Integer userVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .setId(jti)
                .setSubject(userId.toString())
                .claim("role", role.getCode())
                .claim("userVersion", userVersion)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) throws JwtException, IllegalArgumentException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public Long getUserId(Claims claims){
        return Long.valueOf(claims.getSubject());
    }
    public String getRole(Claims claims){
        return claims.get("role", String.class);
    }
    public Integer getUserVersion(Claims claims){
        return claims.get("userVersion", Integer.class);
    }
    public String getJti(Claims claims){
        return claims.getId();
    }
    public Long getExpireHours() {
        return expireHours;
    }
}
