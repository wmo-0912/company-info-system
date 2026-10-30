package com.example.company.config;

import com.example.company.common.security.LoginUser;
import com.example.company.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = tokenProvider.parse(token);
            if (claims != null) {
                try {
                    Long userId = Long.valueOf(claims.getSubject());
                    LoginUser loginUser = userService.loadLoginUser(userId);
                    if (loginUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        var authorities = loginUser.getPermissions().stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();
                        var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // 服务端强制执行"首登必改密"：只放行个人中心与认证接口，
                        // 否则前端路由守卫一旦被绕过（直接调 API），初始密码可长期不换。
                        if (loginUser.isMustChangePassword() && !isPasswordChangeFlow(request)) {
                            writePasswordChangeRequired(response);
                            return;
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // subject 非数字，忽略，按未认证处理
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isPasswordChangeFlow(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        // 个人中心（含 PUT /profile/password）与 /auth/me 属于改密流程内允许的接口
        return uri.startsWith("/api/v1/profile") || uri.startsWith("/api/v1/auth");
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"code":"FORBIDDEN","message":"请先修改初始密码后再使用系统","data":null,"timestamp":"%s"}
                """.formatted(OffsetDateTime.now()));
    }
}
