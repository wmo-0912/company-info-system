package com.example.company.common.security;

import com.example.company.common.exception.BusinessException;

import java.security.SecureRandom;

/**
 * 密码复杂度策略与初始密码生成。
 * 复杂度要求刻意保持"可口头传达"的级别：8-64 位、至少包含一个字母和一个数字，
 * 与 D02 最小技术栈（不引入额外校验组件）保持一致。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    /** 生成初始密码时排除易混淆字符（0/O/1/l/I），方便管理员线下口头交付 */
    private static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /** 校验不通过时抛出 400；通过时静默返回 */
    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw BusinessException.badRequest("密码不能为空");
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw BusinessException.badRequest("密码长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BusinessException.badRequest("密码需同时包含字母和数字");
        }
    }

    /** 生成 12 位随机初始密码，保证至少各含一个大写字母、小写字母和数字 */
    public static String generate() {
        StringBuilder sb = new StringBuilder(12);
        sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS));
        String pool = UPPER + LOWER + DIGITS;
        while (sb.length() < 12) {
            sb.append(pick(pool));
        }
        return shuffle(sb);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }

    private static String shuffle(StringBuilder sb) {
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, tmp);
        }
        return sb.toString();
    }
}
