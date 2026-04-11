package cn.suhoan.starlight.support;

import java.util.Set;

/**
 * MCP API Key 认证主体。
 *
 * @param apiKeyId                API Key ID
 * @param ownerId                 所属用户 ID
 * @param ownerUsername           所属用户名
 * @param readOnly                是否只读
 * @param allowAllCategories      是否允许访问全部分类
 * @param scopeRootCategoryIds    显式授权的根分类 ID 集合
 * @param accessibleCategoryIds   展开后的可访问分类 ID 集合（包含子分类）
 */
public record McpApiKeyPrincipal(
        String apiKeyId,
        String ownerId,
        String ownerUsername,
        boolean readOnly,
        boolean allowAllCategories,
        Set<String> scopeRootCategoryIds,
        Set<String> accessibleCategoryIds
) {
}

