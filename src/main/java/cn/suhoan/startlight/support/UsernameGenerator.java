package cn.suhoan.startlight.support;

import java.util.Locale;
import java.util.function.Predicate;

public final class UsernameGenerator {

    private UsernameGenerator() {
    }

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

