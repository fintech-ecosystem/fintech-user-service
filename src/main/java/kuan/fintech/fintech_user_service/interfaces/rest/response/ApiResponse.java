package kuan.fintech.fintech_user_service.interfaces.rest.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String correlationId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId, Instant.now());
    }

    public static <T> ApiResponse<T> failure(String code, String message, String correlationId) {
        return new ApiResponse<>(false, null, new ApiError(code, message), correlationId, Instant.now());
    }
}
