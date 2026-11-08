package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 이미지의 원래 크기를 문서가 쓰는지, 그리고 기본 제공 데모 이미지가 해독되는지. */
class ExternalImageLayoutTest {
    private static final Path IMAGES = Path.of("../backend-mc26/src/main/resources/assets/better_uiux/images");

    @Test
    void autoSizedImagesUseTheirNaturalSizeOnceKnown() throws Exception {
        UiDocument d = UiDocument.parse("<screen><image id=\"a\" src=\"file:x.png\" x=\"10\" y=\"10\"/>"
                + "<image id=\"b\" src=\"file:x.png\" x=\"10\" y=\"80\" width=\"20\" height=\"20\"/></screen>", new FixedMetrics());
        d.layout(300, 200);
        assertEquals(16, d.query("#a").width(), "모르는 동안은 기본 16x16");
        int[][] size = {null};
        d.setImageSizer(src -> src.equals("file:x.png") ? size[0] : null);
        d.layout(300, 200);
        assertEquals(16, d.query("#a").width());
        size[0] = new int[] {96, 48};
        d.requestRelayout();
        d.layout(300, 200);
        assertEquals(96, d.query("#a").width());
        assertEquals(48, d.query("#a").height());
        assertEquals(20, d.query("#b").width(), "크기를 정했으면 그대로");
    }

    @Test
    void bundledDemoImagesDecode() throws Exception {
        ImageDecoder.Decoded png = ImageDecoder.decode(Files.readAllBytes(IMAGES.resolve("demo.png")));
        assertEquals(96, png.width());
        assertEquals(48, png.height());
        ImageDecoder.Decoded gif = ImageDecoder.decode(Files.readAllBytes(IMAGES.resolve("spin.gif")));
        assertEquals(12, gif.frames().size());
        assertTrue(gif.animated());
        assertEquals(48, gif.width());
        assertTrue(gif.totalDurationMs() >= 600, "12프레임 x 60ms");
        assertTrue(!java.util.Arrays.equals(gif.frames().get(0), gif.frames().get(3)), "프레임마다 모양이 다르다");
    }
}
