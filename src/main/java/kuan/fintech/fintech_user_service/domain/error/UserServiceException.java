package kuan.fintech.fintech_user_service.domain.error;

public class UserServiceException extends RuntimeException {
    private final UserErrorCode code;

    public UserServiceException(UserErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public UserErrorCode getCode() {
        return code;
    }
}
