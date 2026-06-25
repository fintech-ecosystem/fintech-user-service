package kuan.fintech.fintech_user_service.interfaces.rest.request;

import java.util.UUID;

public record StatusChangeRequest(
        String reason,
        UUID changedBy
) {
}
