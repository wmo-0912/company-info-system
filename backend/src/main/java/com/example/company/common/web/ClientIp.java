package com.example.company.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** 解析真实客户端 IP：优先 X-Forwarded-For 首段（Nginx 反代），其次 X-Real-IP、remoteAddr */
public final class ClientIp {

    private static final int MAX_LENGTH = 64;

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = null;
        if (StringUtils.hasText(forwarded)) {
            ip = forwarded.split(",")[0].trim();
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }
}
