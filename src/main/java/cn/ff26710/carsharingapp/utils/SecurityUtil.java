package cn.ff26710.carsharingapp.utils;

import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    public static User getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        return null;
    }
    public static Long getUserId() {
        User user = getLoginUser();
        if (user == null) {
            throw new BusinessException("未登录，请先登录");
        }
        return user.getUserId();
    }
}
