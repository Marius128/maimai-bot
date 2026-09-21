package per.kokabot.maimai.image;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class B50ImageRenderer {

    private static final int B35_START_Y = 190;
    private static final int B15_START_Y = 1_940;
    private static final int CARD_HEIGHT = 220;
    private static final int GAP = 20;
    private static final String TEMPLATE_PATH = "templates/b50.svg";
    private static final String BADGE_PATH = "templates/b50-badges/";

    private final String template;
    private final Map<String, String> badgeDataUris;

    public B50ImageRenderer() {
        this.template = loadTemplate();
        this.badgeDataUris = loadBadgeDataUris();
    }

    public byte[] render(B50Result result) {
        return render(result, Map.of());
    }

    public byte[] render(
            B50Result result,
            Map<String, String> jacketDataUris
    ) {
        String svg = template
                .replace("{{PLAYER_NAME}}", escapeXml(safeText(result.playerName())))
                .replace("{{RATING}}", String.valueOf(result.rating()))
                .replace("{{B35_CARDS}}", renderCards(
                        result.best35(), 32, B35_START_Y, 5, 268, 35,
                        jacketDataUris
                ))
                .replace("{{B15_CARDS}}", renderCards(
                        result.best15(), 32, B15_START_Y, 5, 268, 15,
                        jacketDataUris
                ));

        PNGTranscoder transcoder = new PNGTranscoder();
        TranscoderInput input = new TranscoderInput(new StringReader(svg));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            transcoder.transcode(input, new TranscoderOutput(output));
            return output.toByteArray();
        } catch (TranscoderException | IOException error) {
            throw new IllegalStateException("B50 图片生成失败", error);
        }
    }

    private String renderCards(
            List<ScoreRecord> scores,
            int startX,
            int startY,
            int columns,
            int cardWidth,
            int slotCount,
            Map<String, String> jacketDataUris
    ) {
        List<ScoreRecord> safeScores = scores == null ? List.of() : scores;
        StringBuilder cards = new StringBuilder(slotCount * 700);

        for (int index = 0; index < slotCount; index++) {
            int row = index / columns;
            int column = index % columns;
            int x = startX + column * (cardWidth + GAP);
            int y = startY + row * (CARD_HEIGHT + GAP);
            ScoreRecord score = index < safeScores.size()
                    ? safeScores.get(index)
                    : null;

            cards.append(renderCard(
                    score,
                    index + 1,
                    x,
                    y,
                    cardWidth,
                    jacketDataUris
            ));
        }
        return cards.toString();
    }

    private String renderCard(
            ScoreRecord score,
            int rank,
            int x,
            int y,
            int width,
            Map<String, String> jacketDataUris
    ) {
        StringBuilder card = new StringBuilder(1_200);
        String clipId = "card-" + x + "-" + y;
        String infoClipId = clipId + "-info";

        card.append("<g>")
                .append("<defs><clipPath id=\"").append(clipId)
                .append("\"><rect x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" width=\"").append(width)
                .append("\" height=\"").append(CARD_HEIGHT)
                .append("\" rx=\"24\"/></clipPath>")
                .append("<clipPath id=\"").append(infoClipId)
                .append("\"><rect x=\"").append(x + 16)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"178\" height=\"24\" rx=\"12\"/>")
                .append("</clipPath></defs>")
                .append("<rect x=\"").append(x + 2)
                .append("\" y=\"").append(y + 5)
                .append("\" width=\"").append(width)
                .append("\" height=\"").append(CARD_HEIGHT)
                .append("\" rx=\"24\" fill=\"#050713\" fill-opacity=\"0.40\"/>")
                .append("<rect x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" width=\"").append(width)
                .append("\" height=\"").append(CARD_HEIGHT)
                .append("\" rx=\"24\" fill=\"url(#emptyCover)\"/>");

        if (score == null) {
            card.append("<rect x=\"").append(x + 6)
                    .append("\" y=\"").append(y + 6)
                    .append("\" width=\"").append(width - 12)
                    .append("\" height=\"").append(CARD_HEIGHT - 12)
                    .append("\" rx=\"20\" fill=\"none\"")
                    .append(" stroke=\"#ffffff\" stroke-opacity=\"0.14\"/>");
            appendText(
                    card,
                    "empty",
                    x + width / 2,
                    y + 116,
                    "#" + rank + "   --"
            );
            return card.append("</g>").toString();
        }

        String jacketDataUri = jacketDataUris.get(score.songId());

        if (jacketDataUri == null) {
            appendText(
                    card,
                    "cover-placeholder",
                    x + width / 2,
                    y + 84,
                    "♪"
            );
        } else {
            card.append("<image x=\"").append(x)
                    .append("\" y=\"").append(y)
                    .append("\" width=\"").append(width)
                    .append("\" height=\"").append(CARD_HEIGHT)
                    .append("\" preserveAspectRatio=\"xMidYMid slice\"")
                    .append(" clip-path=\"url(#").append(clipId)
                    .append(")\" xlink:href=\"")
                    .append(jacketDataUri)
                    .append("\"/>");
        }

        card.append("<rect x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" width=\"").append(width)
                .append("\" height=\"").append(CARD_HEIGHT)
                .append("\" rx=\"24\" fill=\"url(#coverShade)\"/>")
                .append("<rect x=\"").append(x + 6)
                .append("\" y=\"").append(y + 108)
                .append("\" width=\"").append(width - 12)
                .append("\" height=\"106\" rx=\"24\"")
                .append(" fill=\"url(#glassPanel)\"")
                .append(" stroke=\"#ffffff\" stroke-opacity=\"0.32\"/>")
                .append("<rect x=\"").append(x + 9)
                .append("\" y=\"").append(y + 8)
                .append("\" width=\"42\" height=\"23\" rx=\"11.5\"")
                .append(" fill=\"#080b18\" fill-opacity=\"0.64\"")
                .append(" stroke=\"#ffffff\" stroke-opacity=\"0.25\"/>")
                .append("<g clip-path=\"url(#").append(infoClipId)
                .append(")\" filter=\"url(#chipShadow)\">")
                .append("<rect x=\"").append(x + 16)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"40\" height=\"24\" fill=\"")
                .append(chartTypeFill(score.chartType()))
                .append("\" fill-opacity=\"0.90\"/>")
                .append("<rect x=\"").append(x + 56)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"40\" height=\"24\"")
                .append(" fill=\"url(#chipRed)\" fill-opacity=\"0.90\"/>")
                .append("<rect x=\"").append(x + 96)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"40\" height=\"24\"")
                .append(" fill=\"url(#chipOrange)\" fill-opacity=\"0.90\"/>")
                .append("<rect x=\"").append(x + 136)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"58\" height=\"24\"")
                .append(" fill=\"url(#chipGlass)\" fill-opacity=\"0.90\"/>")
                .append("<rect x=\"").append(x + 16)
                .append("\" y=\"").append(y + 185)
                .append("\" width=\"178\" height=\"24\" rx=\"12\"")
                .append(" fill=\"none\" stroke=\"#ffffff\" stroke-opacity=\"0.28\"/>")
                .append("</g>");

        int contentX = x + 18;
        int titleUnits = 23;
        String title = truncate(safeText(score.songName()), titleUnits);
        appendText(card, "card-rank", x + 30, y + 25, "#" + rank);
        appendText(card, "card-title", contentX, y + 131, title);
        appendText(
                card,
                "song-id",
                x + width - 15,
                y + 131,
                "ID " + score.songId()
        );
        appendText(
                card,
                "achievement",
                contentX,
                y + 177,
                String.format(Locale.ROOT, "%.4f%%", score.achievement())
        );
        appendText(card, "info-chip", x + 36, y + 201,
                chartTypeLabel(score.chartType()));
        appendText(card, "info-chip", x + 76, y + 201,
                formatConstant(score.chartConstant()));
        appendText(card, "info-chip", x + 116, y + 201,
                String.valueOf(score.rating()));
        appendDxStars(card, x + 136, y + 190, score.dxStar());
        appendImage(
                card,
                x + 197,
                y + 179,
                31,
                playBonusAsset(score.fullCombo())
        );
        appendImage(
                card,
                x + 229,
                y + 179,
                31,
                playBonusAsset(score.fullSync())
        );

        return card.append("</g>").toString();
    }

    private void appendText(
            StringBuilder target,
            String cssClass,
            int x,
            int y,
            String text
    ) {
        target.append("<text class=\"").append(cssClass)
                .append("\" x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\">").append(escapeXml(text)).append("</text>");
    }

    private String chartTypeFill(String chartType) {
        return "DX".equalsIgnoreCase(chartType)
                ? "url(#chipPurple)"
                : "url(#chipBlue)";
    }

    private String chartTypeLabel(String chartType) {
        return "DX".equalsIgnoreCase(chartType) ? "DX" : "标准";
    }

    private String formatConstant(double chartConstant) {
        return chartConstant > 0
                ? String.format(Locale.ROOT, "%.1f", chartConstant)
                : "—";
    }

    private void appendDxStars(
            StringBuilder target,
            int x,
            int y,
            int dxStar
    ) {
        if (dxStar <= 0) {
            appendText(target, "star-empty", x + 29, y + 11, "—");
            return;
        }
        int starCount = Math.min(5, dxStar);
        int tier = dxStar <= 2 ? 1 : dxStar <= 4 ? 2 : 3;
        String asset = badgeDataUris.get("dx-star-" + tier);
        int starSize = 11;
        int step = 10;
        int totalWidth = starSize + (starCount - 1) * step;
        int startX = x + (58 - totalWidth) / 2;
        for (int index = 0; index < starCount; index++) {
            appendImage(target, startX + index * step, y, starSize, asset);
        }
    }

    private String playBonusAsset(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return badgeDataUris.get(
                "bonus-" + code.toLowerCase(Locale.ROOT)
        );
    }

    private void appendImage(
            StringBuilder target,
            int x,
            int y,
            int size,
            String dataUri
    ) {
        if (dataUri == null || dataUri.isBlank()) {
            return;
        }
        target.append("<image x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" width=\"").append(size)
                .append("\" height=\"").append(size)
                .append("\" preserveAspectRatio=\"xMidYMid meet\"")
                .append(" filter=\"url(#iconShadow)\"")
                .append(" xlink:href=\"").append(dataUri).append("\"/>");
    }

    private String truncate(String value, int maxUnits) {
        int units = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int nextUnits = units + (codePoint <= 0x7f ? 1 : 2);
            if (nextUnits > maxUnits) {
                return value.substring(0, end) + "…";
            }
            units = nextUnits;
            end += Character.charCount(codePoint);
        }
        return value;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private String loadTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("无法读取 B50 SVG 模板", error);
        }
    }

    private Map<String, String> loadBadgeDataUris() {
        String[] names = {
                "bonus-blank", "bonus-fc", "bonus-fcp",
                "bonus-ap", "bonus-app", "bonus-fs", "bonus-fsp",
                "bonus-fsd", "bonus-fsdp", "bonus-sync",
                "dx-star-1", "dx-star-2", "dx-star-3"
        };
        Map<String, String> assets = new HashMap<>();
        for (String name : names) {
            try {
                byte[] bytes = new ClassPathResource(
                        BADGE_PATH + name + ".png"
                ).getContentAsByteArray();
                assets.put(
                        name,
                        "data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(bytes)
                );
            } catch (IOException error) {
                throw new IllegalStateException(
                        "无法读取 B50 徽章素材：" + name,
                        error
                );
            }
        }
        return Map.copyOf(assets);
    }
}
