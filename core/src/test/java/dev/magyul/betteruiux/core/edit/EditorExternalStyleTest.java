package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 속성 패널에서 {@code <link>} 로 불러온 외부 스타일시트(.uicss)의 규칙을 고친다. */
class EditorExternalStyleTest {
    private static final int W = 700, H = 700;
    private static final String XML = "<screen><link rel=\"style\" href=\"t.uicss\"/><panel id=\"a\" class=\"card\" width=\"80\" height=\"40\"/></screen>";

    private static final class Host implements EditorHost {
        final Map<String, String> files = new HashMap<>(Map.of("t.uicss", ".card { padding: 8; }\n"));

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.uiml"; }

        @Override public String readFile(String name) { return files.get(name); }

        @Override public void writeFile(String name, String text) { files.put(name, text); }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).reduce((x, y) -> y).orElse(null);
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static EditorController selected(Host host) throws Exception {
        EditorController e = new EditorController(host, new FixedMetrics(), XML, W, H);
        int idx = e.document().layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow().node.sourceIndex();
        e.select(idx);
        paint(e);
        return e;
    }

    private static void type(EditorController e, String s) {
        for (char c : s.toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
    }

    private static void editPadding(EditorController e, String value) {
        DrawCommand.DrawText prop = find(paint(e), "padding");
        click(e, W - EditorController.RIGHT_W + 60 + 5, prop.y() + 2);
        for (int i = 0; i < 4; i++) e.keyPressed(EditorKey.BACKSPACE, false, false);
        type(e, value);
    }

    @Test
    void showsTheLinkedSheetAndEditsItsFile() throws Exception {
        Host host = new Host();
        EditorController e = selected(host);
        DrawList l = paint(e);
        assertTrue(find(l, "- 스타일시트: t.uicss") != null, "외부 스타일시트 구역");
        assertTrue(find(l, "- .card") != null, "규칙 선택자");
        editPadding(e, "16");
        assertEquals(".card { padding: 16; }\n", host.files.get("t.uicss"));
        assertEquals(XML, e.text(), "문서 XML 은 그대로");
        assertFalse(e.isDirty(), "외부 파일 편집은 문서를 더럽히지 않는다");
    }

    @Test
    void undoAndRedoRestoreTheFile() throws Exception {
        Host host = new Host();
        EditorController e = selected(host);
        editPadding(e, "16");
        e.undo();
        assertEquals(".card { padding: 8; }\n", host.files.get("t.uicss"));
        e.redo();
        assertEquals(".card { padding: 16; }\n", host.files.get("t.uicss"));
    }

    @Test
    void undoFollowsTheOrderOfMixedEdits() throws Exception {
        Host host = new Host();
        EditorController e = selected(host);
        editPadding(e, "16");                                    // 1) 파일 편집
        e.alignSelected("center");                               // 2) 문서 편집
        String afterDoc = e.text();
        assertTrue(!afterDoc.equals(XML));
        e.undo();                                                // 문서 편집부터 되돌린다
        assertEquals(XML, e.text());
        assertEquals(".card { padding: 16; }\n", host.files.get("t.uicss"));
        e.undo();                                                // 그다음 파일 편집
        assertEquals(".card { padding: 8; }\n", host.files.get("t.uicss"));
        e.redo();
        assertEquals(".card { padding: 16; }\n", host.files.get("t.uicss"));
        e.redo();
        assertEquals(afterDoc, e.text());
    }

    @Test
    void newRuleIsAppendedToTheFile() throws Exception {
        Host host = new Host();
        host.files.put("t.uicss", ".other { color: #FFFFFF; }\n");     // 맞는 규칙이 없는 시트
        EditorController e = selected(host);
        DrawCommand.DrawText add = find(paint(e), "+ 새 규칙");
        assertTrue(add != null);
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        type(e, "padding: 4");
        assertEquals(".other { color: #FFFFFF; }\n#a { padding: 4; }\n", host.files.get("t.uicss"));
    }
}
