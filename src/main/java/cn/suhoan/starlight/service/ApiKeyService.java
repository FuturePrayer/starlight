package cn.suhoan.starlight.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.suhoan.starlight.entity.ApiKey;
import cn.suhoan.starlight.entity.ApiKeyScope;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.ApiKeyRepository;
import cn.suhoan.starlight.repository.ApiKeyScopeRepository;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.support.McpApiKeyPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * API Key 管理服务。
 * <p>负责 API Key 的创建、更新、删除、查询，以及权限范围组装。</p>
 */
@Service
@Transactional
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyScopeRepository apiKeyScopeRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryAccessService categoryAccessService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ApiKeyScopeRepository apiKeyScopeRepository,
                         CategoryRepository categoryRepository,
                         CategoryAccessService categoryAccessService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyScopeRepository = apiKeyScopeRepository;
        this.categoryRepository = categoryRepository;
        this.categoryAccessService = categoryAccessService;
    }

    /** 查询当前用户的全部 API Key。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserKeys(String ownerId) {
        List<ApiKey> keys = apiKeyRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        Map<String, List<ApiKeyScope>> scopesMap = groupScopes(keys);
        return keys.stream()
                .map(key -> toSummary(key, scopesMap.getOrDefault(key.getId(), List.of())))
                .toList();
    }

    /** 创建新的 API Key，并返回仅展示一次的明文密钥。 */
    public Map<String, Object> createKey(UserAccount owner,
                                         String name,
                                         boolean readOnly,
                                         boolean allowAllCategories,
                                         Collection<String> scopeCategoryIds) {
        ApiKey key = new ApiKey();
        key.setOwner(owner);
        key.setName(normalizeName(name));
        key.setReadOnlyFlag(readOnly);
        key.setAllowAllCategoriesFlag(allowAllCategories);
        key.setEnabledFlag(true);

        String rawApiKey = generateRawApiKey();
        key.setKeyPrefix(maskPrefix(rawApiKey));
        key.setSecretHash(SaSecureUtil.sha256(rawApiKey));
        ApiKey saved = apiKeyRepository.save(key);
        saveScopes(saved, owner.getId(), allowAllCategories, scopeCategoryIds);
        log.info("API Key 创建成功: apiKeyId={}, ownerId={}, allowAllCategories={}, readOnly={}",
                saved.getId(), owner.getId(), allowAllCategories, readOnly);

        List<ApiKeyScope> scopes = apiKeyScopeRepository.findByApiKeyIdOrderByCreatedAtAsc(saved.getId());
        Map<String, Object> result = new HashMap<>(toSummary(saved, scopes));
        result.put("apiKey", rawApiKey);
        return result;
    }

    /** 更新 API Key 的备注、启用状态、只读状态和授权范围。 */
    public Map<String, Object> updateKey(UserAccount owner,
                                         String apiKeyId,
                                         String name,
                                         boolean enabled,
                                         boolean readOnly,
                                         boolean allowAllCategories,
                                         Collection<String> scopeCategoryIds) {
        ApiKey apiKey = getOwnedKey(owner.getId(), apiKeyId);
        apiKey.setName(normalizeName(name));
        apiKey.setEnabledFlag(enabled);
        apiKey.setReadOnlyFlag(readOnly);
        apiKey.setAllowAllCategoriesFlag(allowAllCategories);
        ApiKey saved = apiKeyRepository.save(apiKey);
        saveScopes(saved, owner.getId(), allowAllCategories, scopeCategoryIds);
        log.info("API Key 更新成功: apiKeyId={}, ownerId={}, enabled={}, readOnly={}, allowAllCategories={}",
                saved.getId(), owner.getId(), enabled, readOnly, allowAllCategories);
        List<ApiKeyScope> scopes = apiKeyScopeRepository.findByApiKeyIdOrderByCreatedAtAsc(saved.getId());
        return toSummary(saved, scopes);
    }

    /** 删除 API Key。 */
    public void deleteKey(String ownerId, String apiKeyId) {
        ApiKey apiKey = getOwnedKey(ownerId, apiKeyId);
        apiKeyScopeRepository.deleteByApiKeyId(apiKeyId);
        apiKeyRepository.delete(apiKey);
        log.info("API Key 删除成功: apiKeyId={}, ownerId={}", apiKeyId, ownerId);
    }

    /** 根据明文 API Key 完成认证，并解析完整权限主体。 */
    @Transactional(readOnly = true)
    public McpApiKeyPrincipal authenticate(String rawApiKey) {
        ApiKey apiKey = apiKeyRepository.findBySecretHash(SaSecureUtil.sha256(rawApiKey))
                .orElseThrow(() -> new IllegalArgumentException("API Key 无效"));
        if (!apiKey.isEnabledFlag()) {
            throw new IllegalArgumentException("API Key 已停用");
        }
        List<ApiKeyScope> scopes = apiKeyScopeRepository.findByApiKeyIdOrderByCreatedAtAsc(apiKey.getId());
        Set<String> rootCategoryIds = scopes.stream()
                .map(scope -> scope.getCategory().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> accessibleCategoryIds = apiKey.isAllowAllCategoriesFlag()
                ? Set.of()
                : categoryAccessService.expandAuthorizedCategoryIds(apiKey.getOwner().getId(), rootCategoryIds);
        return new McpApiKeyPrincipal(
                apiKey.getId(),
                apiKey.getOwner().getId(),
                apiKey.getOwner().getUsername(),
                apiKey.isReadOnlyFlag(),
                apiKey.isAllowAllCategoriesFlag(),
                rootCategoryIds,
                accessibleCategoryIds
        );
    }

    /** 刷新最近使用时间，避免过于频繁地写数据库。 */
    public void touchLastUsed(String apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "API Key 不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minusMinutes(1))) {
            apiKey.setLastUsedAt(now);
            apiKeyRepository.save(apiKey);
            log.debug("API Key 最近使用时间已刷新: apiKeyId={}, lastUsedAt={}", apiKeyId, now);
        }
    }

    /** 查询当前用户拥有的 API Key。 */
    @Transactional(readOnly = true)
    public ApiKey getOwnedKey(String ownerId, String apiKeyId) {
        return apiKeyRepository.findByIdAndOwnerId(apiKeyId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "API Key 不存在"));
    }

    private void saveScopes(ApiKey apiKey,
                            String ownerId,
                            boolean allowAllCategories,
                            Collection<String> scopeCategoryIds) {
        apiKeyScopeRepository.deleteByApiKeyId(apiKey.getId());
        if (allowAllCategories) {
            return;
        }
        Set<String> distinctCategoryIds = new LinkedHashSet<>();
        if (scopeCategoryIds != null) {
            for (String scopeCategoryId : scopeCategoryIds) {
                if (scopeCategoryId != null && !scopeCategoryId.isBlank()) {
                    distinctCategoryIds.add(scopeCategoryId);
                }
            }
        }
        for (String categoryId : distinctCategoryIds) {
            Category category = categoryRepository.findByIdAndOwnerId(categoryId, ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("存在无效的分类授权范围"));
            ApiKeyScope scope = new ApiKeyScope();
            scope.setApiKey(apiKey);
            scope.setCategory(category);
            apiKeyScopeRepository.save(scope);
        }
    }

    private Map<String, List<ApiKeyScope>> groupScopes(List<ApiKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ApiKeyScope>> map = new HashMap<>();
        List<ApiKeyScope> scopes = apiKeyScopeRepository.findByApiKeyIdIn(
                keys.stream().map(ApiKey::getId).toList());
        for (ApiKeyScope scope : scopes) {
            map.computeIfAbsent(scope.getApiKey().getId(), key -> new ArrayList<>()).add(scope);
        }
        return map;
    }

    private Map<String, Object> toSummary(ApiKey apiKey, List<ApiKeyScope> scopes) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", apiKey.getId());
        map.put("name", apiKey.getName());
        map.put("keyPrefix", apiKey.getKeyPrefix());
        map.put("enabledFlag", apiKey.isEnabledFlag());
        map.put("readOnlyFlag", apiKey.isReadOnlyFlag());
        map.put("allowAllCategoriesFlag", apiKey.isAllowAllCategoriesFlag());
        map.put("lastUsedAt", apiKey.getLastUsedAt());
        map.put("createdAt", apiKey.getCreatedAt());
        map.put("updatedAt", apiKey.getUpdatedAt());
        map.put("scopeCategoryIds", scopes.stream().map(scope -> scope.getCategory().getId()).toList());
        map.put("scopes", scopes.stream().map(scope -> {
            Map<String, Object> scopeMap = new HashMap<>();
            scopeMap.put("id", scope.getCategory().getId());
            scopeMap.put("name", scope.getCategory().getName());
            scopeMap.put("parentId", scope.getCategory().getParent() == null ? null : scope.getCategory().getParent().getId());
            return scopeMap;
        }).toList());
        return map;
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("API Key 名称不能为空");
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private String generateRawApiKey() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        StringBuilder builder = new StringBuilder("slk_");
        for (byte value : random) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String maskPrefix(String rawApiKey) {
        return rawApiKey.substring(0, Math.min(rawApiKey.length(), 14));
    }
}

