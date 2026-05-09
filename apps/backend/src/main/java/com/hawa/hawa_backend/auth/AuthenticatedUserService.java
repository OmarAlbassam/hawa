package com.hawa.hawa_backend.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public CustomUserDetails getPrincipal() {
        return (CustomUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public Long getUserId() {
        return getPrincipal().getUserId();
    }

    public User getAuthenticatedUser() {
        return userRepository.getReferenceById(getUserId());
    }

    public Long getCompanyId() {
        Long companyId = getPrincipal().getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException(
                    "Authenticated user " + getUserId()
                            + " has no company; tenant-scoped operations are not available for platform-level users (e.g. ADMIN).");
        }
        return companyId;
    }
}
