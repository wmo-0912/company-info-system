package com.example.company.system.vo;

/**
 * 登录页与侧栏展示用的公司信息。仅包含可公开的名称与 Logo，不返回任何业务配置。
 *
 * @param name    公司名称（未配置时回落为默认名称）
 * @param logoUrl Logo 图片地址，未配置时为 null
 */
public record CompanyInfoVO(String name, String logoUrl) {
}
