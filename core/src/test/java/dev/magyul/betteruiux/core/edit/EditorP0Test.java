package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 종류별 속성 편집기, 속성 패널 스크롤/섹션, 도움말, 요소 복사·붙여넣기, 감싸기/풀기. */
class EditorP0Test {
    private static final int W = 400, H = 240;
    private static final int FX = W - EditorController.RIGHT_W + 60 + 6;

    private static final class Host implements EditorHost {
        String clip = "";

        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public String clipboard() { return clip; }

        @Override public void setClipboard(String text) { clip = text; }
    }

    private static final String XML = """
        <screen>
          <column id="c" x="150" y="40" width="160" height="120" gap="2">
            <label id="l1" text="One"/>
            <button id="b1" text="Two"/>
          </column>
        </screen>""";

    private Host host;

    private EditorController editor(String xml) throws Exception {
        host = new Host();
        return new EditorController(host, new FixedMetrics(), xml, W, H);
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    private static DrawCommand.DrawText text(DrawList l, String t) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(t))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
    }

    private static String attr(EditorController e, String id, String name) {
        XmlSource s = XmlSource.parse(e.text());
        for (var el : s.all()) if (id.equals(s.attrValue(el, "id"))) return s.attrValue(el, name);
        return null;
    }

    private static int rowY(int row) { return EditorController.TOOLBAR_H + 3 + row * (EditorController.ROW_H + 1) + 3; }

    // ---- 종류별 편집기 ----

    @Test
    void enumFieldsOpenAMenuAndPickingAValueApplies() throws Exception {
        EditorController e = editor(XML);
        e.select(2);                                               // label#l1 (자유 배치가 아닌 column 안이라 anchor 줄이 없다) → column 선택
        e.select(1);                                               // column#c : tag,id,class,hdr,w,h,margin,anchor,x,y,padding,gap,justify
        paint(e);
        click(e, FX, rowY(12));                                    // justify
        DrawList l = paint(e);
        assertTrue(hasText(l, "space-between"), "선택지가 메뉴로 나온다");
        DrawCommand.DrawText item = text(l, "center");
        click(e, item.x() + 2, item.y() + 2);
        assertEquals("center", attr(e, "c", "justify"));
    }

    @Test
    void numberSteppersAndArrowKeysAdjustValues() throws Exception {
        EditorController e = editor(XML);
        e.select(1);
        paint(e);
        int plusX = W - 4 - 5, y = rowY(4);                        // 너비 칸의 + 버튼
        click(e, plusX, y);
        assertEquals("161", attr(e, "c", "width"));
        int minusX = W - 4 - 14;
        click(e, minusX, y);
        click(e, minusX, y);
        assertEquals("159", attr(e, "c", "width"));
        click(e, FX, y);                                           // 직접 편집 중 ↑ 로 올리기
        e.keyPressed(EditorKey.UP, false, true);
        assertEquals("167", attr(e, "c", "width"));
    }

    @Test
    void booleanFieldsToggleOnClick() throws Exception {
        EditorController e = editor(XML);
        e.select(3);                                               // button#b1
        paint(e);
        // 행: tag,id,class,text,onClick,hdr,w,h,margin,padding,visible(10)
        click(e, FX, rowY(10));
        assertEquals("false", attr(e, "b1", "visible"));
        click(e, FX, rowY(10));
        assertEquals(null, attr(e, "b1", "visible"), "기본값으로 돌아가면 속성을 지운다");
    }

    @Test
    void sectionsCollapseAndThePanelScrollsWhenRowsOverflow() throws Exception {
        EditorController e = editor(XML);
        e.select(1);
        DrawList l = paint(e);
        assertTrue(hasText(l, "너비"));
        click(e, W - 40, EditorController.TOOLBAR_H + 3 + 3 * (EditorController.ROW_H + 1) + 3);   // "- 레이아웃" 머리줄
        assertFalse(hasText(paint(e), "너비"), "접으면 그 아래 속성이 숨는다");
        click(e, W - 40, EditorController.TOOLBAR_H + 3 + 3 * (EditorController.ROW_H + 1) + 3);
        assertTrue(hasText(paint(e), "너비"));

        EditorController small = new EditorController(host, new FixedMetrics(), XML, W, 90);
        small.select(1);
        paint(small);
        assertFalse(hasText(paint(small), "배경"), "작은 창에서는 아래 속성이 화면 밖");
        for (int i = 0; i < 6; i++) small.mouseScrolled(W - 40, 50, -1.0);
        assertTrue(hasText(paint(small), "배경"), "휠로 스크롤하면 보인다");
    }

    // ---- 도움말 ----

    @Test
    void helpOverlayOpensWithQuestionMarkAndClosesWithEscape() throws Exception {
        EditorController e = editor(XML);
        assertFalse(hasText(paint(e), "에디터 도움말"));
        e.charTyped('?');
        assertTrue(hasText(paint(e), "에디터 도움말"));
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertFalse(hasText(paint(e), "에디터 도움말"));
        e.keyPressed(EditorKey.F1, false, false);
        assertTrue(hasText(paint(e), "에디터 도움말"));
    }

    // ---- 복사 / 붙여넣기 ----

    @Test
    void copyPasteDuplicatesAnElementIntoTheSelectedContainerWithUniqueIds() throws Exception {
        EditorController e = editor(XML);
        e.select(3);                                               // button#b1
        e.keyPressed(EditorKey.C, true, false);
        assertTrue(host.clip.contains("<button"));
        e.select(1);                                               // column 에 붙여넣기
        e.keyPressed(EditorKey.V, true, false);
        XmlSource s = XmlSource.parse(e.text());
        long buttons = s.all().stream().filter(el -> el.tag.equals("button")).count();
        assertEquals(2, buttons);
        assertEquals("b1_copy", attr(e, "b1_copy", "id"), "id 가 겹치지 않는다");
    }

    @Test
    void cutRemovesTheElementAndPasteBringsItBack() throws Exception {
        EditorController e = editor(XML);
        e.select(3);
        e.keyPressed(EditorKey.X, true, false);
        assertEquals(null, attr(e, "b1", "text") == null ? null : "x");
        assertFalse(e.text().contains("Two"));
        e.select(1);
        e.keyPressed(EditorKey.V, true, false);
        assertTrue(e.text().contains("Two"));
    }

    // ---- 감싸기 / 풀기 ----

    @Test
    void wrapPutsTheElementInsideANewContainerAndUnwrapReverts() throws Exception {
        EditorController e = editor(XML);
        e.select(2);                                               // label#l1
        e.wrapSelected("row");
        XmlSource s = XmlSource.parse(e.text());
        var row = s.all().stream().filter(el -> el.tag.equals("row")).findFirst().orElseThrow();
        assertEquals("label", row.children().get(0).tag);
        assertEquals(row.index(), e.selectedIndex(), "감싼 컨테이너가 선택된다");
        e.unwrapSelected();
        assertFalse(XmlSource.parse(e.text()).all().stream().anyMatch(el -> el.tag.equals("row")));
        assertTrue(e.text().contains("id=\"l1\""));
    }

    @Test
    void wrapMovesPositionAttributesToTheWrapper() {
        String xml = "<screen><label id=\"l\" x=\"30\" y=\"40\" anchor=\"top-right\" text=\"A\"/></screen>";
        EditorOps.Added r = EditorOps.wrap(xml, 1, "column");
        XmlSource s = XmlSource.parse(r.text());
        var wrapper = s.element(r.index());
        assertEquals("column", wrapper.tag);
        assertEquals("30", s.attrValue(wrapper, "x"));
        assertEquals("top-right", s.attrValue(wrapper, "anchor"));
        assertEquals(null, s.attrValue(wrapper.children().get(0), "x"));
    }

    @Test
    void wrapAndUnwrapRejectInvalidTargets() {
        assertThrows(IllegalArgumentException.class, () -> EditorOps.wrap("<screen><label/></screen>", 0, "row"));
        assertThrows(IllegalArgumentException.class, () -> EditorOps.wrap("<screen><label/></screen>", 1, "button"));
        assertThrows(IllegalArgumentException.class, () -> EditorOps.unwrap("<screen><row/></screen>", 1));
    }
}
