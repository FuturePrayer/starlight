package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题服务
 *
 * @author suhoan
 */
@Service
@Transactional(readOnly = true)
public class ThemeService {

    private final UserAccountRepository userAccountRepository;

    public ThemeService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public List<Map<String, Object>> listThemes() {
        return builtinThemes();
    }

    public Map<String, Object> resolveTheme(String themeId) {
        return listThemes().stream()
                .filter(theme -> theme.get("id").equals(themeId))
                .findFirst()
                .orElseGet(() -> listThemes().stream()
                        .filter(theme -> theme.get("id").equals("win11-light"))
                        .findFirst()
                        .orElseGet(this::defaultTheme));
    }

    @Transactional
    public Map<String, Object> saveUserTheme(UserAccount userAccount, String themeId) {
        Map<String, Object> theme = resolveTheme(themeId);
        userAccount.setThemeId(theme.get("id").toString());
        userAccountRepository.save(userAccount);
        return theme;
    }

    @Transactional
    public void ensureThemeExists(UserAccount userAccount) {
        String themeId = userAccount.getThemeId();
        boolean exists = listThemes().stream().anyMatch(theme -> theme.get("id").equals(themeId));
        if (!exists) {
            userAccount.setThemeId("win11-light");
            userAccountRepository.save(userAccount);
        }
    }

    private List<Map<String, Object>> builtinThemes() {
        return List.of(
                createTheme("win11-light", "Windows 11 浅色", "#0f6cbd"),
                createTheme("win11-dark", "Windows 11 深色", "#60cdff"),
                createTheme("violet-light", "暮光紫 浅色", "#7563ff"),
                createTheme("golden-light", "琥珀金 浅色", "#b8860b"),
                createTheme("green-light", "薄荷绿 浅色", "#2e7d32"),
                createTheme("red-light", "樱花红 浅色", "#c62828")
        );
    }

    private Map<String, Object> defaultTheme() {
        return createTheme("win11-light", "Windows 11 浅色", "#0f6cbd");
    }

    private Map<String, Object> createTheme(String id, String name, String previewColor) {
        Map<String, Object> theme = new HashMap<>();
        theme.put("id", id);
        theme.put("name", name);
        theme.put("cssUrl", "");
        theme.put("previewColor", previewColor);
        theme.put("backgroundImage", "");
        theme.put("backgroundOpacity", 0);
        return theme;
    }
}

