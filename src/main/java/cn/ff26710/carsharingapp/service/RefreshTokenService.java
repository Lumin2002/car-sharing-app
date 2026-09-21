package cn.ff26710.carsharingapp.service;


import cn.ff26710.carsharingapp.entity.RefreshToken;

public interface RefreshTokenService {
    String create(Long userId);
    RefreshToken rotate(String refreshToken);
    void revoke(String refreshToken);
    void revokeAllByUserId(Long userId);
    void deleteAllRevokedToken();
    void deleteCurrentToken(Long currentUserId, String refreshToken);
}
