package com.planit.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 인증 필터 (Schedule-svc)
 * Authorization: Bearer 헤더에서 JWT를 추출해 검증하고 SecurityContext에 userId를 저장.
 * MDC에 userId 저장 (구조화된 로깅)
 * 컨트롤러에서 @RequestHeader("X-User-Id") 대신 SecurityContext에서 userId를 꺼낼 수 있음.
 * 하위 호환: 토큰 없이 X-User-Id 헤더만 있는 경우 폴백 허용 (개발 편의).
 * 
 * @since 2026-03-20 (MDC userId 추가)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                if (jwtProvider.validateToken(token)) {
                    String userId = jwtProvider.getUserIdFromToken(token);
                    setAuthentication(request, userId);
                    // MDC에 userId 저장 (구조화된 로깅)
                    MDC.put(USER_ID_KEY, userId);
                }
            } catch (ExpiredJwtException e) {
                log.info("JWT token expired from IP: {}", getClientIp(request));
            } catch (SignatureException | MalformedJwtException e) {
                log.warn("JWT token invalid or signature mismatch from IP: {}", getClientIp(request));
            } catch (Exception e) {
                log.warn("JWT validation failed from IP: {}", getClientIp(request));
            }
        } else {
            // 폴백: JWT 없을 때 X-User-Id 헤더 사용 (내부 서비스 간 gRPC 호출 등)
            String userIdHeader = request.getHeader("X-User-Id");
            if (StringUtils.hasText(userIdHeader)) {
                setAuthentication(request, userIdHeader);
                MDC.put(USER_ID_KEY, userIdHeader);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, String userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
