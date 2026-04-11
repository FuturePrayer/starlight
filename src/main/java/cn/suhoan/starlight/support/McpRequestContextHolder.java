package cn.suhoan.starlight.support;

/**
 * MCP 请求级 ThreadLocal 上下文。
 * <p>用于在 transport 安全校验与上下文提取之间传递已认证的 API Key 主体。</p>
 */
public final class McpRequestContextHolder {

    private static final ThreadLocal<McpApiKeyPrincipal> HOLDER = new ThreadLocal<>();

    private McpRequestContextHolder() {
    }

    public static void set(McpApiKeyPrincipal principal) {
        HOLDER.set(principal);
    }

    public static McpApiKeyPrincipal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

