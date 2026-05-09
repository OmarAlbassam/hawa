package com.hawa.hawa_backend.auth;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.hawa.hawa_backend.config.JwtProperties;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirationMs;

    public JwtService(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.accessExpirationMs = jwtProperties.accessExpirationMs();
    }

    public record JwtClaims(Long userId, String email, Long companyId, UserRoleEnum role) {}

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);

        Long companyId = user.getCompany() != null ? user.getCompany().getCompanyId() : null;
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getUserId())
                .claim("companyId", companyId)
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public JwtClaims parseClaims(String token) {
        Claims claims = extractClaims(token);
        Number userIdNum = claims.get("userId", Number.class);
        Number companyIdNum = claims.get("companyId", Number.class);
        String roleStr = claims.get("role", String.class);
        return new JwtClaims(
                userIdNum == null ? null : userIdNum.longValue(),
                claims.getSubject(),
                companyIdNum == null ? null : companyIdNum.longValue(),
                roleStr == null ? null : UserRoleEnum.valueOf(roleStr));
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
