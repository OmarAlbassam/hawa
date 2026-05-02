package com.hawa.hawa_backend.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hawa.hawa_backend.user.User;

@Service
public class AuthenticatedUserService {

    public User getAuthenticatedUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return userDetails.getUser();
    }

    public Long getCompanyId() {
        User user = getAuthenticatedUser();
        if (user.getCompany() == null) {
            throw new IllegalStateException(
                    "Authenticated user " + user.getUserId()
                            + " has no company; tenant-scoped operations are not available for platform-level users (e.g. ADMIN).");
        }
        return user.getCompany().getCompanyId();
    }
}
