package per.kokabot.provider.divingfish;

public class DivingFishApiException extends RuntimeException {

    private final int statusCode;

    public DivingFishApiException(int statusCode) {
        super("水鱼接口请求失败，HTTP " + statusCode);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
