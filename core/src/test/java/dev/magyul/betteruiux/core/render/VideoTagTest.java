package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** video 태그: image 처럼 src 를 그리고 영상의 원래 크기로 놓인다. 기본 제공 demo.mp4 가 해독되는지도 확인한다. */
class VideoTagTest {
    @Test
    void videoTagDrawsItsSourceAndUsesTheNaturalSize() throws Exception {
        UiDocument d = UiDocument.parse("<screen><video id=\"v\" src=\"file:demo.mp4\" x=\"10\" y=\"10\"/>"
                + "<video id=\"w\" src=\"file:demo.mp4\" x=\"10\" y=\"100\" width=\"64\" height=\"36\"/></screen>", new FixedMetrics());
        d.setImageSizer(src -> new int[] {192, 108});
        d.layout(400, 300);
        assertEquals(192, d.query("#v").width());
        assertEquals(108, d.query("#v").height());
        assertEquals(64, d.query("#w").width());
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        long draws = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawTexture t && t.texture().equals("file:demo.mp4")).count();
        assertEquals(2, draws);
        assertEquals(java.util.List.of(), d.warnings());
    }

    @Test
    void bundledDemoVideoDecodes() throws Exception {
        byte[] data = Files.readAllBytes(Path.of("../backend-mc26/src/main/resources/assets/better_uiux/images/demo.mp4"));
        try (VideoSource v = VideoSource.open(data, 960)) {
            assertEquals(192, v.info().width());
            assertEquals(108, v.info().height());
            int n = 0;
            while (v.next() != null) n++;
            assertTrue(n >= 50, "4초 x 15fps 근처: " + n);
        }
    }
}
