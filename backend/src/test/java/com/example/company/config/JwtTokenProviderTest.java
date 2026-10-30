package com.example.company.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "test-secret-key-test-secret-key-0123456789");
        ReflectionTestUtils.setField(provider, "expirationMinutes", 120L);
        provider.init();
    }

    @Test
    void generateAndParseToken() {
        String token = provider.generateToken(1L, "admin", 0);
        Claims claims = provider.parse(token);
        assertNotNull(claims);
        assertEquals("1", claims.getSubject());
        assertEquals("admin", claims.get("username"));
    }

    @Test
    void rejectInvalidToken() {
        assertNull(provider.parse("invalid.token.value"));
    }

    @Test
    void rejectTokenSignedByOtherKey() {
        JwtTokenProvider other = new JwtTokenProvider();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-another-secret-0123456789");
        ReflectionTestUtils.setField(other, "expirationMinutes", 120L);
        other.init();
        String token = other.generateToken(1L, "admin", 0);
        assertNull(provider.parse(token));
    }

    @Test
    void rejectShortSecret() {
        JwtTokenProvider weak = new JwtTokenProvider();
        ReflectionTestUtils.setField(weak, "secret", "short");
        ReflectionTestUtils.setField(weak, "expirationMinutes", 120L);
        assertThrows(IllegalStateException.class, weak::init);
    }
}
