package cn.suhoan.starlight.dto;

/**
 * 统一 API 响应封装。
 * <p>所有接口返回值均使用此格式，包含 success 标志、data 数据和 message 消息。</p>
 *
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    /**
     * 构建成功响应（带数据）。
     *
     * @param data 响应数据
     * @return 成功的 ApiResponse
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 构建成功响应（仅消息，无数据）。
     *
     * @param message 成功消息
     * @return 成功的 ApiResponse
     */
    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, null, message);
    }

    /**
     * 构建失败响应。
     *
     * @param message 错误消息
     * @return 失败的 ApiResponse
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}

