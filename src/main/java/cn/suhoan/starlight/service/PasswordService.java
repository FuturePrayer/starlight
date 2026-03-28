package cn.suhoan.starlight.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 密码服务。
 * <p>提供密码哈希、验证和强度校验功能。使用 SHA-256 + 随机盐的方式存储密码。</p>
 *
 * @author suhoan
 */
@Service
public class PasswordService {

    /**
     * 对明文密码进行哈希处理。
     * <p>生成随机盐值，拼接密码后进行 SHA-256 哈希，返回 "盐:哈希值" 格式的字符串。</p>
     *
     * @param rawPassword 明文密码
     * @return "盐:哈希值" 格式的密码字符串
     */
    public String hash(String rawPassword) {
        validate(rawPassword);
        String salt = UUID.randomUUID().toString().replace("-", "");
        return salt + ":" + digest(salt + rawPassword);
    }

    /**
     * 验证明文密码是否与存储的密码哈希匹配。
     *
     * @param rawPassword     明文密码
     * @param storedPassword  存储的 "盐:哈希值" 格式密码
     * @return 密码是否匹配
     */
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || !storedPassword.contains(":")) {
            return false;
        }
        String[] parts = storedPassword.split(":", 2);
        return digest(parts[0] + rawPassword).equals(parts[1]);
    }

    /**
     * 校验密码强度（最少6位）。
     *
     * @param rawPassword 明文密码
     * @throws IllegalArgumentException 当密码不符合要求时
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("密码至少需要 6 位");
        }
    }

    /** 使用 SHA-256 算法计算哈希值 */
    private String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256", exception);
        }
    }
}

