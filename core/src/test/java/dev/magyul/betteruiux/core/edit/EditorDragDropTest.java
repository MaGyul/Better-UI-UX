package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 스크래치식 조작: 팔레트에서 끌어 놓기, 구조 트리 재배치/접기, 팔레트로 끌어 삭제. */
class EditorDragDropTest {
    private static final int W = 400, H = 240;
    // 요소 번호(전위): screen0 a1 col2 l1=3 l2=4 l3=5
    private static final int A = 1, COL = 2, L2 = 4;
    private static final String XML = """
        <screen>
          <panel id="a" x="140" y="40" width="50" height="30" background="#FF336699"/>
          <column id="col" x="140" y="100" gap="2">
            <label id="l1" text="One"/>
            <label id="l2" text="Two"/>
            <label id="l3" text="Three"/>
          </column>
        </screen>""";

    private EditorController ed;

    @BeforeEach
    void setUp() throws Exception {
        ed = new EditorController(new EditorHost() {
            @Override public UiDocument load(String text) throws UiParseException {
                return UiDocument.parse(text, new FixedMetrics());
            }

            @Override public void save(String text) throws IOException { }

            @Override public String title() { return "t.xml"; }
        }, new FixedMetrics(), XML, W, H);
    }

    // ---- 좌표 도우미 ----

    private static int[] block(String tag) {
        var r = EditorController.paletteBlock(EditorController.PALETTE_TAGS.indexOf(tag));
        return new int[] {r.x() + r.w() / 2, r.y() + r.h() / 2};
    }

    /** 구조 트리 i 번째 행의 세로 중앙. */
    private static int rowY(int i) { return EditorController.rowsTop() + i * 11 + 4; }

    private void drag(int x0, int y0, int x1, int y1) {
        ed.mousePressed(x0, y0, 0);
        for (int i = 1; i <= 5; i++) ed.mouseDragged(x0 + (x1 - x0) * i / 5, y0 + (y1 - y0) * i / 5);
        ed.mouseReleased(x1, y1, 0);
    }

    private void dragBlock(String tag, int x, int y) {
        int[] b = block(tag);
        drag(b[0], b[1], x, y);
    }

    private XmlSource src() { return XmlSource.parse(ed.text()); }

    private String attr(String id, String name) {
        XmlSource s = src();
        for (XmlSource.Element e : s.all()) if (id.equals(s.attrValue(e, "id"))) return s.attrValue(e, name);
        return null;
    }

    /** 부모의 자식 목록(id 가 있으면 id, 없으면 태그 이름). */
    private List<String> childTags(String parentId) {
        XmlSource s = src();
        for (XmlSource.Element e : s.all()) {
            if (parentId.equals(s.attrValue(e, "id"))) {
                return e.children().stream().map(c -> {
                    String id = s.attrValue(c, "id");
                    return id != null ? id : c.tag;
                }).toList();
            }
        }
        throw new IllegalArgumentException(parentId);
    }

