package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.auth.LoginDTO;
import cn.ff26710.carsharingapp.dto.auth.RegisterDTO;
import cn.ff26710.carsharingapp.dto.auth.ResetPasswordDTO;
import cn.ff26710.carsharingapp.vo.LoginVO;
import cn.ff26710.carsharingapp.vo.user.UserVO;

public interface AuthService {
    void register(RegisterDTO dto);
    LoginVO login(LoginDTO dto);
    UserVO currentUser();
    LoginVO refresh(String refreshToken);
    void logout(String jti,Long remainSeconds, String refreshToken, Long userId);
    void resetPassword(ResetPasswordDTO dto);
}
