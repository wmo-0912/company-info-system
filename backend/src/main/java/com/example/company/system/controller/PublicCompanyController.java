package com.example.company.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.api.ApiResponse;
import com.example.company.system.entity.SysConfig;
import com.example.company.system.mapper.SysConfigMapper;
import com.example.company.system.vo.CompanyInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开信息接口：登录页在拿到 token 之前就要展示公司名称与 Logo，
 * 因此这里只暴露 sys_config 中 company.* 两个白名单键，且不返回任何其他配置。
 */
@Tag(name = "公开信息")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicCompanyController {

    /** 系统配置中公司名称的键 */
    public static final String KEY_COMPANY_NAME = "company.name";

    /** 系统配置中公司 Logo 地址的键 */
    public static final String KEY_COMPANY_LOGO_URL = "company.logo.url";

    /** 未配置公司名称时的兜底文案，与前端初始标题保持一致 */
    public static final String DEFAULT_COMPANY_NAME = "公司信息管理系统";

    private final SysConfigMapper configMapper;

    @Operation(summary = "公司名称与 Logo（无需鉴权，供登录页使用）")
    @GetMapping("/company-info")
    public ApiResponse<CompanyInfoVO> companyInfo() {
        String name = configValue(KEY_COMPANY_NAME);
        String logoUrl = configValue(KEY_COMPANY_LOGO_URL);
        return ApiResponse.ok(new CompanyInfoVO(
                StringUtils.hasText(name) ? name.trim() : DEFAULT_COMPANY_NAME,
                StringUtils.hasText(logoUrl) ? logoUrl.trim() : null));
    }

    private String configValue(String key) {
        SysConfig config = configMapper.selectOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        return config == null ? null : config.getConfigValue();
    }
}
