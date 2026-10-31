package com.example.company.config;

import com.example.company.common.controller.HealthController;
import com.example.company.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 显式开启文档（开发 profile）时，文档路径不再被安全链拦截。
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ApiDocsEnabledWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserService userService;

    @Test
    void apiDocsReachableWhenEnabled() throws Exception {
        // 本切片未加载 springdoc 控制器，放行后应落到 404 而不是 401
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
