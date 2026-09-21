package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.RefreshToken;
import cn.ff26710.carsharingapp.entity.enums.TokenRevoked;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RefreshTokenMapper;
import cn.ff26710.carsharingapp.service.RefreshTokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final long REFRESH_DAYS = 7;
    private static final int RAW_TOKEN_BYTES = 48;

    private final RefreshTokenMapper refreshTokenMapper;
    private final SecureRandom secureRandom;

    @Override
    public String create(Long userId) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(tokenHash);
        rt.setExpireAt(LocalDateTime.now().plusDays(REFRESH_DAYS));
        rt.setRevoked(TokenRevoked.VALID);
        rt.setCreateTime(LocalDateTime.now());
        refreshTokenMapper.insert(rt);
        return rawToken;
    }

    @Override
    public RefreshToken rotate(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        LocalDateTime now = LocalDateTime.now();

        int updated = refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, tokenHash)
                        .eq(RefreshToken::getRevoked, TokenRevoked.VALID)
                        .gt(RefreshToken::getExpireAt, now)
                        .set(RefreshToken::getRevoked, TokenRevoked.REVOKED));
        if (updated != 1) {
            throw new BusinessException("refreshToken无效或已过期，请重新登录");
        }

        RefreshToken revokedToken = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, tokenHash));
        if (revokedToken == null) {
            throw new BusinessException("refreshToken无效或已过期，请重新登录");
        }
        return revokedToken;
    }

    @Override
    public void revoke(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, tokenHash)
                        .eq(RefreshToken::getRevoked, TokenRevoked.VALID)
                        .set(RefreshToken::getRevoked, TokenRevoked.REVOKED));
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .set(RefreshToken::getRevoked, TokenRevoked.REVOKED));
    }

    @Override
    public void deleteAllRevokedToken() {
        int limit = 100;
        while (true) {
            int count = refreshTokenMapper.delete(Wrappers.lambdaQuery(RefreshToken.class)
                    .eq(RefreshToken::getRevoked, TokenRevoked.REVOKED)
                    .or().lt(RefreshToken::getExpireAt, LocalDateTime.now())
                    .last("LIMIT " + limit));
            if (count == 0) {
                break;
            }
        }
    }

    @Override
    public void deleteCurrentToken(Long currentUserId, String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshToken tokenEntity = refreshTokenMapper.selectOne(
                Wrappers.lambdaQuery(RefreshToken.class)
                        .eq(RefreshToken::getToken, tokenHash));
        if (tokenEntity == null) {
            return;
        }
        if (!tokenEntity.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("无权操作该凭证");
        }
        tokenEntity.setRevoked(TokenRevoked.REVOKED);
        refreshTokenMapper.updateById(tokenEntity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
