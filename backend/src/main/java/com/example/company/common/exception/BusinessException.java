package com.example.company.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", 400, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", 401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", 403, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", 404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("CONFLICT", 409, message);
    }

    public static BusinessException unprocessable(String message) {
        return new BusinessException("UNPROCESSABLE", 422, message);
    }

    /** 429：请求过于频繁（如登录按 IP 限流） */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException("TOO_MANY_REQUESTS", 429, message);
    }
}
