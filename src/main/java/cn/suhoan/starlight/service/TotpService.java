package cn.suhoan.starlight.service;

import cn.dev33.satoken.secure.totp.SaTotpUtil;
import org.springframework.stereotype.Service;

/**
 * TOTP两步验证
 *
 * @author suhoan
 */
@Service
public class TotpService {

    private static final String ISSUER = "Starlight";

    public String generateSecret() {
        return SaTotpUtil.generateSecretKey();
    }

    /**
     * Generate the otpauth:// URI used by authenticator apps.
     */
    public String generateOtpAuthUri(String account, String secretKey) {
        return SaTotpUtil.generateGoogleSecretKey(account, ISSUER, secretKey);
    }

    public boolean validate(String secretKey, String code) {
        if (secretKey == null || code == null || code.isBlank()) {
            return false;
        }
        return SaTotpUtil.validateTOTP(secretKey, code.trim(), 1);
    }
}

