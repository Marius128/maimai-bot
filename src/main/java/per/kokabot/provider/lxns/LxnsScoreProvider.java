package per.kokabot.provider.lxns;

import org.springframework.stereotype.Component;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.provider.MaimaiScoreProvider;
import per.kokabot.provider.lxns.response.LxnsB50DataVO;
import per.kokabot.provider.lxns.response.LxnsB50ResponseVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class LxnsScoreProvider implements MaimaiScoreProvider {

    private static final List<String> DIFFICULTIES = List.of(
            "Basic", "Advanced", "Expert", "Master", "Re:Master"
    );

    private final LxnsClient client;

    public LxnsScoreProvider(LxnsClient client) {
        this.client = client;
    }

    @Override
    public MaimaiProviderType type() {
        return MaimaiProviderType.LXNS;
    }

    @Override
    public CompletableFuture<B50Result> queryB50(long qqId) {
        return client.queryB50(qqId)
                .thenApply(this::toB50Result);
    }

    private B50Result toB50Result(LxnsB50DataVO data) {
        return new B50Result(
                data.player().name(),
                data.player().rating(),
                toScoreRecords(data.bests().standard()),
                toScoreRecords(data.bests().dx())
        );
    }

    private List<ScoreRecord> toScoreRecords(
            List<LxnsB50ResponseVO.Score> scores
    ) {
        if (scores == null) {
            return List.of();
        }

        return scores.stream()
                .map(this::toScoreRecord)
                .toList();
    }

    private ScoreRecord toScoreRecord(LxnsB50ResponseVO.Score score) {
        return new ScoreRecord(
                String.valueOf(score.id()),
                score.songName(),
                normalizeChartType(score.type()),
                difficultyName(score.levelIndex()),
                score.level(),
                0,
                score.achievements(),
                score.dxScore(),
                (int) Math.floor(score.dxRating()),
                score.dxStar(),
                score.fc(),
                score.fs()
        );
    }

    private String normalizeChartType(String type) {
        if (type == null) {
            return "";
        }
        return switch (type.toLowerCase()) {
            case "standard" -> "SD";
            case "dx" -> "DX";
            default -> type.toUpperCase();
        };
    }

    private String difficultyName(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= DIFFICULTIES.size()) {
            return "Unknown";
        }
        return DIFFICULTIES.get(levelIndex);
    }
}
