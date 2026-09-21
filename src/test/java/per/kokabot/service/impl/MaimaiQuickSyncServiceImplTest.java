package per.kokabot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import per.kokabot.maimai.adapter.MaimaiAdapterClient;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.provider.lxns.LxnsUserScoreClient;
import per.kokabot.service.DivingFishBindingService;
import per.kokabot.service.LxnsOAuthBindingService;
import per.kokabot.service.MaimaiAccountBindingService;
import per.kokabot.service.MaimaiProviderPreferenceService;
import per.kokabot.service.MaimaiQuickSyncService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaimaiQuickSyncServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void synchronizesOnlyTheSelectedDivingFishTarget() throws Exception {
        MaimaiAdapterClient adapterClient = mock(MaimaiAdapterClient.class);
        LxnsUserScoreClient lxnsScoreClient = mock(LxnsUserScoreClient.class);
        DivingFishBindingService fishBinding = mock(
                DivingFishBindingService.class
        );
        MaimaiAccountBindingService maimaiBinding = mock(
                MaimaiAccountBindingService.class
        );
        LxnsOAuthBindingService lxnsBinding = mock(
                LxnsOAuthBindingService.class
        );
        MaimaiProviderPreferenceService preferenceService = mock(
                MaimaiProviderPreferenceService.class
        );
        JsonNode source = score(
                100,
                "dx",
                3,
                100.5,
                900000,
                null,
                null
        );
        when(preferenceService.getProvider(123L))
                .thenReturn(MaimaiProviderType.DIVING_FISH);
        when(fishBinding.isBound(123L)).thenReturn(true);
        when(maimaiBinding.credential(123L)).thenReturn("arcade-user");
        when(adapterClient.arcadeScores("arcade-user"))
                .thenReturn(List.of(source));
        when(fishBinding.importToken(123L)).thenReturn("fish-token");
        when(adapterClient.divingFishScores("fish-token"))
                .thenReturn(List.of());

        MaimaiQuickSyncServiceImpl service = new MaimaiQuickSyncServiceImpl(
                adapterClient,
                lxnsScoreClient,
                fishBinding,
                maimaiBinding,
                lxnsBinding,
                preferenceService,
                Runnable::run
        );
        MaimaiQuickSyncService.SyncResult result = service.sync(123L).join();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.provider()).isEqualTo(MaimaiProviderType.DIVING_FISH);
        assertThat(result.changed()).isEqualTo(1);
        verify(adapterClient).uploadDivingFishScores(
                "fish-token",
                List.of(source)
        );
        verifyNoInteractions(lxnsScoreClient, lxnsBinding);
    }

    @Test
    void skipsIdenticalAndWorseScores() throws Exception {
        JsonNode target = score(100, "dx", 3, 100.5, 900000, 1, 2);
        JsonNode identical = score(100, "dx", 3, 100.5, 900000, 1, 2);
        JsonNode worse = score(100, "dx", 3, 99.0, 800000, null, null);

        assertThat(MaimaiQuickSyncServiceImpl.changedScores(
                List.of(identical, worse),
                List.of(target)
        )).isEmpty();
    }

    @Test
    void uploadsNewScoresAndMergesExistingBadges() throws Exception {
        JsonNode target = score(100, "standard", 4, 100.0, 800000, 3, 4);
        JsonNode improved = score(100, "standard", 4, 100.6, 900000, null, null);
        JsonNode newScore = score(200, "dx", 2, 98.0, 700000, null, null);

        List<JsonNode> changed = MaimaiQuickSyncServiceImpl.changedScores(
                List.of(improved, newScore),
                List.of(target)
        );

        assertThat(changed).hasSize(2);
        assertThat(changed.get(0).path("achievements").asDouble())
                .isEqualTo(100.6);
        assertThat(changed.get(0).path("dx_score").asInt())
                .isEqualTo(900000);
        assertThat(changed.get(0).path("fc").asInt()).isEqualTo(3);
        assertThat(changed.get(0).path("fs").asInt()).isEqualTo(4);
        assertThat(changed.get(1).path("id").asInt()).isEqualTo(200);
    }

    @Test
    void uploadsBadgeImprovementsEvenWhenScoresAreUnchanged() throws Exception {
        JsonNode target = objectMapper.readTree("""
                {
                  "id": 100,
                  "type": "standard",
                  "level_index": 3,
                  "achievements": 100.5,
                  "dx_score": 900000,
                  "fc": "fc",
                  "fs": "fs"
                }
                """);
        JsonNode source = score(
                100,
                "standard",
                3,
                100.5,
                900000,
                1,
                4
        );

        List<JsonNode> changed = MaimaiQuickSyncServiceImpl.changedScores(
                List.of(source),
                List.of(target)
        );

        assertThat(changed).hasSize(1);
        assertThat(changed.get(0).path("fc").asInt()).isEqualTo(1);
        assertThat(changed.get(0).path("fs").asInt()).isEqualTo(4);
    }

    private JsonNode score(
            int id,
            String type,
            int levelIndex,
            double achievements,
            int dxScore,
            Integer fc,
            Integer fs
    ) throws Exception {
        String fcJson = fc == null ? "null" : fc.toString();
        String fsJson = fs == null ? "null" : fs.toString();
        return objectMapper.readTree("""
                {
                  "id": %d,
                  "level": "14",
                  "level_index": %d,
                  "achievements": %s,
                  "fc": %s,
                  "fs": %s,
                  "dx_score": %d,
                  "dx_rating": 0,
                  "play_count": null,
                  "play_time": null,
                  "rate": 0,
                  "type": "%s"
                }
                """.formatted(
                id,
                levelIndex,
                achievements,
                fcJson,
                fsJson,
                dxScore,
                type
        ));
    }
}
