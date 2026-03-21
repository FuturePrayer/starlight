package cn.suhoan.startlight.service;

import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.repository.UserAccountRepository;
import cn.suhoan.startlight.support.UsernameGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserAccountRepository userAccountRepository;
    private final PasswordService passwordService;
    private final SettingsService settingsService;
    private final ThemeService themeService;

    public AuthService(UserAccountRepository userAccountRepository,
                       PasswordService passwordService,
                       SettingsService settingsService,
                       ThemeService themeService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordService = passwordService;
        this.settingsService = settingsService;
        this.themeService = themeService;
    }

    public UserAccount register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (!userAccountRepository.findAll().isEmpty() && !settingsService.isRegistrationEnabled()) {
            throw new IllegalArgumentException("注册功能已关闭");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(normalizedEmail);
        userAccount.setUsername(UsernameGenerator.fromEmail(normalizedEmail, userAccountRepository::existsByUsername));
        userAccount.setPasswordHash(passwordService.hash(password));
        userAccount.setAdminFlag(userAccountRepository.count() == 0 && userAccountRepository.countByAdminFlagTrue() == 0);
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

    @Transactional(readOnly = true)
    public Map<String, Object> toProfile(UserAccount userAccount) {
        Map<String, Object> theme = themeService.resolveTheme(userAccount.getThemeId());
        return Map.of(
                "id", userAccount.getId(),
                "username", userAccount.getUsername(),
                "email", userAccount.getEmail(),
                "admin", userAccount.isAdminFlag(),
                "theme", theme
        );
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("请输入正确的邮箱地址");
        }
        return value;
    }
}

