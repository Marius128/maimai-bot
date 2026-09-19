package per.kokabot.provider.lxns.oauth;

public class LxnsOAuthException extends RuntimeException {

    private final int statusCode;

    public LxnsOAuthException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public LxnsOAuthException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}
