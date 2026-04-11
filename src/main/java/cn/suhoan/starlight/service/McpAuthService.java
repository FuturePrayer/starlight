package cn.suhoan.starlight.service;

import cn.suhoan.starlight.support.McpApiKeyPrincipal;
import cn.suhoan.starlight.support.McpRequestContextHolder;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * MCP API Key 鉴权服务。
 * <p>负责 MCP 请求头校验、ThreadLocal 认证主体传递以及工具内部权限断言。</p>
 */
@Service
public class McpAuthService {

    public static final String TRANSPORT_PRINCIPAL_KEY = "starlight.mcp.principal";

    private static final Logger log = LoggerFactory.getLogger(McpAuthService.class);

    private final SettingsService settingsService;
    private final ApiKeyService apiKeyService;

    public McpAuthService(SettingsService settingsService, ApiKeyService apiKeyService) {
        this.settingsService = settingsService;
        this.apiKeyService = apiKeyService;
    }

    /**
     * 在 MCP transport 层进行校验。
     */
    public void validateAndBind(Map<String, List<String>> headers) throws ServerTransportSecurityException {
        if (!settingsService.isMcpServerEnabled()) {
            throw new ServerTransportSecurityException(HttpStatus.SERVICE_UNAVAILABLE.value(), "管理员尚未开启 MCP Server");
        }
        String rawApiKey = extractApiKey(headers);
        try {
            McpApiKeyPrincipal principal = apiKeyService.authenticate(rawApiKey);
            apiKeyService.touchLastUsed(principal.apiKeyId());
            McpRequestContextHolder.set(principal);
            log.debug("MCP API Key 认证成功: apiKeyId={}, ownerId={}", principal.apiKeyId(), principal.ownerId());
        } catch (IllegalArgumentException exception) {
            log.warn("MCP API Key 认证失败: {}", exception.getMessage());
            throw new ServerTransportSecurityException(UNAUTHORIZED.value(), exception.getMessage());
        }
    }

    /** 从 transport context 中提取当前认证主体。 */
    public McpApiKeyPrincipal requirePrincipal(McpTransportContext transportContext) {
        Object principal = transportContext == null ? null : transportContext.get(TRANSPORT_PRINCIPAL_KEY);
        if (principal instanceof McpApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal;
        }
        throw new ResponseStatusException(UNAUTHORIZED, "MCP 认证信息缺失");
    }

    /** 断言当前 key 可写。 */
    public McpApiKeyPrincipal requireWritable(McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = requirePrincipal(transportContext);
        if (principal.readOnly()) {
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 为只读，不能执行写操作");
        }
        return principal;
    }

    /** 断言分类在当前 key 的权限范围内。 */
    public void assertCategoryAccessible(McpApiKeyPrincipal principal, String categoryId) {
        String normalizedCategoryId = NoteService.normalizeNullableCategoryId(categoryId);
        if (normalizedCategoryId == null || principal.allowAllCategories()) {
            return;
        }
        if (!principal.accessibleCategoryIds().contains(normalizedCategoryId)) {
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 无权访问该分类");
        }
    }

    /** 断言笔记所属分类在当前 key 的权限范围内。 */
    public void assertNoteAccessible(McpApiKeyPrincipal principal, String categoryId) {
        if (principal.allowAllCategories()) {
            return;
        }
        if (categoryId == null || categoryId.isBlank() || !principal.accessibleCategoryIds().contains(categoryId)) {
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 无权访问该笔记");
        }
    }

    /** 将 ThreadLocal 中的认证主体转换为 transport metadata。 */
    public Map<String, Object> exportTransportMetadata() {
        McpApiKeyPrincipal principal = McpRequestContextHolder.get();
        if (principal == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "MCP 认证信息缺失");
        }
        return Map.of(TRANSPORT_PRINCIPAL_KEY, principal);
    }

    private String extractApiKey(Map<String, List<String>> headers) throws ServerTransportSecurityException {
        String authorization = firstHeader(headers, "authorization");
        if (authorization != null && authorization.toLowerCase().startsWith("bearer ")) {
            String token = authorization.substring(7).trim();
            if (!token.isBlank()) {
                return token;
            }
        }
        String xApiKey = firstHeader(headers, "x-api-key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.trim();
        }
        throw new ServerTransportSecurityException(UNAUTHORIZED.value(), "缺少 API Key");
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return null;
    }
}

