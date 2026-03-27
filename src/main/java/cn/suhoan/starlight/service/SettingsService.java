package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.AppSetting;
import cn.suhoan.starlight.repository.AppSettingRepository;
import cn.suhoan.starlight.repository.UserAccountRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
@Transactional
public class SettingsService {

    public static final String REGISTRATION_ENABLED_KEY = "registration.enabled";
    public static final String SHARE_BASE_URL_KEY = "share.base-url";
    public static final String TOTP_ENABLED_KEY = "totp.enabled";
    public static final String PASSKEY_ENABLED_KEY = "passkey.enabled";

    private final AppSettingRepository appSettingRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserCredentialRepository userCredentialRepository;

    public SettingsService(AppSettingRepository appSettingRepository,
                           UserAccountRepository userAccountRepository,
                           UserCredentialRepository userCredentialRepository) {
        this.appSettingRepository = appSettingRepository;
        this.userAccountRepository = userAccountRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return Boolean.parseBoolean(getValue(REGISTRATION_ENABLED_KEY, "false"));
    }

    @Transactional(readOnly = true)
    public boolean isBootstrapAdminRegistrationRequired() {
        return userAccountRepository.countByAdminFlagTrue() == 0;
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationAvailable() {
        return isRegistrationEnabled() || isBootstrapAdminRegistrationRequired();
    }

    public void setRegistrationEnabled(boolean enabled) {
        saveValue(REGISTRATION_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Transactional(readOnly = true)
    public String getShareBaseUrl() {
        return getValue(SHARE_BASE_URL_KEY, "").trim();
    }

    public void setShareBaseUrl(String value) {
        String newUrl = value == null ? "" : value.trim();
        String oldUrl = getShareBaseUrl();
        saveValue(SHARE_BASE_URL_KEY, newUrl);

        // If new URL is not HTTPS, auto-disable passkey
        if (!isHttps(newUrl)) {
            saveValue(PASSKEY_ENABLED_KEY, "false");
        }

        // If domain changed, invalidate all existing passkeys
        String oldDomain = extractDomain(oldUrl);
        String newDomain = extractDomain(newUrl);
        if (!oldDomain.isEmpty() && !oldDomain.equals(newDomain)) {
            userCredentialRepository.deleteAll();
            saveValue(PASSKEY_ENABLED_KEY, "false");
        }
    }

    @Transactional(readOnly = true)
    public boolean isTotpEnabled() {
        return Boolean.parseBoolean(getValue(TOTP_ENABLED_KEY, "false"));
    }

    public void setTotpEnabled(boolean enabled) {
        saveValue(TOTP_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Transactional(readOnly = true)
    public boolean isPasskeyEnabled() {
        return Boolean.parseBoolean(getValue(PASSKEY_ENABLED_KEY, "false"));
    }

    public void setPasskeyEnabled(boolean enabled) {
        if (enabled) {
            String baseUrl = getShareBaseUrl();
            if (!isHttps(baseUrl)) {
                throw new IllegalArgumentException("通行密钥仅在站点 URL 为 HTTPS 协议时可开启");
            }
        }
        saveValue(PASSKEY_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Transactional(readOnly = true)
    public boolean isSiteUrlHttps() {
        return isHttps(getShareBaseUrl());
    }

    @Transactional(readOnly = true)
    public String getValue(String key, String defaultValue) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getSettingValue)
                .orElse(defaultValue);
    }

    public void saveValue(String key, String value) {
        AppSetting appSetting = appSettingRepository.findById(key).orElseGet(AppSetting::new);
        appSetting.setSettingKey(key);
        appSetting.setSettingValue(value);
        appSettingRepository.save(appSetting);
    }

    private boolean isHttps(String url) {
        return url != null && url.toLowerCase().startsWith("https://");
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
