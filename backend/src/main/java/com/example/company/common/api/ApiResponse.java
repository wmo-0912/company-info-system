package com.example.company.common.api;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private OffsetDateTime timestamp;

    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", "success", null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
