package per.kokabot.provider.divingfish;

import org.springframework.stereotype.Component;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.provider.MaimaiScoreProvider;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DivingFishScoreProvider implements MaimaiScoreProvider {

    private final DivingFishClient client;

    public DivingFishScoreProvider(DivingFishClient client) {
        this.client = client;
    }

    @Override
    public MaimaiProviderType type() {
        return MaimaiProviderType.DIVING_FISH;
    }

    @Override
    public CompletableFuture<B50Result> queryB50(long qqId) {
        return client.queryB50(qqId)
                .thenApply(this::toB50Result);
    }

    private B50Result toB50Result(DivingFishB50ResponseVO response) {
        if (response.charts() == null) {
            throw new IllegalStateException("水鱼响应缺少 charts 字段");
        }

        return new B50Result(
                response.nickname(),
                response.rating(),
                toScoreRecords(response.charts().sd()),
                toScoreRecords(response.charts().dx())
        );
    }

    private List<ScoreRecord> toScoreRecords(
            List<DivingFishB50ResponseVO.Score> scores
    ) {
        if (scores == null) {
            return List.of();
        }

        return scores.stream()
                .map(this::toScoreRecord)
                .toList();
    }

    private ScoreRecord toScoreRecord(DivingFishB50ResponseVO.Score score) {
        return new ScoreRecord(
                score.songId(),
                score.title(),
                score.type(),
                score.levelLabel(),
                score.level(),
                score.ds(),
                score.achievements(),
                score.dxScore(),
                score.ra(),
                0,
                score.fc(),
                score.fs()
        );
    }
}
