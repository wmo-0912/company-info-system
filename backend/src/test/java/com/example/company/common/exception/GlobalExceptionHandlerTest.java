package com.example.company.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("唯一约束冲突返回 409 而不是 500")
    void duplicateKeyMapsToConflict() {
        ResponseEntity<?> response = handler.handleDuplicateKey(
                new DuplicateKeyException("duplicate key value violates unique constraint \"uk_employee_period\""));

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("DUPLICATE_KEY"));
    }

    @Test
    @DisplayName("外键/非空/长度等约束错误返回 400，并给出可操作提示")
    void integrityViolationMapsToBadRequest() {
        ResponseEntity<?> response = handler.handleIntegrityViolation(
                new DataIntegrityViolationException("null value in column \"employee_no\" violates not-null constraint"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("DATA_INTEGRITY_VIOLATION"));
    }

    @Test
    @DisplayName("业务异常按自身状态码与错误码返回")
    void businessExceptionKeepsStatus() {
        ResponseEntity<?> response = handler.handleBusiness(BusinessException.notFound("员工不存在"));

        assertEquals(404, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("员工不存在"));
    }
}
