package com.planit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 검증 Provider (Schedule-svc 전용)
 * 토큰 발급은 User-svc에서만 수행. 이 서비스는 검증만 담당.
 * User-svc와 동일한 JWT_SECRET 사용 필수.
 */
@Component
public class JwtProvider {

    private final SecretKey secretKey;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
        return true;
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
