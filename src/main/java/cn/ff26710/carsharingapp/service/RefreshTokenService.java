package cn.ff26710.carsharingapp.service;


import cn.ff26710.carsharingapp.entity.RefreshToken;

public interface RefreshTokenService {
    RefreshToken create(Long userId);
    RefreshToken getValidByToken(String refreshToken);
    void revoke(String refreshToken);
    void revokeAllByUserId(Long userId);
    String getTokenByUserId(Long userId);
    void deleteAllRevokedToken();
    void deleteCurrentToken(Long currentUserId, String refreshToken);
}
