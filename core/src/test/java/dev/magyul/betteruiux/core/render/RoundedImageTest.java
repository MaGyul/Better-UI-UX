package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundedImageTest {
    private static List<DrawCommand.DrawTexture> textures(String style) throws Exception {
        UiDocument d = UiDocument.parse("<screen><image id='i' src='tex.png' width='40' height='40' style='" + style + "'/></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawTexture).map(c -> (DrawCommand.DrawTexture) c).toList();
    }

    @Test
    void plainImageIsOneDraw() throws Exception {
        assertEquals(1, textures("").size());
    }

    @Test
    void roundedImageIsCutIntoRowStrips() throws Exception {
        List<DrawCommand.DrawTexture> t = textures("border-radius: 8");
        assertTrue(t.size() > 1 && t.size() <= 12, "모서리 행 묶음 + 가운데: " + t.size());
        DrawCommand.DrawTexture first = t.get(0), mid = t.get(t.size() / 2);
        assertTrue(first.w() < 40, "맨 위 줄은 모서리 때문에 폭이 좁다: " + first.w());
        assertTrue(mid.w() == 40 || t.stream().anyMatch(x -> x.w() == 40), "가운데는 전체 폭");
        int rows = t.stream().mapToInt(DrawCommand.DrawTexture::h).sum();
        assertEquals(40, rows, "행을 빠짐없이 덮는다");
    }

    @Test
    void imageStaysInsideTheBorder() throws Exception {
        List<DrawCommand.DrawTexture> t = textures("border: 3 solid #FFFFFF");
        assertEquals(1, t.size());
        assertEquals(34, t.get(0).w());
    }
}
