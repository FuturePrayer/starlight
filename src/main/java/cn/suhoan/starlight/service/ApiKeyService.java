package cn.suhoan.starlight.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.suhoan.starlight.entity.ApiKey;
import cn.suhoan.starlight.entity.ApiKeyScope;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.ApiKeyRepository;
import cn.suhoan.starlight.repository.ApiKeyScopeRepository;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.repository.UserCredentialRepository;
import cn.suhoan.starlight.support.McpApiKeyPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final int COPY_KEY_BYTES = 32;
    private static final int COPY_IV_BYTES = 12;
    private static final int COPY_TAG_LENGTH_BITS = 128;
    private final Object copyKeySecretLock = new Object();

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyScopeRepository apiKeyScopeRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryAccessService categoryAccessService;
    private final UserCredentialRepository userCredentialRepository;
    private final SettingsService settingsService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ApiKeyScopeRepository apiKeyScopeRepository,
                         CategoryRepository categoryRepository,
                         CategoryAccessService categoryAccessService,
                         UserCredentialRepository userCredentialRepository,
                         SettingsService settingsService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyScopeRepository = apiKeyScopeRepository;
        this.categoryRepository = categoryRepository;
        this.categoryAccessService = categoryAccessService;
        this.userCredentialRepository = userCredentialRepository;
        this.settingsService = settingsService;
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
        key.setSecretCiphertext(encryptRawApiKey(rawApiKey));
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

    /**
     * 断言当前用户至少启用了一个可用于复制 API Key 的二次验证方式。
         * <p>允许使用两步验证或通行密钥，二者任意一种存在即可。</p>
     */
    @Transactional(readOnly = true)
    public void assertCopyVerificationAvailable(UserAccount owner) {
        boolean hasTotp = owner.getTotpSecret() != null && !owner.getTotpSecret().isBlank();
        boolean hasPasskey = userCredentialRepository.countByUserId(owner.getId()) > 0;
        if (!hasTotp && !hasPasskey) {
            throw new IllegalArgumentException("请先开启两步验证或通行密钥后，再复制 API Key");
        }
    }

    /**
     * 在完成二次验证后返回 API Key 明文。
     * <p>旧版本创建的 API Key 未持久化明文，因此无法复制。</p>
     */
    @Transactional(readOnly = true)
    public String copyKey(UserAccount owner, String apiKeyId) {
        assertCopyVerificationAvailable(owner);
        ApiKey apiKey = getOwnedKey(owner.getId(), apiKeyId);
        if (apiKey.getSecretCiphertext() == null || apiKey.getSecretCiphertext().isBlank()) {
            throw new IllegalArgumentException("该 API Key 创建于旧版本，系统未保存可复制的明文");
        }
        return decryptRawApiKey(apiKey.getSecretCiphertext());
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
        List<ApiKeyScope> existingScopes = apiKeyScopeRepository.findByApiKeyIdOrderByCreatedAtAsc(apiKey.getId());
        if (allowAllCategories) {
            if (!existingScopes.isEmpty()) {
                apiKeyScopeRepository.deleteAllInBatch(existingScopes);
            }
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
        Map<String, ApiKeyScope> existingScopeByCategoryId = new HashMap<>();
        for (ApiKeyScope existingScope : existingScopes) {
            existingScopeByCategoryId.put(existingScope.getCategory().getId(), existingScope);
        }

        List<ApiKeyScope> scopesToDelete = existingScopes.stream()
                .filter(scope -> !distinctCategoryIds.contains(scope.getCategory().getId()))
                .toList();
        if (!scopesToDelete.isEmpty()) {
            apiKeyScopeRepository.deleteAllInBatch(scopesToDelete);
        }

        List<ApiKeyScope> scopesToCreate = new ArrayList<>();
        for (String categoryId : distinctCategoryIds) {
            if (existingScopeByCategoryId.containsKey(categoryId)) {
                continue;
            }
            Category category = categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("存在无效的分类授权范围"));
            ApiKeyScope scope = new ApiKeyScope();
            scope.setApiKey(apiKey);
            scope.setCategory(category);
            scopesToCreate.add(scope);
        }
        if (!scopesToCreate.isEmpty()) {
            apiKeyScopeRepository.saveAll(scopesToCreate);
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
        map.put("copyableFlag", apiKey.getSecretCiphertext() != null && !apiKey.getSecretCiphertext().isBlank());
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

    /**
     * 使用服务端主密钥加密 API Key 明文。
     * <p>这里采用 AES/GCM，密文中会拼接随机 IV，便于后续安全地解密复制。</p>
     */
    private String encryptRawApiKey(String rawApiKey) {
        try {
            byte[] iv = new byte[COPY_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(getOrCreateCopySecretKey(), "AES"),
                    new GCMParameterSpec(COPY_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(rawApiKey.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return BASE64_URL_ENCODER.encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 明文加密失败", exception);
        }
    }

    /**
     * 解密可复制 API Key 明文。
     * <p>如果服务端主密钥丢失或数据已损坏，会明确抛错，避免返回错误结果。</p>
     */
    private String decryptRawApiKey(String secretCiphertext) {
        try {
            byte[] payload = BASE64_URL_DECODER.decode(secretCiphertext);
            if (payload.length <= COPY_IV_BYTES) {
                throw new IllegalArgumentException("密文格式无效");
            }
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, COPY_IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, COPY_IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(getOrCreateCopySecretKey(), "AES"),
                    new GCMParameterSpec(COPY_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("API Key 明文解密失败", exception);
        }
    }

    /**
     * 获取用于 API Key 明文加解密的服务端密钥。
     * <p>密钥会延迟生成并持久化到应用配置中，确保兼容旧库并避免引入额外部署步骤。</p>
     */
    private byte[] getOrCreateCopySecretKey() {
        synchronized (copyKeySecretLock) {
            String current = settingsService.getValue(SettingsService.API_KEY_COPY_ENCRYPTION_KEY, "").trim();
            if (current.isBlank()) {
                byte[] secret = new byte[COPY_KEY_BYTES];
                SECURE_RANDOM.nextBytes(secret);
                current = BASE64_URL_ENCODER.encodeToString(secret);
                settingsService.saveValue(SettingsService.API_KEY_COPY_ENCRYPTION_KEY, current);
            }
            try {
                byte[] decoded = BASE64_URL_DECODER.decode(current);
                if (decoded.length != COPY_KEY_BYTES) {
                    throw new IllegalStateException("API Key 复制密钥长度无效");
                }
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("API Key 复制密钥格式无效", exception);
            }
        }
    }
}

