package cn.ff26710.carsharingapp.filter;

import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.UserStatus;
import cn.ff26710.carsharingapp.service.JwtBlacklistService;
import cn.ff26710.carsharingapp.service.UserService;
import cn.ff26710.carsharingapp.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            filterChain.doFilter(request, response);
            return;
        }

        String jti = jwtUtil.getJti(claims);
        Long userId = jwtUtil.getUserId(claims);
        Integer tokenUserVersion = jwtUtil.getUserVersion(claims);

        if (jwtBlacklistService.isInBlacklist(jti)) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userService.getById(userId);
        if (user == null || !user.getUserVersion().equals(tokenUserVersion)
                || UserStatus.DISABLED == user.getStatus()) {
            filterChain.doFilter(request, response);
            return;
        }

        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, Collections.singletonList(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        authentication.setDetails(claims);
        filterChain.doFilter(request, response);
    }
}
