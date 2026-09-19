package per.kokabot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.maimai.adapter.MaimaiAdapterClient;
import per.kokabot.provider.lxns.LxnsUserScoreClient;
import per.kokabot.service.LxnsOAuthBindingService;
import per.kokabot.service.MaimaiAccountBindingService;
import per.kokabot.service.MaimaiQuickSyncService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class MaimaiQuickSyncServiceImpl implements MaimaiQuickSyncService {

    private static final double ACHIEVEMENT_EPSILON = 0.000001D;

    private final MaimaiAdapterClient adapterClient;
    private final LxnsUserScoreClient lxnsScoreClient;
    private final MaimaiAccountBindingService maimaiBindingService;
    private final LxnsOAuthBindingService lxnsBindingService;
    private final Executor syncExecutor;

    public MaimaiQuickSyncServiceImpl(
            MaimaiAdapterClient adapterClient,
            LxnsUserScoreClient lxnsScoreClient,
            MaimaiAccountBindingService maimaiBindingService,
            LxnsOAuthBindingService lxnsBindingService,
            @Qualifier("maimaiSyncExecutor") Executor syncExecutor
    ) {
        this.adapterClient = adapterClient;
        this.lxnsScoreClient = lxnsScoreClient;
        this.maimaiBindingService = maimaiBindingService;
        this.lxnsBindingService = lxnsBindingService;
        this.syncExecutor = syncExecutor;
    }

    @Override
    public CompletableFuture<SyncResult> sync(long qqId) {
        return CompletableFuture.supplyAsync(() -> syncNow(qqId), syncExecutor);
    }

    private SyncResult syncNow(long qqId) {
        String arcadeCredential = maimaiBindingService.credential(qqId);
        String accessToken = lxnsBindingService.validAccessToken(qqId);

        List<JsonNode> arcadeScores = adapterClient.arcadeScores(
                arcadeCredential
        );
        List<JsonNode> lxnsScores = lxnsScoreClient.scores(accessToken);
        List<JsonNode> changed = changedScores(arcadeScores, lxnsScores);
        lxnsScoreClient.upload(accessToken, changed);
        return new SyncResult(arcadeScores.size(), changed.size());
    }

    static List<JsonNode> changedScores(
            List<JsonNode> source,
            List<JsonNode> target
    ) {
        Map<ScoreKey, JsonNode> targetByKey = new HashMap<>();
        for (JsonNode score : target) {
            targetByKey.put(ScoreKey.from(score), score);
        }

        List<JsonNode> changed = new ArrayList<>();
        for (JsonNode sourceScore : source) {
            JsonNode targetScore = targetByKey.get(ScoreKey.from(sourceScore));
            if (targetScore == null) {
                changed.add(sourceScore.deepCopy());
                continue;
            }
            if (!hasImprovement(sourceScore, targetScore)) {
                continue;
            }
            changed.add(mergeBest(sourceScore, targetScore));
        }
        return changed;
    }

    private static boolean hasImprovement(
            JsonNode source,
            JsonNode target
    ) {
        return decimal(source, "achievements")
                > decimal(target, "achievements") + ACHIEVEMENT_EPSILON
                || integer(source, "dx_score") > integer(target, "dx_score");
    }

    private static JsonNode mergeBest(JsonNode source, JsonNode target) {
        ObjectNode merged = source.deepCopy();
        putMaximumDecimal(merged, target, "achievements");
        putMaximumInteger(merged, target, "dx_score");
        preserveField(merged, target, "fc");
        preserveField(merged, target, "fs");
        return merged;
    }

    private static void preserveField(
            ObjectNode merged,
            JsonNode target,
            String field
    ) {
        JsonNode current = merged.get(field);
        JsonNode existing = target.get(field);
        if ((current == null || current.isNull())
                && existing != null && !existing.isNull()) {
            merged.set(field, existing.deepCopy());
        }
    }

    private static void putMaximumDecimal(
            ObjectNode merged,
            JsonNode other,
            String field
    ) {
        double value = Math.max(
                decimal(merged, field),
                decimal(other, field)
        );
        merged.put(field, value);
    }

    private static void putMaximumInteger(
            ObjectNode merged,
            JsonNode other,
            String field
    ) {
        int value = Math.max(integer(merged, field), integer(other, field));
        if (value < 0) {
            merged.putNull(field);
        } else {
            merged.put(field, value);
        }
    }

    private static double decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : -1D;
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() || value.isLong() ? value.asInt() : -1;
    }

    private record ScoreKey(int id, String type, int levelIndex) {

        private static ScoreKey from(JsonNode score) {
            return new ScoreKey(
                    score.path("id").asInt(),
                    score.path("type").asText(),
                    score.path("level_index").asInt()
            );
        }
    }
}
