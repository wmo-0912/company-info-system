package com.example.company.employee;

import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.employee.service.EmployeeService;
import com.example.company.employment.service.EmploymentService;
import com.example.company.config.JwtAuthenticationFilter;
import com.example.company.config.JwtTokenProvider;
import com.example.company.config.SecurityConfig;
import com.example.company.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全边界测试：未认证返回 401；无 employee:read 权限返回 403；
 * 数据范围外的员工详情返回 403。
 */
@WebMvcTest(controllers = com.example.company.employee.controller.EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, com.example.company.common.exception.GlobalExceptionHandler.class})
class EmployeeSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private EmploymentService employmentService;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private DataScopeService dataScopeService;
    @MockitoBean
    private com.example.company.department.mapper.DepartmentMapper departmentMapper;

    private LoginUser userWith(Set<String> permissions, String dataScope, Long employeeId) {
        LoginUser user = new LoginUser();
        user.setUserId(2L);
        user.setUsername("zhangsan");
        user.setEmployeeId(employeeId);
        user.setRoles(Set.of("EMPLOYEE"));
        user.setPermissions(permissions);
        user.setDataScope(dataScope);
        return user;
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void employeeWithoutReadPermissionGets403() throws Exception {
        LoginUser user = userWith(Set.of("profile:read"), "SELF", 10L);
        String token = "valid-token";
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("2");
        when(tokenProvider.parse(token)).thenReturn(claims);
        when(userService.loadLoginUser(2L)).thenReturn(user);

        mockMvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotReadOtherEmployeeDetail() throws Exception {
        LoginUser user = userWith(Set.of("employee:read"), "SELF", 10L);
        String token = "valid-token";
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("2");
        when(tokenProvider.parse(token)).thenReturn(claims);
        when(userService.loadLoginUser(2L)).thenReturn(user);
        when(dataScopeService.canAccessEmployee(user, 99L)).thenReturn(false);
        when(employeeService.detail(anyLong()))
                .thenThrow(com.example.company.common.exception.BusinessException.forbidden("无权查看该员工档案"));

        mockMvc.perform(get("/api/v1/employees/99").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
