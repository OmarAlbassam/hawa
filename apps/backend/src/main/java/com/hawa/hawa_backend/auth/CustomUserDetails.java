package com.hawa.hawa_backend.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;

import lombok.Getter;

public class CustomUserDetails implements UserDetails {

    @Getter
    private final Long userId;
    @Getter
    private final Long companyId;
    @Getter
    private final UserRoleEnum role;
    private final String email;
    private final String password;

    public CustomUserDetails(User user) {
        this.userId = user.getUserId();
        this.companyId = user.getCompany() != null ? user.getCompany().getCompanyId() : null;
        this.role = user.getRole();
        this.email = user.getEmail();
        this.password = user.getPassword();
    }

    private CustomUserDetails(Long userId, Long companyId, UserRoleEnum role, String email) {
        this.userId = userId;
        this.companyId = companyId;
        this.role = role;
        this.email = email;
        this.password = null;
    }

    public static CustomUserDetails fromClaims(JwtService.JwtClaims claims) {
        return new CustomUserDetails(claims.userId(), claims.companyId(), claims.role(), claims.email());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
