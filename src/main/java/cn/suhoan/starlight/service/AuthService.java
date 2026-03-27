package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.UserAccountRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import cn.suhoan.starlight.support.UsernameGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserAccountRepository userAccountRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService passwordService;
    private final SettingsService settingsService;
    private final ThemeService themeService;

    public AuthService(UserAccountRepository userAccountRepository,
                       UserCredentialRepository userCredentialRepository,
                       PasswordService passwordService,
                       SettingsService settingsService,
                       ThemeService themeService) {
        this.userAccountRepository = userAccountRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.passwordService = passwordService;
        this.settingsService = settingsService;
        this.themeService = themeService;
    }

    public UserAccount register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (!settingsService.isRegistrationAvailable()) {
            throw new IllegalArgumentException("注册功能已关闭");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(normalizedEmail);
        userAccount.setUsername(UsernameGenerator.fromEmail(normalizedEmail, userAccountRepository::existsByUsername));
        userAccount.setPasswordHash(passwordService.hash(password));
        userAccount.setAdminFlag(settingsService.isBootstrapAdminRegistrationRequired());
        userAccount.setThemeId("win11-light");
        return userAccountRepository.save(userAccount);
    }

    @Transactional(readOnly = true)
    public UserAccount login(String usernameOrEmail, String password) {
        String principal = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        UserAccount userAccount = principal.contains("@")
                ? userAccountRepository.findByEmail(principal.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"))
                : userAccountRepository.findByUsername(principal)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordService.matches(password, userAccount.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        themeService.ensureThemeExists(userAccount);
        return userAccount;
    }

    public UserAccount updateProfile(UserAccount userAccount, String newUsername, String currentPassword, String newPassword) {
        // Verify current password
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("请输入当前密码以确认身份");
        }
        if (!passwordService.matches(currentPassword, userAccount.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }
        // Update username
        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.trim();
            if (!trimmed.equals(userAccount.getUsername())) {
                if (trimmed.length() > 120) {
                    throw new IllegalArgumentException("用户名不能超过 120 个字符");
                }
                if (userAccountRepository.existsByUsername(trimmed)) {
                    throw new IllegalArgumentException("用户名已被占用");
                }
                userAccount.setUsername(trimmed);
            }
        }
        // Update password
        if (newPassword != null && !newPassword.isBlank()) {
            passwordService.validate(newPassword);
            userAccount.setPasswordHash(passwordService.hash(newPassword));
        }
        return userAccountRepository.save(userAccount);
    }

    public void updateTotpSecret(UserAccount userAccount) {
        userAccountRepository.save(userAccount);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> toProfile(UserAccount userAccount) {
        Map<String, Object> theme = themeService.resolveTheme(userAccount.getThemeId());
        boolean hasTotpSecret = userAccount.getTotpSecret() != null && !userAccount.getTotpSecret().isBlank();
        long passkeyCount = userCredentialRepository.countByUserId(userAccount.getId());
        Map<String, Object> map = new HashMap<>();
        map.put("id", userAccount.getId());
        map.put("username", userAccount.getUsername());
        map.put("email", userAccount.getEmail());
        map.put("admin", userAccount.isAdminFlag());
        map.put("theme", theme);
        map.put("totpBound", hasTotpSecret);
        map.put("passkeyCount", passkeyCount);
        return map;
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("请输入正确的邮箱地址");
        }
        return value;
    }
}
