package com.hawa.hawa_backend.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.hawa.hawa_backend.config.JwtProperties;
import com.hawa.hawa_backend.exception.InvalidTokenException;
import com.hawa.hawa_backend.user.User;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final TransactionTemplate transactionTemplate;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtProperties jwtProperties,
                               PlatformTransactionManager transactionManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshExpirationMs() / 1000))
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Delete in a separate transaction so the cleanup commits
            // even though we throw an exception afterward
            transactionTemplate.executeWithoutResult(status ->
                    refreshTokenRepository.deleteByToken(token));
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        return refreshToken;
    }

    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {
        User user = oldToken.getUser();
        refreshTokenRepository.delete(oldToken);
        return createRefreshToken(user);
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