    private static boolean hasText(DrawList l, String prefix) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().startsWith(prefix));
    }

    private DrawList paint() {
        DrawList l = new DrawList();
        ed.paint(l);
        return l;
    }

    // ---- 팔레트 → 캔버스 ----

    @Test
    void dropOnAStackContainerPlacesTheNewElementCenteredOnTheCursor() {
        dragBlock("label", 165, 55);                                     // 패널 a 위(= stack 부모)
        XmlSource s = src();
        assertEquals("label", s.element(ed.selectedIndex()).tag);
        assertEquals(List.of("label"), childTags("a"));
        // 라벨 "Label" 은 30x9 → 가운데가 (165,55) 이도록 왼쪽 위 = (150,51) → 부모(140,40) 기준 오프셋 (10,11)
        var box = ed.document().query("panel").children().get(0);
        assertEquals(150, box.x());
        assertEquals(51, box.y());
        assertEquals("10", s.attrValue(s.element(ed.selectedIndex()), "x"));
        ed.undo();
        assertEquals(XML, ed.text(), "추가+위치 지정이 한 번의 Undo 로 취소된다");
    }

    @Test
    void dropInsideAColumnInsertsAtTheCursorPosition() {
        dragBlock("button", 141, 113);                                   // l1 과 l2 사이
        assertEquals(List.of("l1", "button", "l2", "l3"), childTags("col"));
        assertFalse(src().element(ed.selectedIndex()).attrs().stream().anyMatch(a -> a.name.equals("x")),
                "row/column 안에서는 x/y 를 쓰지 않는다");
        dragBlock("label", 141, 148);                                    // 맨 아래(l3 의 아래쪽 절반)
        assertEquals("label", childTags("col").get(4));
    }

    @Test
    void clickingAPaletteBlockWithoutDraggingAddsToTheSelectedContainer() {
        ed.select(COL);
        int[] b = block("label");
        ed.mousePressed(b[0], b[1], 0);
        ed.mouseReleased(b[0], b[1], 0);
        assertEquals(4, childTags("col").size());
        assertEquals("label", src().element(ed.selectedIndex()).tag);
    }

    @Test
    void droppingOnEmptyCanvasGoesIntoTheRootAndSidePanelsAreNotDropTargets() {
        dragBlock("panel", 230, 200);                                    // 빈 캔버스 → screen(루트)
        assertEquals(3, src().root().children().size());
        assertEquals("panel", src().element(ed.selectedIndex()).tag);
        String before = ed.text();
        dragBlock("label", 300, 120);                                    // 오른쪽 속성 패널 위 → 놓을 곳 없음
        assertEquals(before, ed.text());
    }

    @Test
    void droppingOnThePaletteItselfCancelsTheAdd() {
        dragBlock("label", 60, 40);
        assertEquals(XML, ed.text());
    }

    @Test
    void escapeCancelsAPaletteDrag() {
        int[] b = block("label");
        ed.mousePressed(b[0], b[1], 0);
        ed.mouseDragged(165, 55);
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        ed.mouseReleased(165, 55, 0);
        assertEquals(XML, ed.text());
    }

    // ---- 팔레트 → 구조 트리 ----

    @Test
    void dropOnTreeRowsInsideBeforeAfter() {
        dragBlock("label", 60, rowY(1));                                  // 행 가운데 = 패널 a 안
        assertEquals(List.of("label"), childTags("a"));
        ed.undo();

        int l2Row = EditorController.rowsTop() + 4 * 11;                  // l2 행의 위쪽 경계
        dragBlock("button", 60, l2Row);                                   // 행 위쪽 = l2 앞
        assertEquals(List.of("l1", "button", "l2", "l3"), childTags("col"));
        ed.undo();
        dragBlock("button", 60, l2Row + 9);                               // 행 아래쪽 = l2 뒤
        assertEquals(List.of("l1", "l2", "button", "l3"), childTags("col"));
        ed.undo();
        dragBlock("button", 60, rowY(4));                                 // 리프 행 가운데 → 그 뒤
        assertEquals(List.of("l1", "l2", "button", "l3"), childTags("col"));
    }

    // ---- 구조 트리 안에서 재배치 ----

    @Test
    void dragTreeRowIntoAnotherContainerReparents() {
        drag(60, rowY(3), 60, rowY(1));                                   // l1 을 패널 a 안으로
        assertEquals(List.of("l1"), childTags("a"));
        assertEquals(List.of("l2", "l3"), childTags("col"));
        assertEquals("l1", src().attrValue(src().element(ed.selectedIndex()), "id"), "옮긴 요소가 선택된다");
        ed.undo();
        assertEquals(XML, ed.text());
    }

    @Test
    void dragTreeRowToReorderWithinSameParent() {
        int l3RowBottom = EditorController.rowsTop() + 5 * 11 + 9;        // l3 행 아래쪽 = l3 뒤
        drag(60, rowY(3), 60, l3RowBottom);
        assertEquals(List.of("l2", "l3", "l1"), childTags("col"));
    }

    @Test
    void cannotDropAnElementIntoItsOwnSubtree() {
        String before = ed.text();
        drag(60, rowY(2), 60, rowY(3));                                   // col 을 자기 자식 l1 행(그 뒤 = col 안)으로
        assertEquals(before, ed.text());
        assertTrue(ed.status().contains("놓을 수 없"));
    }

    @Test
    void aSimpleClickOnATreeRowOnlySelects() {
        ed.mousePressed(60, rowY(4), 0);
        ed.mouseReleased(60, rowY(4), 0);
        assertEquals(L2, ed.selectedIndex());
        assertEquals(XML, ed.text());
    }

    // ---- 삭제: 팔레트 쪽으로 끌어다 놓기 ----

    @Test
    void draggingATreeRowOntoThePaletteDeletesIt() {
        drag(60, rowY(4), 60, 40);
        assertEquals(List.of("l1", "l3"), childTags("col"));
        assertTrue(ed.status().contains("삭제"));
        ed.undo();
        assertEquals(XML, ed.text());
    }

    @Test
    void draggingACanvasElementOntoThePaletteDeletesItAndUndoRestoresIt() {
        drag(150, 50, 30, H - 10);                                        // 패널 a 를 왼쪽 아래 삭제 칸으로(끄는 동안 패널이 숨겨져 칸이 나타난다)
        assertNull(attr("a", "width"));
        assertEquals(1, src().root().children().size());
        ed.undo();
        assertEquals(XML, ed.text(), "드래그 도중 미리보기 편집은 기록에 남지 않고, 삭제 한 번만 취소된다");
    }

    @Test
    void theRootCannotBeDeletedByDragging() {
        drag(60, rowY(0), 60, 40);
        assertEquals(XML, ed.text());
    }

    // ---- 접고 펼치기 ----

    private void clickToggle(int row, int depth) {
        int x = 3 + depth * 8 + 3;
        ed.mousePressed(x, rowY(row), 0);
        ed.mouseReleased(x, rowY(row), 0);
    }

    @Test
    void togglingARowHidesAndShowsItsChildrenAndKeepsSelection() {
        assertTrue(hasText(paint(), "label#l2"));
        ed.select(A);
        clickToggle(2, 1);                                                // col 접기
        DrawList l = paint();
        assertFalse(hasText(l, "label#l1"));
        assertTrue(hasText(l, "column#col"));
        assertEquals(A, ed.selectedIndex());
        clickToggle(2, 1);                                                // 다시 펼치기
        assertTrue(hasText(paint(), "label#l3"));
    }

    @Test
    void collapseAllAndExpandAllButtons() {
        int headerY = 16 + EditorController.PALETTE_H + 5;
        ed.mousePressed(120, headerY, 0);                                 // "-" 모두 접기
        ed.mouseReleased(120, headerY, 0);
        DrawList l = paint();
        assertFalse(hasText(l, "label#l1"));
        assertTrue(hasText(l, "panel#a"));
        ed.mousePressed(133, headerY, 0);                                 // "+" 모두 펼치기
        ed.mouseReleased(133, headerY, 0);
        assertTrue(hasText(paint(), "label#l1"));
    }

    @Test
    void collapsedStateSurvivesEditsAndSelectingOnCanvasRevealsTheElement() {
        clickToggle(2, 1);                                                // col 접기
        ed.select(A);
        ed.keyPressed(EditorKey.RIGHT, false, false);                     // 편집이 일어나 문서가 다시 만들어져도
        assertFalse(hasText(paint(), "label#l1"), "접힘이 유지된다(id 기반 키)");

        ed.mousePressed(141, 113, 0);                                     // 캔버스에서 l2 를 직접 클릭
        ed.mouseReleased(141, 113, 0);
        assertEquals(L2, ed.selectedIndex());
        assertTrue(hasText(paint(), "label#l2"), "선택한 요소가 보이도록 조상이 펼쳐진다");
    }

    // ---- 놓을 자리 표시 ----

    @Test
    void paletteDragShowsDropTargetOutlineAndGhostBlock() {
        int[] b = block("panel");
        ed.mousePressed(b[0], b[1], 0);
        ed.mouseDragged(165, 55);
        DrawList l = paint();
        assertTrue(l.commands().contains(new DrawCommand.FillRect(140, 40, 50, 1, 0xFF55FF77)), "놓을 컨테이너 윤곽(초록)");
        assertTrue(hasText(l, "패널"), "마우스를 따라다니는 블록");
        ed.mouseReleased(165, 55, 0);
    }

    @Test
    void invalidTargetIsOutlinedInRedAndTrashShowsDeleteHint() {
        ed.mousePressed(60, rowY(2), 0);                                  // col 행을 들고
        ed.mouseDragged(60, rowY(3));                                     // 자기 자식 행 위로
        DrawList bad = paint();
        assertTrue(bad.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFFFF5555),
                "놓을 수 없는 자리는 빨간색");
        ed.mouseDragged(60, 40);                                          // 팔레트 위로
        DrawList trash = paint();
        assertTrue(trash.commands().contains(new DrawCommand.FillRect(0, 16, 140, EditorController.PALETTE_H, 0x70FF3030)));
        assertTrue(hasText(trash, "여기에 놓으면 삭제"));
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        ed.mouseReleased(60, 40, 0);
        assertEquals(XML, ed.text(), "Esc 로 취소하면 삭제되지 않는다");
    }

    @Test
    void paletteIsDrawnWithAllBlocks() {
        DrawList l = paint();
        for (String label : List.of("라벨", "버튼", "패널", "세로", "가로", "이미지", "여백")) assertTrue(hasText(l, label), label);
    }
}
