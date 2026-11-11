package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundedClipTest {
    private static List<DrawCommand> paint(String style) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>#a { width: 40; height: 40; background: #202020; " + style + " } #k { width: 40; height: 40; background: #FF0000 }</style>"
                + "<panel id='a'><panel id='k'/></panel></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands();
    }

    private static long count(List<DrawCommand> c, Class<?> type) { return c.stream().filter(type::isInstance).count(); }

    @Test
    void plainOverflowHiddenUsesOneRectClip() throws Exception {
        List<DrawCommand> c = paint("overflow: hidden");
        assertEquals(1, count(c, DrawCommand.PushClip.class));
    }

    @Test
    void roundedOverflowClipsChildrenPerCornerStrip() throws Exception {
        List<DrawCommand> c = paint("overflow: hidden; border-radius: 6");
        long clips = count(c, DrawCommand.PushClip.class);
        assertTrue(clips > 1 && clips <= BoxPainter.MAX_CLIP_STRIPS, "모서리 행 묶음마다 클립: " + clips);
        assertEquals(clips, count(c, DrawCommand.PopClip.class), "push/pop 짝이 맞는다");
        assertEquals(clips, c.stream().filter(x -> x instanceof DrawCommand.FillRect r && r.argb() == 0xFFFF0000).count(), "자식이 클립마다 다시 그려진다");
        DrawCommand.PushClip first = (DrawCommand.PushClip) c.stream().filter(x -> x instanceof DrawCommand.PushClip).findFirst().orElseThrow();
        assertTrue(first.x() > 0 && first.w() < 40, "맨 위 줄 클립은 모서리 때문에 좁다: " + first);
    }

    @Test
    void roundedWithoutOverflowDoesNotClip() throws Exception {
        assertEquals(0, count(paint("border-radius: 6"), DrawCommand.PushClip.class));
    }

    @Test
    void hugeRadiusMergesStripsAndStaysInsideTheShape() throws Exception {
        List<DrawCommand> c = paint("overflow: hidden; border-radius: 20");
        long clips = count(c, DrawCommand.PushClip.class);
        assertTrue(clips > 1 && clips <= BoxPainter.MAX_CLIP_STRIPS, "합쳐서 상한 이하로: " + clips);
        int[][] sp = BoxPainter.spans(40, 40, new BoxPainter.Radii(20, 20, 20, 20));
        for (DrawCommand x : c) {
            if (!(x instanceof DrawCommand.PushClip p)) continue;
            for (int row = p.y(); row < p.y() + p.h(); row++) {
                assertTrue(p.x() >= sp[0][row] && p.x() + p.w() <= 40 - sp[1][row], "둥근 모양 밖으로 새면 안 된다: " + p + " row " + row);
            }
        }
    }

    @Test
    void borderMovesTheClipInside() throws Exception {
        List<DrawCommand> c = paint("overflow: hidden; border-radius: 6; border: 3 solid #FFFFFF");
        DrawCommand.PushClip mid = (DrawCommand.PushClip) c.stream().filter(x -> x instanceof DrawCommand.PushClip p && p.w() == 34).findFirst().orElseThrow();
        assertEquals(3, mid.x(), "테두리 안쪽(폭 34)에 걸린다");
    }
}
