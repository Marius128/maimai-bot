package per.kokabot.arcade;

public enum ArcadeVenue {

    GUANG_DA("光大", "g"),
    YONG_LI("永利", "y");

    private final String displayName;
    private final String shortCode;

    ArcadeVenue(String displayName, String shortCode) {
        this.displayName = displayName;
        this.shortCode = shortCode;
    }

    public String displayName() {
        return displayName;
    }

    public String shortCode() {
        return shortCode;
    }
}
