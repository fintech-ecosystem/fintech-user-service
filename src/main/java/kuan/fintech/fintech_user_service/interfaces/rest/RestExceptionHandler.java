package kuan.fintech.fintech_user_service.interfaces.rest;

import kuan.fintech.fintech_user_service.domain.error.UserErrorCode;
import kuan.fintech.fintech_user_service.domain.error.UserServiceException;
import kuan.fintech.fintech_user_service.interfaces.rest.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(UserServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserServiceException(
            UserServiceException ex,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ResponseEntity.status(status(ex.getCode()))
                .body(ApiResponse.failure(ex.getCode().name(), ex.getMessage(), correlationId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(UserErrorCode.USER_INVALID_REQUEST.name(), "Invalid request body", correlationId));
    }

    private HttpStatus status(UserErrorCode code) {
        return switch (code) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case USER_INVALID_PHONE_NUMBER, USER_INVALID_DATE_OF_BIRTH, USER_STATUS_REASON_REQUIRED,
                    USER_INVALID_STATUS_TRANSITION, USER_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        };
    }
}
