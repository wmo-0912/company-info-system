package com.example.company.user;

import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void rejectsTooShortOrTooLong() {
        assertEquals(400, assertThrows(BusinessException.class, () -> PasswordPolicy.validate("a1b2c3")).getHttpStatus());
        String longPwd = "a1" + "x".repeat(63);
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(longPwd));
    }

    @Test
    void requiresBothLetterAndDigit() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("abcdefgh"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("12345678"));
    }

    @Test
    void acceptsCompliantPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Company@2024"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a1b2c3d4"));
    }

    @Test
    void generatedPasswordAlwaysPassesPolicyAndIsRandom() {
        String first = PasswordPolicy.generate();
        String second = PasswordPolicy.generate();
        assertEquals(12, first.length());
        assertDoesNotThrow(() -> PasswordPolicy.validate(first));
        assertNotEquals(first, second);
    }
}
