package kuan.fintech.fintech_user_service.interfaces.rest.request;

public record AddressRequest(
        String line1,
        String line2,
        String city,
        String country
) {
}
