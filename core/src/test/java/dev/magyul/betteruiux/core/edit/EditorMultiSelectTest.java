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
import java.util.List;
import org.junit.jupiter.api.Test;

/** 여러 개 선택(Ctrl/Shift 클릭, 사각형 선택)과 함께 이동/삭제/복제/정렬, 구조 트리 검색. */
class EditorMultiSelectTest {
    private static final int W = 700, H = 300;
    private static final String XML = """
        <screen>
          <panel id="a" x="150" y="40" width="50" height="30"/>
          <panel id="b" x="250" y="40" width="50" height="30"/>
          <panel id="c" x="150" y="120" width="50" height="30"/>
        </screen>""";

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.uiml"; }
    }

    private static EditorController editor(String xml) throws Exception {
        return new EditorController(new Host(), new FixedMetrics(), xml, W, H);
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static void click(EditorController e, int x, int y, boolean ctrl) {
        e.setModifiers(ctrl, false);
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
        e.setModifiers(false, false);
    }

    private static String attr(EditorController e, int idx, String n) {
        XmlSource s = XmlSource.parse(e.text());
        return s.attrValue(s.element(idx), n);
    }

    @Test
    void ctrlClickAddsAndRemovesFromTheSelection() throws Exception {
        EditorController e = editor(XML);
        click(e, 160, 50, false);
        click(e, 260, 50, true);
        assertEquals(List.of(1, 2), e.selection());
        click(e, 160, 130, true);
        assertEquals(List.of(1, 2, 3), e.selection());
        click(e, 260, 50, true);
        assertEquals(List.of(1, 3), e.selection(), "다시 Ctrl+클릭하면 선택에서 빠진다");
        click(e, 160, 50, false);
        assertEquals(List.of(1), e.selection(), "그냥 클릭하면 하나만 남는다");
    }

    @Test
    void rubberBandSelectsEverythingFullyInside() throws Exception {
        EditorController e = editor(XML);
        paint(e);
        e.mouseMoved(145, 35);
        e.mousePressed(145, 35, 0);
        for (int i = 1; i <= 6; i++) e.mouseDragged(145 + i * 30, 35 + i * 8);
        e.mouseReleased(325, 83, 0);
        assertEquals(List.of(1, 2), e.selection(), "a, b 만 안에 있다(c 는 밖)");
    }

    @Test
    void draggingOneMovesTheWholeSelection() throws Exception {
        EditorController e = editor(XML);
        click(e, 160, 50, false);
        click(e, 260, 50, true);
        e.mouseMoved(160, 50);
        e.mousePressed(160, 50, 0);
        for (int i = 1; i <= 5; i++) e.mouseDragged(160 + i * 4, 50 + i * 2);
        e.mouseReleased(180, 60, 0);
        int ax = Integer.parseInt(attr(e, 1, "x")), bx = Integer.parseInt(attr(e, 2, "x"));
        assertTrue(ax > 150, "a 가 움직였다: " + ax);
        assertEquals(100, bx - ax, "a 와 b 의 간격이 유지된다");
        assertEquals("120", attr(e, 3, "y"), "선택하지 않은 c 는 그대로");
        assertEquals(List.of(1, 2), e.selection(), "끌고 난 뒤에도 선택이 유지된다");
    }

    @Test
    void arrowKeysAndAlignAndDeleteApplyToAllSelected() throws Exception {
        EditorController e = editor(XML);
        click(e, 160, 50, false);
        click(e, 260, 50, true);
        e.keyPressed(EditorKey.RIGHT, false, true);                       // Shift+→ = 8px
        assertEquals("158", attr(e, 1, "x"));
        assertEquals("258", attr(e, 2, "x"));
        assertEquals(List.of(1, 2), e.selection());
        e.alignSelected("bottom-right");
        assertEquals("bottom-right", attr(e, 1, "anchor"));
        assertEquals("bottom-right", attr(e, 2, "anchor"));
        e.deleteSelected();
        assertFalse(e.text().contains("id=\"a\"") || e.text().contains("id=\"b\""));
        assertTrue(e.text().contains("id=\"c\""));
        e.undo();
        assertTrue(e.text().contains("id=\"a\"") && e.text().contains("id=\"b\""), "한 번에 되돌아온다");
    }

    @Test
    void duplicateMakesCopiesOfEverySelectedElement() throws Exception {
        EditorController e = editor(XML);
        click(e, 160, 50, false);
        click(e, 260, 50, true);
        e.duplicate();
        assertTrue(e.text().contains("a_copy") && e.text().contains("b_copy"));
        assertEquals(5, XmlSource.parse(e.text()).all().size() - 1);
    }

    @Test
    void escapeFirstDropsTheExtraSelection() throws Exception {
        EditorController e = editor(XML);
        click(e, 160, 50, false);
        click(e, 260, 50, true);
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(List.of(1), e.selection());
    }

    // ---- 구조 트리 검색 ----

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    private static void clickText(EditorController e, String text) {
        DrawList l = paint(e);
        DrawCommand.DrawText t = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow(() -> new AssertionError("no " + text));
        e.mouseMoved(t.x() + 2, t.y() + 2);
        e.mousePressed(t.x() + 2, t.y() + 2, 0);
        e.mouseReleased(t.x() + 2, t.y() + 2, 0);
    }

    @Test
    void treeSearchFiltersRowsLiveAndKeepsAncestors() throws Exception {
        EditorController e = editor("<screen><column id=\"col\" x=\"150\" y=\"40\"><label id=\"hello\" text=\"Hi\"/><label id=\"bye\" text=\"Yo\"/></column>"
                + "<panel id=\"other\" x=\"300\" y=\"40\"/></screen>");
        assertTrue(hasText(paint(e), "panel#other"));
        clickText(e, "검색");
        for (char c : "hel".toCharArray()) e.charTyped(c);
        DrawList l = paint(e);
        assertTrue(hasText(l, "label#hello"));
        assertTrue(hasText(l, "column#col"), "일치하는 요소의 부모는 함께 보인다");
        assertFalse(hasText(l, "label#bye"));
        assertFalse(hasText(l, "panel#other"));
        e.keyPressed(EditorKey.ENTER, false, false);
        assertFalse(hasText(paint(e), "panel#other"), "확정해도 걸러진 채 유지된다");
        clickText(e, "hel");                                              // 검색 칸을 다시 눌러 비운다
        e.keyPressed(EditorKey.A, true, false);
        e.keyPressed(EditorKey.BACKSPACE, false, false);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(hasText(paint(e), "panel#other"));
    }
}
