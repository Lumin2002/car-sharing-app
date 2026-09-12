package cn.ff26710.carsharingapp.security;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class SmsCodeAuthenticationToken implements Authentication {
    @Getter
    private final String phone;
    private String smsCode;
    private boolean authenticated;
    private Object principal;
    private Collection<? extends GrantedAuthority> authorities;

    public SmsCodeAuthenticationToken(String phone, String smsCode) {
        this.phone = phone;
        this.smsCode = smsCode;
        this.authenticated = false;
    }

    public SmsCodeAuthenticationToken(Object principal, Collection<? extends GrantedAuthority> authorities) {
        this.principal = principal;
        this.authorities = authorities;
        this.authenticated = true;
        this.phone = null;
        this.smsCode = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return smsCode;
    }

    @Override
    public Object getDetails() {
        return phone;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return "sms";
    }
}
