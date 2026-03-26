package cn.suhoan.starlight.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PasswordService {

    public String hash(String rawPassword) {
        validate(rawPassword);
        String salt = UUID.randomUUID().toString().replace("-", "");
        return salt + ":" + digest(salt + rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || !storedPassword.contains(":")) {
            return false;
        }
        String[] parts = storedPassword.split(":", 2);
        return digest(parts[0] + rawPassword).equals(parts[1]);
    }

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("密码至少需要 6 位");
        }
    }

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

