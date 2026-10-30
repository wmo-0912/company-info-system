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
                    }
                } catch (NumberFormatException ignored) {
                    // subject 非数字，忽略，按未认证处理
                }
            }
        }
        chain.doFilter(request, response);
    }
}
