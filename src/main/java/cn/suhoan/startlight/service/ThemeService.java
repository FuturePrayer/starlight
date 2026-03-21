package cn.suhoan.startlight.service;

import cn.suhoan.startlight.config.StarlightProperties;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.repository.UserAccountRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ThemeService {

    private final ObjectMapper objectMapper;
    private final StarlightProperties starlightProperties;
    private final UserAccountRepository userAccountRepository;

    public ThemeService(ObjectMapper objectMapper,
                        StarlightProperties starlightProperties,
                        UserAccountRepository userAccountRepository) {
        this.objectMapper = objectMapper;
        this.starlightProperties = starlightProperties;
        this.userAccountRepository = userAccountRepository;
    }

    public List<Map<String, Object>> listThemes() {
        List<Map<String, Object>> themes = new ArrayList<>();
        themes.addAll(builtinThemes());
        themes.addAll(readClasspathThemes());
        themes.addAll(readExternalThemes());
        themes.sort(Comparator.comparing(item -> item.get("id").toString()));
        return themes;
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

    private List<Map<String, Object>> readClasspathThemes() {
        List<Map<String, Object>> themes = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:static/themes/*/theme.json");
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    JsonNode node = objectMapper.readTree(inputStream);
                    themes.add(nodeToTheme(node));
                }
            }
        } catch (IOException ignored) {
        }
        return themes;
    }

    private List<Map<String, Object>> readExternalThemes() {
        List<Map<String, Object>> themes = new ArrayList<>();
        Path root = Path.of(starlightProperties.getThemeDir()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return themes;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                Path jsonPath = dir.resolve("theme.json");
                if (Files.exists(jsonPath)) {
                    try (InputStream inputStream = Files.newInputStream(jsonPath)) {
                        JsonNode node = objectMapper.readTree(inputStream);
                        Map<String, Object> map = nodeToTheme(node);
                        map.put("cssUrl", "/theme-files/" + dir.getFileName() + "/theme.css");
                        themes.add(map);
                    } catch (IOException ignored) {
                    }
                }
            });
        } catch (IOException ignored) {
        }
        return themes;
    }

    private Map<String, Object> nodeToTheme(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", readText(node, "id", "default"));
        map.put("name", readText(node, "name", "默认主题"));
        map.put("cssUrl", readText(node, "cssUrl", "/themes/default/theme.css"));
        map.put("previewColor", readText(node, "previewColor", "#5b7cfa"));
        map.put("backgroundImage", readText(node, "backgroundImage", ""));
        map.put("backgroundOpacity", node.path("backgroundOpacity").asDouble(0));
        return map;
    }

    private String readText(JsonNode node, String field, String defaultValue) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return defaultValue;
        }
        String value = child.asString();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private List<Map<String, Object>> builtinThemes() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> light = new HashMap<>();
        light.put("id", "win11-light");
        light.put("name", "Windows 11 浅色");
        light.put("cssUrl", "");
        light.put("previewColor", "#005fb8");
        light.put("backgroundImage", "");
        light.put("backgroundOpacity", 0);
        list.add(light);

        Map<String, Object> dark = new HashMap<>();
        dark.put("id", "win11-dark");
        dark.put("name", "Windows 11 深色");
        dark.put("cssUrl", "");
        dark.put("previewColor", "#60cdff");
        dark.put("backgroundImage", "");
        dark.put("backgroundOpacity", 0);
        list.add(dark);
        return list;
    }

    private Map<String, Object> defaultTheme() {
        return builtinThemes().getFirst();
    }
}

