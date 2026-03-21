package cn.suhoan.startlight.service;

import cn.suhoan.startlight.entity.AppSetting;
import cn.suhoan.startlight.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

    public static final String REGISTRATION_ENABLED_KEY = "registration.enabled";
    public static final String SHARE_BASE_URL_KEY = "share.base-url";

    private final AppSettingRepository appSettingRepository;

    public SettingsService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return Boolean.parseBoolean(getValue(REGISTRATION_ENABLED_KEY, "false"));
    }

    public void setRegistrationEnabled(boolean enabled) {
        saveValue(REGISTRATION_ENABLED_KEY, Boolean.toString(enabled));
    }

    @Transactional(readOnly = true)
    public String getShareBaseUrl() {
        return getValue(SHARE_BASE_URL_KEY, "").trim();
    }

    public void setShareBaseUrl(String value) {
        saveValue(SHARE_BASE_URL_KEY, value == null ? "" : value.trim());
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
}

