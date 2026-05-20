package cn.suhoan.starlight.service;

/**
 * 分类 ID 归一化工具。
 * <p>集中处理 Web/MCP 客户端传入空分类时可能出现的 null、空串、"null"、"undefined"。</p>
 */
public final class CategoryIdNormalizer {

    private CategoryIdNormalizer() {
    }

    /** 将“无分类”的各种输入形式统一转换为 null。 */
    public static String normalizeNullableCategoryId(String categoryId) {
        if (categoryId == null) {
            return null;
        }
        String normalized = categoryId.trim();
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}
