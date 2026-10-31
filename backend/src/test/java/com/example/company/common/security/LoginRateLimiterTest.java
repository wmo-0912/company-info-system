package com.example.company.common.security;

import com.example.company.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("登录按 IP 限流")
class LoginRateLimiterTest {

    private static final String IP = "203.0.113.9";

    /** 可手动推进的时钟，用来验证窗口过期 */
    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-30T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void plus(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    @Test
    @DisplayName("失败次数未达阈值时放行")
    void allowsBelowThreshold() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure(IP);
        }
        assertThatCode(() -> limiter.assertAllowed(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("同一 IP 失败满 10 次后拒绝，错误码 429")
    void rejectsAfterMaxFailures() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        assertThatThrownBy(() -> limiter.assertAllowed(IP))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(429);
                    assertThat(ex.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                    assertThat(ex.getMessage()).contains("5 分钟");
                });
        // 其他 IP 不受影响
        assertThatCode(() -> limiter.assertAllowed("198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("成功登录清空该 IP 的失败记录")
    void successResetsCounter() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        limiter.recordSuccess(IP);
        assertThatCode(() -> limiter.assertAllowed(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("窗口外的失败不再计入")
    void oldFailuresExpire() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        clock.plus(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertThatCode(() -> limiter.assertAllowed(IP)).doesNotThrowAnyException();
        limiter.recordFailure(IP);
        assertThatCode(() -> limiter.assertAllowed(IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IP 为空时不做限制（无请求上下文）")
    void nullIpSkips() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MutableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES * 2; i++) {
            limiter.recordFailure(null);
        }
        assertThatCode(() -> limiter.assertAllowed(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset 清空全部计数")
    void resetClearsAll() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure(IP);
        }
        limiter.reset();
        assertThatCode(() -> limiter.assertAllowed(IP)).doesNotThrowAnyException();
    }
}
