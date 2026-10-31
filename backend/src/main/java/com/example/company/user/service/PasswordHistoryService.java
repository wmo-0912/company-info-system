package com.example.company.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.exception.BusinessException;
import com.example.company.user.entity.SysPasswordHistory;
import com.example.company.user.mapper.SysPasswordHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 密码历史：记录最近 N 个密码的哈希，避免用户在若干个旧密码之间来回切换。
 * 只存哈希，且只保留最近 {@link #HISTORY_DEPTH} 条，表不会无限增长。
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    /** 保留/比对的密码个数 */
    public static final int HISTORY_DEPTH = 5;

    private final SysPasswordHistoryMapper passwordHistoryMapper;
    private final PasswordEncoder passwordEncoder;

    /** 新密码命中任一历史密码时返回 400 */
    public void assertNotReused(Long userId, String rawPassword) {
        if (userId == null || rawPassword == null) {
            return;
        }
        for (SysPasswordHistory history : recent(userId)) {
            if (passwordEncoder.matches(rawPassword, history.getPasswordHash())) {
                throw BusinessException.badRequest(
                        "新密码不能与最近 " + HISTORY_DEPTH + " 次使用过的密码相同");
            }
        }
    }

    /** 记录一条历史密码，并裁剪超出深度的旧记录 */
    public void record(Long userId, String passwordHash) {
        if (userId == null || passwordHash == null) {
            return;
        }
        SysPasswordHistory history = new SysPasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(passwordHash);
        passwordHistoryMapper.insert(history);
        prune(userId);
    }

    private List<SysPasswordHistory> recent(Long userId) {
        List<SysPasswordHistory> list = passwordHistoryMapper.selectList(
                new QueryWrapper<SysPasswordHistory>()
                        .eq("user_id", userId)
                        .orderByDesc("id")
                        .last("limit " + HISTORY_DEPTH));
        return list == null ? List.of() : list;
    }

    private void prune(Long userId) {
        List<Long> keep = recent(userId).stream()
                .map(SysPasswordHistory::getId)
                .filter(Objects::nonNull)
                .toList();
        if (keep.size() < HISTORY_DEPTH) {
            return;
        }
        passwordHistoryMapper.delete(new QueryWrapper<SysPasswordHistory>()
                .eq("user_id", userId)
                .notIn("id", keep));
    }
}
