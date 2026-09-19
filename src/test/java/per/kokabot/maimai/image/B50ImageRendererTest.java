package per.kokabot.maimai.image;

import org.junit.jupiter.api.Test;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class B50ImageRendererTest {

    @Test
    void rendersCompleteB50AsPng() throws Exception {
        B50Result result = new B50Result(
                "测试玩家 KokaBot",
                15234,
                scores(35, "旧曲"),
                scores(15, "新曲")
        );

        BufferedImage cover = new BufferedImage(
                16,
                16,
                BufferedImage.TYPE_INT_RGB
        );
        ByteArrayOutputStream coverOutput = new ByteArrayOutputStream();
        ImageIO.write(cover, "png", coverOutput);
        String coverDataUri = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(
                        coverOutput.toByteArray()
                );

        byte[] png = new B50ImageRenderer().render(
                result,
                Map.of("1000", coverDataUri)
        );
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertNotNull(image);
        assertEquals(1500, image.getWidth());
        assertEquals(2720, image.getHeight());
        assertTrue(png.length > 10_000);

        Path output = Path.of("target", "b50-preview.png");
        Files.createDirectories(output.getParent());
        Files.write(output, png);
    }

    private List<ScoreRecord> scores(int count, String prefix) {
        String[] difficulties = {
                "Basic", "Advanced", "Expert", "Master", "Re:Master"
        };

        return IntStream.range(0, count)
                .mapToObj(index -> new ScoreRecord(
                        String.valueOf(1000 + index),
                        prefix + "示例歌曲 " + (index + 1),
                        index % 2 == 0 ? "DX" : "SD",
                        difficulties[index % difficulties.length],
                        index % 3 == 0 ? "14+" : "14",
                        13.0 + index / 10.0,
                        100.0000 + index * 0.01,
                        2800 + index,
                        300 - index,
                        index % 6,
                        index % 4 == 0 ? "app" : "fc",
                        index % 3 == 0 ? "fsdp" : "fs"
                ))
                .toList();
    }
}
