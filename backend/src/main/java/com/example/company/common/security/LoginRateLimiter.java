package com.example.company.common.security;

import com.example.company.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录接口的按 IP 限流：window 内失败达到 MAX_FAILURES 次即拒绝后续尝试。
 * <p>
 * D02 约定不引入 Redis，因此计数保存在本进程内存中：单实例部署足够，多实例部署时每个实例各自计数
 * （阈值需按实例数放大，或改由网关层实现）。成功登录会清空该 IP 的失败记录。
 */
@Component
public class LoginRateLimiter {

    /** 窗口内允许的失败次数，达到即 429 */
    public static final int MAX_FAILURES = 10;
    /** 计数窗口 */
    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 超过阈值直接拒绝；ip 为空（无请求上下文）时不做限制 */
    public void assertAllowed(String ip) {
        if (ip == null) {
            return;
        }
        Deque<Instant> attempts = failures.get(ip);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= MAX_FAILURES) {
                throw BusinessException.tooManyRequests("登录尝试过于频繁，请 5 分钟后再试");
            }
        }
    }

    public void recordFailure(String ip) {
        if (ip == null) {
            return;
        }
        Deque<Instant> attempts = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void recordSuccess(String ip) {
        if (ip != null) {
            failures.remove(ip);
        }
    }

    /** 清空全部计数（测试与运维排障用） */
    public void reset() {
        failures.clear();
    }

    private void prune(Deque<Instant> attempts) {
        Instant threshold = clock.instant().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
            attempts.pollFirst();
        }
    }
}
