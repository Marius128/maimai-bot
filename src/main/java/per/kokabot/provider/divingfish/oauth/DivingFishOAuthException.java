package per.kokabot.provider.divingfish.oauth;

public class DivingFishOAuthException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public DivingFishOAuthException(
            int statusCode,
            String errorCode,
            String message
    ) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "" : errorCode;
    }

    public DivingFishOAuthException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = "";
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean consentRequired() {
        return "consent_required".equals(errorCode);
    }
}
