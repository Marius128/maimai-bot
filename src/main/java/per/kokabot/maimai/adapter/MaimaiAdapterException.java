package per.kokabot.maimai.adapter;

public class MaimaiAdapterException extends RuntimeException {

    private final int statusCode;

    public MaimaiAdapterException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public MaimaiAdapterException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}
