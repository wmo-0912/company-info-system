package com.example.company.system;

import com.example.company.config.JwtAuthenticationFilter;
import com.example.company.config.JwtTokenProvider;
import com.example.company.config.SecurityConfig;
import com.example.company.system.controller.PublicCompanyController;
import com.example.company.system.entity.SysConfig;
import com.example.company.system.mapper.SysConfigMapper;
import com.example.company.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开公司信息接口：登录页需要在无 token 的情况下拿到名称与 Logo。
 */
@WebMvcTest(controllers = PublicCompanyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PublicCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SysConfigMapper configMapper;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserService userService;

    private SysConfig config(String key, String value) {
        SysConfig c = new SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    @Test
    @DisplayName("未登录即可访问（permitAll），未配置时返回默认名称")
    void companyInfoIsPublicAndFallsBackToDefaultName() throws Exception {
        when(configMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/public/company-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("公司信息管理系统"))
                .andExpect(jsonPath("$.data.logoUrl").isEmpty());
    }

    @Test
    @DisplayName("返回系统配置中的公司名称与 Logo，空白 Logo 归一为 null")
    void companyInfoReadsConfiguredValues() throws Exception {
        when(configMapper.selectOne(any()))
                .thenReturn(config("company.name", " 示例科技有限公司 "))
                .thenReturn(config("company.logo.url", "   "));

        mockMvc.perform(get("/api/v1/public/company-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("示例科技有限公司"))
                .andExpect(jsonPath("$.data.logoUrl").isEmpty());
    }

    @Test
    @DisplayName("配置了 Logo 地址时原样返回")
    void companyInfoReturnsLogoUrl() throws Exception {
        when(configMapper.selectOne(any()))
                .thenReturn(config("company.name", "示例科技有限公司"))
                .thenReturn(config("company.logo.url", "https://cdn.example.com/logo.png"));

        mockMvc.perform(get("/api/v1/public/company-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/logo.png"));
    }
}
