package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.UserAccountRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import cn.suhoan.starlight.support.UsernameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证服务。
 * <p>处理用户注册、登录、个人信息更新和 TOTP 密钥管理等认证相关业务逻辑。</p>
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 邮箱格式校验正则 */
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

    /**
     * 注册新用户。
     * <p>校验注册开关和邮箱唯一性后创建用户账户。如果是系统中第一个注册的用户，自动设为管理员。</p>
     *
     * @param email    用户邮箱
     * @param password 明文密码
     * @return 新创建的用户账户
     * @throws IllegalArgumentException 当注册已关闭或邮箱已被注册时
     */
    public UserAccount register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        log.info("尝试注册新用户, email={}", normalizedEmail);

        if (!settingsService.isRegistrationAvailable()) {
            log.warn("注册被拒绝: 注册功能已关闭, email={}", normalizedEmail);
            throw new IllegalArgumentException("注册功能已关闭");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            log.warn("注册被拒绝: 邮箱已被注册, email={}", normalizedEmail);
            throw new IllegalArgumentException("邮箱已被注册");
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(normalizedEmail);
        userAccount.setUsername(UsernameGenerator.fromEmail(normalizedEmail, userAccountRepository::existsByUsername));
        userAccount.setPasswordHash(passwordService.hash(password));
        userAccount.setAdminFlag(settingsService.isBootstrapAdminRegistrationRequired());
        userAccount.setThemeId("win11-light");
        UserAccount saved = userAccountRepository.save(userAccount);

        log.info("用户注册成功: userId={}, username={}, isAdmin={}", saved.getId(), saved.getUsername(), saved.isAdminFlag());
        return saved;
    }

    /**
     * 用户登录验证。
     * <p>支持通过用户名或邮箱登录。验证通过后会检查用户主题是否存在。</p>
     *
     * @param usernameOrEmail 用户名或邮箱地址
     * @param password        明文密码
     * @return 验证通过的用户账户
     * @throws IllegalArgumentException 当用户名/密码不正确时
     */
    @Transactional(readOnly = true)
    public UserAccount login(String usernameOrEmail, String password) {
        String principal = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        log.info("用户尝试登录: principal={}", principal);

        UserAccount userAccount = principal.contains("@")
                ? userAccountRepository.findByEmail(principal.toLowerCase())
                .orElseThrow(() -> {
                    log.warn("登录失败: 邮箱不存在, email={}", principal);
                    return new IllegalArgumentException("用户名或密码错误");
                })
                : userAccountRepository.findByUsername(principal)
                .orElseThrow(() -> {
                    log.warn("登录失败: 用户名不存在, username={}", principal);
                    return new IllegalArgumentException("用户名或密码错误");
                });

        if (!passwordService.matches(password, userAccount.getPasswordHash())) {
            log.warn("登录失败: 密码错误, userId={}", userAccount.getId());
            throw new IllegalArgumentException("用户名或密码错误");
        }

        themeService.ensureThemeExists(userAccount);
        log.info("用户登录成功: userId={}, username={}", userAccount.getId(), userAccount.getUsername());
        return userAccount;
    }

    /**
     * 更新用户个人资料（用户名和/或密码）。
     * <p>必须验证当前密码以确认身份。支持单独修改用户名、单独修改密码或同时修改。</p>
     *
     * @param userAccount    当前登录用户
     * @param newUsername     新用户名，为 null 或空白表示不修改
     * @param currentPassword 当前密码（必须提供以确认身份）
     * @param newPassword     新密码，为 null 或空白表示不修改
     * @return 更新后的用户账户
     */
    public UserAccount updateProfile(UserAccount userAccount, String newUsername, String currentPassword, String newPassword) {
        log.info("更新用户资料: userId={}", userAccount.getId());

        // 验证当前密码
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("请输入当前密码以确认身份");
        }
        if (!passwordService.matches(currentPassword, userAccount.getPasswordHash())) {
            log.warn("更新资料失败: 当前密码错误, userId={}", userAccount.getId());
            throw new IllegalArgumentException("当前密码错误");
        }

        // 更新用户名
        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.trim();
            if (!trimmed.equals(userAccount.getUsername())) {
                if (trimmed.length() > 120) {
                    throw new IllegalArgumentException("用户名不能超过 120 个字符");
                }
                if (userAccountRepository.existsByUsername(trimmed)) {
                    log.warn("更新用户名失败: 用户名已被占用, username={}", trimmed);
                    throw new IllegalArgumentException("用户名已被占用");
                }
                log.info("用户名变更: userId={}, oldName={}, newName={}", userAccount.getId(), userAccount.getUsername(), trimmed);
                userAccount.setUsername(trimmed);
            }
        }

        // 更新密码
        if (newPassword != null && !newPassword.isBlank()) {
            passwordService.validate(newPassword);
            userAccount.setPasswordHash(passwordService.hash(newPassword));
            log.info("密码已更新: userId={}", userAccount.getId());
        }

        return userAccountRepository.save(userAccount);
    }

    /**
     * 更新用户的 TOTP 两步验证密钥。
     *
     * @param userAccount 包含新 TOTP 密钥的用户账户
     */
    public void updateTotpSecret(UserAccount userAccount) {
        log.info("更新 TOTP 密钥: userId={}, hasSecret={}", userAccount.getId(), userAccount.getTotpSecret() != null);
        userAccountRepository.save(userAccount);
    }

    /**
     * 将用户账户转换为前端展示用的个人资料 Map。
     * <p>包含用户基本信息、主题设置、TOTP 绑定状态和通行密钥数量。</p>
     *
     * @param userAccount 用户账户
     * @return 个人资料信息 Map
     */
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

    /**
     * 标准化并校验邮箱格式。
     * <p>去除首尾空格并转为小写，然后用正则校验格式。</p>
     *
     * @param email 原始邮箱字符串
     * @return 标准化后的邮箱
     * @throws IllegalArgumentException 当邮箱格式不合法时
     */
    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("请输入正确的邮箱地址");
        }
        return value;
    }
}
