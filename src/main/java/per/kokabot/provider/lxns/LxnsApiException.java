package per.kokabot.provider.lxns;

public class LxnsApiException extends RuntimeException {

    private final int statusCode;

    public LxnsApiException(int statusCode, String message) {
        super(message == null || message.isBlank()
                ? "落雪接口请求失败，HTTP " + statusCode
                : message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
