package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.dto.auth.LoginDTO;
import cn.ff26710.carsharingapp.dto.auth.RegisterDTO;
import cn.ff26710.carsharingapp.dto.auth.ResetPasswordDTO;
import cn.ff26710.carsharingapp.dto.auth.SendSmsCodeDTO;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.AuthService;
import cn.ff26710.carsharingapp.service.CaptchaService;
import cn.ff26710.carsharingapp.service.RefreshTokenService;
import cn.ff26710.carsharingapp.utils.IpUtil;
import cn.ff26710.carsharingapp.vo.LoginVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import cn.ff26710.carsharingapp.vo.user.UserVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CaptchaService captchaService;

    @GetMapping("/imageCaptcha")
    public void getImageCaptcha(@RequestParam String uuid, HttpServletResponse response) throws IOException {
        byte[] imageBytes = captchaService.getImageCaptcha(uuid);

        response.setContentType("image/png");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expire", 0);
        response.getOutputStream().write(imageBytes);
    }

    @PostMapping("/sendSmsCode")
    public ResultVO<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeDTO dto) {
        captchaService.sendSmsCode(dto.getPhone(), dto.getImageCode(), dto.getImageUuid());
        return ResultVO.success();
    }

    @PostMapping("/register")
    public ResultVO<Void> register(@Valid @RequestBody RegisterDTO dto){
        authService.register(dto);
        return ResultVO.success();
    }
    @PostMapping("/login")
    public ResultVO<LoginVO> login(HttpServletRequest request, @Valid @RequestBody LoginDTO dto){
        dto.setIp(IpUtil.getClientIp(request));
        return ResultVO.success(authService.login(dto));
    }
    @GetMapping("/current")
    public ResultVO<UserVO> current() {
        return ResultVO.success(authService.currentUser());
    }
    @PostMapping("/refresh")
    public ResultVO<LoginVO> refresh(HttpServletRequest request) {
        String refreshToken = request.getHeader("Refresh-Token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(401,"刷新令牌不存在");
        }
        return ResultVO.success(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResultVO<Void> logout(@RequestParam String refreshToken) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return ResultVO.success(null);
        }

        User loginUser = (User) authentication.getPrincipal();
        Claims claims = (Claims) authentication.getDetails();
        long remainSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;

        String jti = claims.getId();
        authService.logout(jti, remainSeconds, refreshToken, loginUser.getUserId());
        return ResultVO.success(null);
    }

    @PostMapping("/reset/password")
    public ResultVO<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return ResultVO.success();
    }
}
