package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.AppSetting;
import cn.suhoan.starlight.repository.AppSettingRepository;
import cn.suhoan.starlight.repository.UserAccountRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

/**
 * 系统设置服务。
 * <p>管理应用级别的配置项，包括注册开关、分享 URL、TOTP 和通行密钥开关等。</p>
 *
 * @author suhoan
 */
@Service
@Transactional
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    /** 配置项：是否允许注册 */
    public static final String REGISTRATION_ENABLED_KEY = "registration.enabled";
    /** 配置项：分享功能的基础 URL */
    public static final String SHARE_BASE_URL_KEY = "share.base-url";
    /** 配置项：是否启用 TOTP 两步验证 */
    public static final String TOTP_ENABLED_KEY = "totp.enabled";
    /** 配置项：是否启用通行密钥 */
    public static final String PASSKEY_ENABLED_KEY = "passkey.enabled";
    /** 配置项：是否启用 MCP Server */
    public static final String MCP_ENABLED_KEY = "mcp.enabled";
    /** 配置项：是否启用 Git 导入 */
    public static final String GIT_IMPORT_ENABLED_KEY = "git.import.enabled";
    /** 配置项：Git 导入最大并发数，0 或负数表示不限制 */
    public static final String GIT_IMPORT_MAX_CONCURRENT_KEY = "git.import.max-concurrent";

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

    /** 查询注册功能是否开启 */
    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return Boolean.parseBoolean(getValue(REGISTRATION_ENABLED_KEY, "false"));
    }

    /** 判断是否需要引导注册首个管理员（系统中无管理员时为 true） */
    @Transactional(readOnly = true)
    public boolean isBootstrapAdminRegistrationRequired() {
        return userAccountRepository.countByAdminFlagTrue() == 0;
    }

    /** 判断注册是否可用（注册开关开启或需要引导管理员） */
    @Transactional(readOnly = true)
    public boolean isRegistrationAvailable() {
        return isRegistrationEnabled() || isBootstrapAdminRegistrationRequired();
    }

    /** 设置注册开关 */
    public void setRegistrationEnabled(boolean enabled) {
        log.info("设置注册开关: enabled={}", enabled);
        saveValue(REGISTRATION_ENABLED_KEY, Boolean.toString(enabled));
    }

    /** 获取分享基础 URL */
    @Transactional(readOnly = true)
    public String getShareBaseUrl() {
        return getValue(SHARE_BASE_URL_KEY, "").trim();
    }

    /**
     * 设置分享基础 URL。
     * <p>如果新 URL 不是 HTTPS，会自动禁用通行密钥。如果域名发生变化，会删除所有已注册的通行密钥。</p>
     */
    public void setShareBaseUrl(String value) {
        String newUrl = value == null ? "" : value.trim();
        String oldUrl = getShareBaseUrl();
        log.info("设置分享基础 URL: oldUrl={}, newUrl={}", oldUrl, newUrl);
        saveValue(SHARE_BASE_URL_KEY, newUrl);

        // 如果新 URL 不是 HTTPS，自动禁用通行密钥
        if (!isHttps(newUrl)) {
            log.info("站点 URL 非 HTTPS，自动禁用通行密钥");
            saveValue(PASSKEY_ENABLED_KEY, "false");
        }

        // 如果域名发生变化，清除所有已注册的通行密钥
        String oldDomain = extractDomain(oldUrl);
        String newDomain = extractDomain(newUrl);
        if (!oldDomain.isEmpty() && !oldDomain.equals(newDomain)) {
            log.info("域名变更 ({} -> {})，清除所有通行密钥", oldDomain, newDomain);
            userCredentialRepository.deleteAll();
            saveValue(PASSKEY_ENABLED_KEY, "false");
        }
    }

    @Transactional(readOnly = true)
    public boolean isTotpEnabled() {
        return Boolean.parseBoolean(getValue(TOTP_ENABLED_KEY, "false"));
    }

    /** 设置 TOTP 两步验证开关 */
    public void setTotpEnabled(boolean enabled) {
        log.info("设置 TOTP 开关: enabled={}", enabled);
        saveValue(TOTP_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Transactional(readOnly = true)
    public boolean isPasskeyEnabled() {
        return Boolean.parseBoolean(getValue(PASSKEY_ENABLED_KEY, "false"));
    }

    /** 查询 MCP Server 是否已启用。 */
    @Transactional(readOnly = true)
    public boolean isMcpServerEnabled() {
        return Boolean.parseBoolean(getValue(MCP_ENABLED_KEY, "false"));
    }

    /** 设置通行密钥开关，仅在 HTTPS 站点下允许启用 */
    public void setPasskeyEnabled(boolean enabled) {
        if (enabled) {
            String baseUrl = getShareBaseUrl();
            if (!isHttps(baseUrl)) {
                throw new IllegalArgumentException("通行密钥仅在站点 URL 为 HTTPS 协议时可开启");
            }
        }
        saveValue(PASSKEY_ENABLED_KEY, Boolean.toString(enabled));
    }

    /** 设置 MCP Server 开关。 */
    public void setMcpServerEnabled(boolean enabled) {
        log.info("设置 MCP Server 开关: enabled={}", enabled);
        saveValue(MCP_ENABLED_KEY, Boolean.toString(enabled));
    }

    /** 查询 Git 导入功能是否已启用。 */
    @Transactional(readOnly = true)
    public boolean isGitImportEnabled() {
        return Boolean.parseBoolean(getValue(GIT_IMPORT_ENABLED_KEY, "false"));
    }

    /** 设置 Git 导入功能开关。 */
    public void setGitImportEnabled(boolean enabled) {
        log.info("设置 Git 导入开关: enabled={}", enabled);
        saveValue(GIT_IMPORT_ENABLED_KEY, Boolean.toString(enabled));
    }

    /** 获取 Git 导入最大并发数。 */
    @Transactional(readOnly = true)
    public int getGitImportMaxConcurrent() {
        try {
            return Integer.parseInt(getValue(GIT_IMPORT_MAX_CONCURRENT_KEY, "2").trim());
        } catch (Exception exception) {
            return 2;
        }
    }

    /** 设置 Git 导入最大并发数，0 或负数表示不限制。 */
    public void setGitImportMaxConcurrent(int limit) {
        if (limit > 1000) {
            throw new IllegalArgumentException("Git 导入最大并发数不能超过 1000");
        }
        log.info("设置 Git 导入最大并发数: limit={}", limit);
        saveValue(GIT_IMPORT_MAX_CONCURRENT_KEY, Integer.toString(limit));
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

    /**
     * 保存配置项到数据库。
     *
     * @param key   配置项键名
     * @param value 配置项值
     */
    public void saveValue(String key, String value) {
        AppSetting appSetting = appSettingRepository.findById(key).orElseGet(AppSetting::new);
        appSetting.setSettingKey(key);
        appSetting.setSettingValue(value);
        appSettingRepository.save(appSetting);
    }

    /** 判断 URL 是否使用 HTTPS 协议 */
    private boolean isHttps(String url) {
        return url != null && url.toLowerCase().startsWith("https://");
    }

    /** 从 URL 中提取域名 */
    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
