package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** File 메뉴: 다른 UI 열기, 새 UI 만들기, 저장하지 않은 변경이 있으면 전환하지 않음. */
class EditorFileMenuTest {
    private static final String XML = "<screen><panel width=\"100\" height=\"80\"/></screen>";

    private static final class Host implements EditorHost {
        String switched;
        int created = -1;

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public List<String> screens() { return List.of("alpha", "beta"); }

        @Override public void switchTo(String id) { switched = id; }

        @Override public String createScreen(int kind) {
            created = kind;
            return "new_1";
        }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElse(null);
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static void pick(EditorController e, String label) {
        click(e, 8, 8);                                          // 툴바 첫 버튼 = File
        DrawCommand.DrawText t = find(paint(e), label);
        assertTrue(t != null, label + " 항목이 보여야 한다");
        click(e, t.x() + 2, t.y() + 2);
    }

    @Test
    void opensAnotherScreenAndCreatesNewOnes() throws Exception {
        Host h = new Host();
        EditorController e = new EditorController(h, new FixedMetrics(), XML, 400, 240);
        paint(e);
        pick(e, "열기: beta");
        assertEquals("beta", h.switched);

        h.switched = null;
        pick(e, "새 화면: 대화상자");
        assertEquals(1, h.created);
        assertEquals("new_1", h.switched);
    }

    @Test
    void dirtyEditorRefusesToSwitch() throws Exception {
        Host h = new Host();
        EditorController e = new EditorController(h, new FixedMetrics(), XML, 400, 240);
        e.add("label");
        paint(e);
        pick(e, "열기: alpha");
        assertNull(h.switched);
        assertEquals(-1, h.created);
        assertTrue(e.status().contains("저장"));
    }
}
