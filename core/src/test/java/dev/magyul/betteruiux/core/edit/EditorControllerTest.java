package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EditorControllerTest {
    private static final int W = 400, H = 240;
    // 패널 모양: 왼쪽 120, 오른쪽 142, 도구 모음 높이 16, 속성 행 높이 11 (EditorController 상수와 같다)
    private static final int RIGHT_X = W - 142, FIELD_X = RIGHT_X + 52 + 2, ROW_STEP = 12, ROW0_Y = 16 + 3;

    private static final String XML = """
        <screen>
          <panel id="a" x="140" y="40" width="50" height="30" background="#FF336699"/>
          <column id="col" x="140" y="100" gap="2">
            <label id="l1" text="One"/>
            <label id="l2" text="Two"/>
            <label id="l3" text="Three"/>
          </column>
        </screen>""";

    private static final class FakeHost implements EditorHost {
        final List<String> saved = new ArrayList<>();
        boolean failSave;

        @Override public UiDocument load(String text) throws UiParseException {
            if (text.contains("BOOM")) throw new UiParseException("boom", 1);
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException {
            if (failSave) throw new IOException("디스크 가득 참");
            saved.add(text);
        }

        @Override public String title() { return "t.xml"; }
    }

    private FakeHost host;
    private EditorController ed;

    @BeforeEach
    void setUp() throws Exception {
        host = new FakeHost();
        ed = new EditorController(host, new FixedMetrics(), XML, W, H);
    }

    // 요소 번호(전위): screen0 a1 col2 l1=3 l2=4 l3=5
    private static final int A = 1, COL = 2, L1 = 3, L2 = 4, L3 = 5;

    private void click(int x, int y) {
        ed.mousePressed(x, y, 0);
        ed.mouseReleased(x, y, 0);
    }

    private void drag(int x0, int y0, int x1, int y1) {
        ed.mousePressed(x0, y0, 0);
        int steps = 5;
        for (int i = 1; i <= steps; i++) ed.mouseDragged(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps);
        ed.mouseReleased(x1, y1, 0);
    }

    private XmlSource src() { return XmlSource.parse(ed.text()); }

    private String attr(String id, String name) {
        XmlSource s = src();
        for (XmlSource.Element e : s.all()) {
            if (id.equals(s.attrValue(e, "id"))) return s.attrValue(e, name);   // 속성이 없으면 null
        }
        return null;
    }

    private List<String> childIds(String parentId) {
        XmlSource s = src();
        return s.all().stream().filter(e -> parentId.equals(s.attrValue(e, "id"))).findFirst().orElseThrow()
                .children().stream().map(e -> s.attrValue(e, "id")).toList();
    }

    // ---- 선택 ----

    @Test
    void clickSelectsDeepestSourceElementAndEmptyAreaSelectsRoot() {
        click(150, 50);
        assertEquals(A, ed.selectedIndex());
        click(141, 112);                        // 컬럼 안의 두 번째 라벨
        assertEquals(L2, ed.selectedIndex());
        click(230, 200);                        // 빈 캔버스(패널 사이) → 루트(screen)
        assertEquals(0, ed.selectedIndex());
        assertEquals(XML, ed.text(), "선택만으로는 텍스트가 바뀌지 않는다");
        assertFalse(ed.isDirty());
    }

    @Test
    void clicksOnPanelsDoNotSelectCanvasElements() {
        ed.select(A);
        click(10, 100);                         // 아웃라이너 빈 곳
        click(RIGHT_X + 5, 200);                // 속성 패널 빈 곳
        assertEquals(A, ed.selectedIndex());
    }

    // ---- 이동 / 크기 ----

    @Test
    void draggingBodyMovesViaOffsetsAsOneUndoStep() {
        drag(150, 50, 160, 60);
        assertEquals(A, ed.selectedIndex());
        assertEquals("150", attr("a", "x"));
        assertEquals("50", attr("a", "y"));
        assertTrue(ed.isDirty());
        assertEquals(150, ed.document().query("#a").x());
        ed.keyPressed(EditorKey.Z, true, false);
        assertEquals(XML, ed.text(), "드래그 전체가 한 번의 Undo 로 취소된다");
        assertEquals(140, ed.document().query("#a").x());
        ed.keyPressed(EditorKey.Y, true, false);
        assertEquals("150", attr("a", "x"));
    }

    @Test
    void tinyMovementIsAClickNotADrag() {
        ed.mousePressed(150, 50, 0);
        ed.mouseDragged(151, 51);
        ed.mouseReleased(151, 51, 0);
        assertEquals(XML, ed.text());
        assertFalse(ed.isDirty());
    }

    @Test
    void resizeHandlesChangeWidthAndHeight() {
        ed.select(A);                           // (140,40,50,30) → SE 손잡이 (190,70)
        drag(190, 70, 200, 76);
        assertEquals("60", attr("a", "width"));
        assertEquals("36", attr("a", "height"));
        assertEquals("140", attr("a", "x"), "오른쪽/아래 손잡이는 위치를 바꾸지 않는다");
        ed.select(A);
        drag(140, 55, 130, 55);                 // W 손잡이: 왼쪽으로 10 → x 와 width 가 함께 바뀐다
        assertEquals("130", attr("a", "x"));
        assertEquals("70", attr("a", "width"));
    }

    @Test
    void escapeDuringDragCancelsIt() {
        ed.mousePressed(150, 50, 0);
        ed.mouseDragged(170, 70);
        assertTrue(ed.text().contains("x=\"1"), "미리보기 중에는 텍스트가 바뀌어 있다");
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(XML, ed.text());
        ed.mouseReleased(170, 70, 0);
        assertEquals(XML, ed.text());
        assertEquals(140, ed.document().query("#a").x());
    }

    @Test
    void draggingInsideAColumnReordersChildren() {
        drag(141, 101, 141, 135);               // l1 을 맨 아래로
        assertEquals(List.of("l2", "l3", "l1"), childIds("col"));
        assertEquals("l1", src().attrValue(src().element(ed.selectedIndex()), "id"), "선택이 옮겨진 요소를 따라간다");
        ed.undo();
        assertEquals(List.of("l1", "l2", "l3"), childIds("col"));
    }

    @Test
    void snappingPullsEdgesToSiblingsAndShowsGuides() {
        // a(140,40,50,30) 를 col(x=140..170) 의 왼쪽선 근처(138)로 끌면 140 에 달라붙는다. 이동 후 x 오프셋은 다시 140.
        ed.mousePressed(150, 50, 0);
        ed.mouseDragged(148, 52);
        ed.mouseDragged(147, 53);               // 왼쪽 가장자리 137 → col.x(140) 근처
        DrawList l = new DrawList();
        ed.paint(l);
        assertEquals("140", attr("a", "x"), "달라붙어서 140 유지");
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.w() == 1 && r.x() == 140 && r.h() == H),
                "세로 보조선이 그려진다");
        ed.mouseReleased(147, 53, 0);
    }

    // ---- 속성 패널 ----

    private void clickField(int rowIndex) { click(FIELD_X + 6, ROW0_Y + rowIndex * ROW_STEP + 1); }

    private void type(String s) { for (char c : s.toCharArray()) ed.charTyped(c); }

    @Test
    void editingAStylePropertyThroughTheFieldWritesToTheRightPlace() {
        ed.select(A);
        clickField(3);                          // 행: tag, id, class, width ...
        assertTrue(ed.isEditingField());
        ed.keyPressed(EditorKey.BACKSPACE, false, false);
        ed.keyPressed(EditorKey.BACKSPACE, false, false);
        type("75");
        ed.keyPressed(EditorKey.ENTER, false, false);
        assertFalse(ed.isEditingField());
        assertEquals("75", attr("a", "width"));
        assertEquals(75, ed.document().query("#a").width());
        assertTrue(ed.isDirty());
    }

    @Test
    void escapeCancelsFieldEditingAndEmptyValueRemovesTheAttribute() {
        ed.select(A);
        clickField(3);
        type("999");
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(XML, ed.text());
        assertFalse(ed.isEditingField());

        clickField(3);
        for (int i = 0; i < 5; i++) ed.keyPressed(EditorKey.BACKSPACE, false, false);
        ed.keyPressed(EditorKey.ENTER, false, false);
        assertNull(attr("a", "width"), "빈 값은 속성 제거");
    }

    @Test
    void textFieldEditingCaretMovementAndClickingElsewhereCommits() {
        ed.select(L1);
        // l1: tag, id, class, text(3)
        clickField(3);
        ed.keyPressed(EditorKey.HOME, false, false);
        type(">");
        ed.keyPressed(EditorKey.END, false, false);
        type("<");
        ed.keyPressed(EditorKey.LEFT, false, false);
        ed.keyPressed(EditorKey.DELETE, false, false);
        click(300, 220);                        // 필드 밖을 누르면 확정(그리고 그 자리 요소를 선택)
        assertEquals(">One", attr("l1", "text"));
    }

    @Test
    void invalidResultIsRolledBackWithErrorStatus() {
        ed.select(A);
        clickField(1);                          // id
        type("BOOM");
        ed.keyPressed(EditorKey.ENTER, false, false);
        assertEquals(XML, ed.text(), "문서를 만들 수 없는 편집은 되돌려진다");
        assertTrue(ed.status().contains("XML 오류"));
        assertEquals(140, ed.document().query("#a").x(), "이전 문서가 살아 있다");
    }

    // ---- 추가 / 삭제 / 복제 ----

    @Test
    void addDeleteDuplicateUseSelectionAsContext() {
        ed.select(COL);
        ed.add("button");                       // 컬럼(컨테이너) 안에 추가
        assertEquals(4, childIds("col").size());
        assertEquals("button", src().element(ed.selectedIndex()).tag);

        ed.select(L2);
        ed.add("label");                        // 라벨(리프) 선택 시 부모(col) 안에 추가
        assertEquals(5, childIds("col").size());

        ed.select(L2);
        ed.keyPressed(EditorKey.DELETE, false, false);
        assertFalse(childIds("col").contains("l2"));

        ed.select(L1);
        ed.keyPressed(EditorKey.D, true, false);
        assertTrue(childIds("col").contains("l1_copy"));
        assertEquals("l1_copy", src().attrValue(src().element(ed.selectedIndex()), "id"));
    }

    @Test
    void rootCannotBeDeletedOrDuplicated() {
        ed.select(0);
        ed.keyPressed(EditorKey.DELETE, false, false);
        ed.keyPressed(EditorKey.D, true, false);
        assertEquals(XML, ed.text());
    }

    // ---- 키보드 ----

    @Test
    void arrowKeysNudgeOffsetsAndReorderInsideFlowParents() {
        ed.select(A);
        ed.keyPressed(EditorKey.RIGHT, false, false);
        ed.keyPressed(EditorKey.DOWN, false, true);                       // Shift = 8px
        assertEquals("141", attr("a", "x"));
        assertEquals("48", attr("a", "y"));

        ed.select(L2);
        ed.keyPressed(EditorKey.UP, false, false);                        // 한 칸 앞으로
        assertEquals(List.of("l2", "l1", "l3"), childIds("col"));
        assertEquals("l2", src().attrValue(src().element(ed.selectedIndex()), "id"));
        ed.keyPressed(EditorKey.DOWN, false, false);
        ed.keyPressed(EditorKey.DOWN, false, false);
        assertEquals(List.of("l1", "l3", "l2"), childIds("col"));
    }

    @Test
    void nudgeBackToZeroRemovesTheOffset() {
        ed.select(A);
        for (int i = 0; i < 3; i++) ed.keyPressed(EditorKey.LEFT, false, false);   // x: 140 → 137
        assertEquals("137", attr("a", "x"));
        ed.select(A);
        ed.undo();
        ed.undo();
        ed.undo();
        assertEquals(XML, ed.text());
    }

    @Test
    void saveWritesTextAndClearsDirtyAndReportsFailures() {
        drag(150, 50, 160, 60);
        assertTrue(ed.isDirty());
        ed.keyPressed(EditorKey.S, true, false);
        assertEquals(1, host.saved.size());
        assertEquals(ed.text(), host.saved.get(0));
        assertFalse(ed.isDirty());
        assertTrue(ed.status().contains("저장됨"));

        host.failSave = true;
        ed.select(A);
        ed.keyPressed(EditorKey.LEFT, false, false);
        assertFalse(ed.save());
        assertTrue(ed.status().contains("저장 실패"));
        assertTrue(ed.isDirty());
    }

    @Test
    void tabTogglesPanelsAndGridSnapRoundsPositions() {
        DrawList with = new DrawList();
        ed.paint(with);
        ed.keyPressed(EditorKey.TAB, false, false);
        DrawList without = new DrawList();
        ed.paint(without);
        assertTrue(with.commands().size() > without.commands().size(), "패널을 숨기면 그릴 것이 줄어든다");
        ed.keyPressed(EditorKey.TAB, false, false);

        // 격자 켜기(도구 모음의 Grid 버튼) 후 드래그하면 위치가 8 의 배수로
        DrawList l = new DrawList();
        ed.paint(l);
        ed.mousePressed(0, 0, 0);               // 도구 모음 빈 곳(영향 없음)
        int gridBtnX = findButtonX(l, "Grid:off");
        click(gridBtnX + 3, 5);
        assertTrue(ed.isGridSnap());
        drag(150, 50, 157, 57);
        int x = Integer.parseInt(attr("a", "x")), y = Integer.parseInt(attr("a", "y"));
        assertEquals(0, (140 + (x - 140)) % 8 == 0 ? 0 : 1, "x=" + x);
        assertEquals(0, y % 8, "y=" + y);
    }

    private static int findButtonX(DrawList l, String label) {
        // 버튼 글자는 (x+4) 위치에 그려진다
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals(label))
                .map(c -> ((DrawCommand.DrawText) c).x() - 4).findFirst().orElseThrow();
    }

    // ---- 그리기 ----

    @Test
    void paintShowsSelectionOutlineHandlesSizeLabelAndPanels() {
        ed.select(A);
        DrawList l = new DrawList();
        ed.paint(l);
        List<DrawCommand> c = l.commands();
        assertTrue(c.contains(new DrawCommand.FillRect(140, 40, 50, 1, 0xFFFFFF55)), "선택 윤곽(위쪽 변)");
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText t && t.text().startsWith("50x30")), "크기 표시");
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText t && t.text().equals("Save")), "도구 모음");
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText t && t.text().startsWith("panel#a")), "아웃라이너");
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText t && t.text().equals("width")), "속성 패널");
        long handles = c.stream().filter(x -> x instanceof DrawCommand.FillRect r && r.w() == 6 && r.h() == 6 && r.argb() == 0xFFFFFFFF).count();
        assertEquals(8, handles, "손잡이 8개");
    }

    @Test
    void outlinerRowClickSelectsAndShowsNestedHierarchy() {
        DrawList l = new DrawList();
        ed.paint(l);
        List<String> labels = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.x() < 120)
                .map(c -> ((DrawCommand.DrawText) c).text()).toList();
        assertTrue(labels.stream().anyMatch(s -> s.startsWith("screen")));
        assertTrue(labels.stream().anyMatch(s -> s.startsWith("label#l3")));
        // 행 순서: screen, panel#a, column#col, label#l1.. → 다섯 번째 행(l2)을 클릭
        click(60, 16 + 2 + 4 * 11 + 3);
        assertEquals(L2, ed.selectedIndex());
    }

    @Test
    void expandedElementsAreNotSelectableButTheirSourceAncestorIs() throws Exception {
        String xml = "<screen>\n<template id=\"t\"><label id=\"x\" text=\"tpl\"/></template>\n"
                + "<column id=\"c\" x=\"140\" y=\"40\"><use template=\"t\"/></column>\n</screen>";
        EditorController e2 = new EditorController(host, new FixedMetrics(), xml, W, H);
        e2.mousePressed(141, 41, 0);
        e2.mouseReleased(141, 41, 0);
        XmlSource s = XmlSource.parse(xml);
        assertEquals("column", s.element(e2.selectedIndex()).tag, "템플릿에서 생긴 라벨 대신 원본에 있는 조상 column 이 선택된다");
        assertNotEquals(-1, e2.selectedIndex());
    }

    @Test
    void documentHasAnimationsDisabledSoDragPreviewsDoNotReplay() throws Exception {
        String xml = "<screen><style>@keyframes e { from { opacity: 0 } to { opacity: 1 } } "
                + "label { animation: e 1s linear both }</style><label id='l' text='x' x='140' y='40'/></screen>";
        EditorController e2 = new EditorController(host, new FixedMetrics(), xml, W, H);
        DrawList l = new DrawList();
        e2.document().paint(l, -1, -1);
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.argb() == 0xFFFFFFFF),
                "애니메이션이 꺼져 있어 첫 프레임부터 최종 모양(불투명)");
    }
}
