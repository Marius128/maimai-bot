package per.kokabot.maimai.adapter;

public class MaimaiAdapterException extends RuntimeException {

    private final int statusCode;
    private final String details;

    public MaimaiAdapterException(int statusCode, String message) {
        this(statusCode, message, "");
    }

    public MaimaiAdapterException(
            int statusCode,
            String message,
            String details
    ) {
        super(message);
        this.statusCode = statusCode;
        this.details = details == null ? "" : details;
    }

    public MaimaiAdapterException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.details = "";
    }

    public int statusCode() {
        return statusCode;
    }

    public String details() {
        return details;
    }
}
