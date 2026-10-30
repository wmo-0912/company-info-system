package com.example.company.config;

import com.example.company.common.security.LoginUser;
import com.example.company.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * 会话版本回归测试：改密/重置后服务端不保存 token 黑名单，
 * 而是比对 token 里的 ver 声明与 sys_user.token_version，不一致即视为未认证。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserService userService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Claims claims(Integer version) {
        var builder = Jwts.claims().subject("7");
        if (version != null) {
            builder.add(JwtTokenProvider.CLAIM_TOKEN_VERSION, version);
        }
        return builder.build();
    }

    private LoginUser loginUser(Integer version) {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        user.setUsername("zhangsan");
        user.setTokenVersion(version);
        user.setPermissions(Set.of("employee:read"));
        user.setRoles(Set.of("HR"));
        return user;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        request.addHeader("Authorization", "Bearer signed-token");
        return request;
    }

    @Test
    @DisplayName("会话版本一致时正常认证")
    void authenticatesWhenVersionMatches() throws Exception {
        when(tokenProvider.parse("signed-token")).thenReturn(claims(3));
        when(userService.loadLoginUser(7L)).thenReturn(loginUser(3));

        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("改密后旧 token（版本落后）不再被认证，请求继续走匿名流程")
    void rejectsStaleTokenAfterPasswordChange() throws Exception {
        when(tokenProvider.parse("signed-token")).thenReturn(claims(1));
        when(userService.loadLoginUser(7L)).thenReturn(loginUser(2));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "旧 token 必须失效");
        assertNotNull(chain.getRequest(), "过滤器不应中断请求，交给鉴权链返回 401");
    }

    @Test
    @DisplayName("升级前签发的 token 没有 ver 声明，按版本 0 兼容")
    void treatsMissingVersionAsZero() throws Exception {
        when(tokenProvider.parse("signed-token")).thenReturn(claims(null));
        when(userService.loadLoginUser(7L)).thenReturn(loginUser(0));

        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("旧 token 缺少 ver 而账号版本已提升时同样拒绝")
    void rejectsLegacyTokenAfterBump() throws Exception {
        when(tokenProvider.parse("signed-token")).thenReturn(claims(null));
        when(userService.loadLoginUser(7L)).thenReturn(loginUser(1));

        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("未携带 Authorization 头的请求直接放行")
    void passesThroughWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("/api/v1/health", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }
}
