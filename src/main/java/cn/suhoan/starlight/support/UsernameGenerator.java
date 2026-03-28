package cn.suhoan.starlight.support;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * 用户名生成器。
 * <p>根据邮箱地址自动生成唯一的用户名。从邮箱的本地部分提取基础名称，
 * 如遇冲突则自动追加数字后缀（如 user-2, user-3）。</p>
 */
public final class UsernameGenerator {

    private UsernameGenerator() {
    }

    /**
     * 根据邮箱生成唯一用户名。
     * <p>提取邮箱 @ 前的部分，去除特殊字符，转为小写作为基础用户名。
     * 如果已被占用，则追加递增数字后缀直到找到可用名称。</p>
     *
     * @param email  用户邮箱
     * @param exists 检查用户名是否已存在的函数
     * @return 生成的唯一用户名
     * @throws IllegalArgumentException 当无法生成唯一用户名时
     */
    public static String fromEmail(String email, Predicate<String> exists) {
        String localPart = email == null ? "" : email.strip();
        int atIndex = localPart.indexOf('@');
        if (atIndex > 0) {
            localPart = localPart.substring(0, atIndex);
        }
        String base = localPart.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        if (!exists.test(base)) {
            return base;
        }
        for (int index = 2; index < 10_000; index++) {
            String candidate = base + "-" + index;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("用户名生成失败，请稍后重试");
    }
}

