package com.example.medilingo.security.filter;

import com.example.medilingo.ex.ErrorCode;
import com.example.medilingo.ex.ErrorRespDto;
import com.example.medilingo.security.service.UserDetailsServiceImpl;
import com.example.medilingo.security.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j(topic = "로그인 후 토큰 검증 인가")

public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final AntPathMatcher matcher = new AntPathMatcher();

    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/api/auth/**" // signup, login
    );

    // ✅ 공개 경로 & 프리플라이트는 아예 필터 적용 안 함
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        for (String p : PUBLIC_PATTERNS) {
            if (matcher.match(p, uri)) return true;
        }
        return false;
    }


    public JwtAuthorizationFilter(final JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String auth = request.getHeader("Authorization");
        log.info("🔍 [JWT 인가필터] {} {} | Authorization={}", request.getMethod(), request.getRequestURI(), auth);

        // ✅ 헤더가 없거나 Bearer 접두어가 아니면 인증 시도 없이 그대로 통과
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String jwt = jwtUtil.substringToken(auth); // "Bearer " 제거
        try {
            // ✅ 토큰 유효성 검사 실패 시에도 '차단하지 않고' 통과
            if (!jwtUtil.verifyAccessToken(jwt)) {
                log.warn("JWT 검증 실패(verifyAccessToken=false) → 인증 미설정으로 통과");
                chain.doFilter(request, response);
                return;
            }

            Claims claims = jwtUtil.parseToken(jwt);
            if (claims == null) {
                log.warn("JWT claims null → 인증 미설정으로 통과");
                chain.doFilter(request, response);
                return;
            }

            String email = (String) claims.get("email");
            if (email == null || email.isBlank()) {
                log.warn("JWT에 email 클레임 없음 → 인증 미설정으로 통과");
                chain.doFilter(request, response);
                return;
            }

            // ✅ 유효하면 인증 컨텍스트 세팅
            setAuthentication(email);
            chain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT → 인증 미설정으로 통과", e);
            chain.doFilter(request, response);
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("유효하지 않은 JWT 서명/형식 → 인증 미설정으로 통과", e);
            chain.doFilter(request, response);
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT → 인증 미설정으로 통과", e);
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.error("JWT 처리 중 예외 → 인증 미설정으로 통과", e);
            chain.doFilter(request, response);
        }
    }

    // ✅ 인증 처리 (SecurityContext에 유저 저장)
    private void setAuthentication(String email) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        log.info("✅ 인증 컨텍스트 세팅 완료: {}", email);
    }

    // (선택) 필요하면 커스텀 에러 바디를 내려야 하는 경우에만 사용
    @SuppressWarnings("unused")
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode, HttpServletRequest request) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorCode.getStatus());
        String json = objectMapper.writeValueAsString(new ErrorRespDto(errorCode, request.getRequestURI()));
        response.getWriter().write(json);
    }
}
