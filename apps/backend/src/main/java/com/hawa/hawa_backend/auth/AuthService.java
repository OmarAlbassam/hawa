package com.hawa.hawa_backend.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.dto.AuthResponse;
import com.hawa.hawa_backend.auth.dto.LoginRequest;
import com.hawa.hawa_backend.auth.dto.RefreshRequest;
import com.hawa.hawa_backend.auth.dto.RegisterRequest;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.exception.DuplicateEmailException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        refreshTokenService.deleteByUser(user);
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse.UserInfo register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered");
        }

        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + request.companyId()));

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .company(company)
                .role(request.role())
                .build();

        user = userRepository.save(user);
        log.info("Admin registered new user: {}", user.getEmail());
        return toUserInfo(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(request.refreshToken());
        User user = oldToken.getUser();
        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken);

        String accessToken = jwtService.generateAccessToken(user);
        log.info("Token refreshed for user: {}", user.getEmail());

        return new AuthResponse(
                accessToken,
                newToken.getToken(),
                toUserInfo(user));
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.deleteByToken(request.refreshToken());
        log.info("User logged out");
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                toUserInfo(user));
    }

    private AuthResponse.UserInfo toUserInfo(User user) {
        return new AuthResponse.UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName());
    }
}
