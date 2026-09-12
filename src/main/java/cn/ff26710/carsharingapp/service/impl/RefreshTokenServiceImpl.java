package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.RefreshToken;
import cn.ff26710.carsharingapp.entity.enums.TokenRevoked;
import cn.ff26710.carsharingapp.mapper.RefreshTokenMapper;
import cn.ff26710.carsharingapp.service.RefreshTokenService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenMapper refreshTokenMapper;
    private static final long REFRESH_DAYS = 7;

    @Override
    public RefreshToken create(Long userId) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(UUID.randomUUID().toString());
        rt.setExpireAt(LocalDateTime.now().plusDays(REFRESH_DAYS));
        rt.setRevoked(TokenRevoked.VALID);
        rt.setCreateTime(LocalDateTime.now());
        refreshTokenMapper.insert(rt);
        return rt;
    }

    @Override
    public RefreshToken getValidByToken(String refreshToken) {
        return refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, refreshToken)
                        .eq(RefreshToken::getRevoked, TokenRevoked.VALID)
                        .gt(RefreshToken::getExpireAt, LocalDateTime.now())
        );
    }

    @Override
    public void revoke(String refreshToken) {
        refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getToken, refreshToken)
                        .set(RefreshToken::getRevoked, TokenRevoked.REVOKED)
        );
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .set(RefreshToken::getRevoked, TokenRevoked.REVOKED)
        );
    }

    @Override
    public String getTokenByUserId(Long userId) {
        RefreshToken refreshToken = refreshTokenMapper.selectOne(
                Wrappers.lambdaQuery(RefreshToken.class)
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getRevoked, TokenRevoked.VALID)
                        .gt(RefreshToken::getExpireAt, LocalDateTime.now())
                        .orderByDesc(RefreshToken::getExpireAt)
                        .last("LIMIT 1")
        );
        if(refreshToken == null){
            return null;
        }
        return refreshToken.getToken();
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
        RefreshToken tokenEntity = refreshTokenMapper.selectOne(
                Wrappers.lambdaQuery(RefreshToken.class)
                        .eq(RefreshToken::getToken, refreshToken)
        );
        // token 不存在（已过期被清理 / 客户端传错）时直接当作已退出，保证登出幂等，
        // 否则这里会 NPE，客户端拿到 500 而不是正常的登出成功
        if (tokenEntity == null) {
            return;
        }
        if (!tokenEntity.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("无权操作该凭证");
        }
        tokenEntity.setRevoked(TokenRevoked.REVOKED);
        refreshTokenMapper.updateById(tokenEntity);
    }
}
