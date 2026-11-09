package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 속성 패널의 "스타일 규칙" 구역: 적용된 규칙을 보여 주고 값을 고친다. */
class EditorStyleRulesTest {
    private static final int W = 700, H = 700;
    private static final String XML = "<screen><style>.card { padding: 8; }</style><panel id=\"a\" class=\"card\" width=\"80\" height=\"40\"/></screen>";

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).reduce((x, y) -> y).orElse(null);   // 같은 글자가 여럿이면 마지막(스타일 구역은 맨 아래)
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static EditorController selected() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), XML, W, H);
        int idx = e.document().layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow().node.sourceIndex();
        e.select(idx);
        paint(e);
        return e;
    }

    @Test
    void showsMatchingRuleAndEditsItsValue() throws Exception {
        EditorController e = selected();
        DrawList l = paint(e);
        assertTrue(find(l, "- 스타일 규칙") != null, "구역 제목");
        assertTrue(find(l, "- .card") != null, "선택자");
        DrawCommand.DrawText prop = find(l, "padding");
        assertTrue(prop != null, "선언 이름");
        click(e, W - EditorController.RIGHT_W + 60 + 5, prop.y() + 2);          // 값 칸
        assertTrue(e.isEditingField());
        for (int i = 0; i < 4; i++) e.keyPressed(EditorKey.BACKSPACE, false, false);
        e.charTyped('1');
        e.charTyped('6');
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains(".card { padding: 16; }"), e.text());
        assertTrue(e.isDirty());
    }

    @Test
    void addRowAddsADeclaration() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        assertTrue(add != null);
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "color: #FFFFFF".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("padding: 8; color: #FFFFFF;"), e.text());
    }

    @Test
    void invalidAddShowsAnErrorAndChangesNothing() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "엉터리".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertEquals(XML, e.text());
        assertTrue(e.status().contains("속성"));
    }
}
