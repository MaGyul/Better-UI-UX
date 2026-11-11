package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * "화면(screen) 위치가 어긋나 보인다" 수정: 캔버스에서 누른 요소는 화면을 흔들지 않고, 새 요소는 지금 보는 화면 가운데에 놓고,
 * 화면을 옮겨 두면 실제 화면 경계를 보여주며, 이동은 부드럽게 한다.
 */
class EditorPanFixTest {
    private static final int W = 400, H = 240;
    private static final int RIGHT_X = W - 142;
    /** 작업 영역(패널 사이) = x 140..258, y 16..240 → 중심 (199,128). */
    private static final int CX = 199, CY = 128;
    private static final int SCREEN_FRAME = 0xFF55AAFF, OUTSIDE = 0x80000000;

    private static final String XML = """
        <screen>
          <panel id="p" x="240" y="100" width="50" height="30" background="#336699FF"/>
          <label id="far" anchor="bottom-right" margin="4" text="demo.xml"/>
        </screen>""";

    private static class Host implements EditorHost {
        boolean animate;

        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public boolean panAnimation() { return animate; }
    }

    private EditorController editor(boolean animate) throws Exception { return editor(animate, W); }

    private EditorController editor(boolean animate, int width) throws Exception {
        Host h = new Host();
        h.animate = animate;
        return new EditorController(h, new FixedMetrics(), XML, width, H);
    }

    private static boolean pannedBy(DrawList l, int tx, int ty) {
        return l.commands().get(0).equals(new DrawCommand.PushTransform(tx, ty, 1f, 0f, 0f, 0f));
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static boolean hasText(DrawList l, String text) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().equals(text));
    }

    private static int rowY(int i) { return EditorController.rowsTop() + i * 11 + 4; }

    // ---- 캔버스에서 직접 누른 요소는 화면을 흔들지 않는다 ----

    @Test
    void clickingAPartiallyHiddenElementOnTheCanvasDoesNotMoveTheView() throws Exception {
        EditorController e = editor(false);
        e.mousePressed(245, 110, 0);                 // 패널 p(240..290)는 오른쪽 패널(258~)에 일부 가려져 있다
        e.mouseReleased(245, 110, 0);
        assertEquals(1, e.selectedIndex());
        assertTrue(pannedBy(paint(e), 0, 0), "눈앞에서 직접 누른 요소 때문에 화면 전체가 밀리지 않는다");
    }

    @Test
    void pickingAnElementInTheTreeBringsItIntoTheWorkArea() throws Exception {
        EditorController e = editor(false);
        e.mousePressed(60, rowY(1), 0);              // 구조 트리의 panel#p 행
        e.mouseReleased(60, rowY(1), 0);
        assertEquals(1, e.selectedIndex());
        // 요소(240..290)의 오른쪽 끝이 작업 영역(~258)에 가려져 있다 → 여유 6px 을 두고 보이게 가로로만 -38 이동(세로는 이미 보임)
        assertTrue(pannedBy(paint(e), -38, 0));
    }

    @Test
    void aRevealRequestWaitsWhileDraggingAndIsAppliedAfterTheDrop() throws Exception {
        EditorController e = editor(false);
        e.mousePressed(60, rowY(1), 0);              // 트리 행을 누른 채(끌기 준비)
        assertTrue(pannedBy(paint(e), 0, 0), "누르고 있는 동안에는 화면이 움직이지 않는다");
        e.mouseReleased(60, rowY(1), 0);
        assertTrue(pannedBy(paint(e), -38, 0));
    }

    // ---- 새 요소는 지금 보는 화면 한가운데에 ----

    private static String attrOf(EditorController e, int index, String name) {
        XmlSource s = XmlSource.parse(e.text());
        return s.attrValue(s.element(index), name);
    }

    @Test
    void addedElementsAppearAtTheCenterOfTheVisibleWorkArea() throws Exception {
        EditorController e = editor(false);
        e.select(0);                                 // 루트(stack) 안에 추가
        e.add("label");                              // "Label" = 30x9 → 왼쪽 위 (199-15, 128-4)
        assertEquals("184", attrOf(e, e.selectedIndex(), "x"));
        assertEquals("124", attrOf(e, e.selectedIndex(), "y"));
        assertTrue(pannedBy(paint(e), 0, 0), "이미 보이는 자리에 놓았으므로 화면은 움직이지 않는다");
    }

    @Test
    void theCenterFollowsThePanAndTreeDropsUseItToo() throws Exception {
        EditorController e = editor(false);
        e.mouseScrolled(230, 120, 1.0);              // 화면 아래로 16px → 보이는 중심의 문서 좌표 (199, 112)
        e.select(0);
        e.add("label");
        assertEquals("184", attrOf(e, e.selectedIndex(), "x"));
        assertEquals("108", attrOf(e, e.selectedIndex(), "y"));

        EditorController e2 = editor(false);
        var block = EditorController.paletteBlock(EditorController.PALETTE_TAGS.indexOf("button"));
        e2.mousePressed(block.x() + 3, block.y() + 3, 0);
        e2.mouseDragged(60, rowY(0));
        e2.mouseReleased(60, rowY(0), 0);           // 루트 행에 놓기 → 맨 뒤에 추가되고 보는 화면 가운데로
        int idx = e2.selectedIndex();
        assertEquals("button", XmlSource.parse(e2.text()).element(idx).tag);
        assertNotEquals(null, attrOf(e2, idx, "x"));
    }

    // ---- 실제 화면 경계 표시 ----

    @Test
    void screenFrameIsShownOnlyWhenTheViewHasBeenMoved() throws Exception {
        EditorController e = editor(false, 700);                        // Fit 버튼이 "..." 로 숨지 않는 넓은 창
        DrawList normal = paint(e);
        assertTrue(normal.commands().stream().noneMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == SCREEN_FRAME));
        assertTrue(hasText(normal, "Fit") && !hasText(normal, "Fit!"));

        e.mouseScrolled(230, 120, 1.0);
        DrawList moved = paint(e);
        assertTrue(moved.commands().contains(new DrawCommand.FillRect(0, 0, 700, 1, SCREEN_FRAME)), "파란 경계선");
        assertTrue(moved.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == OUTSIDE),
                "경계 밖을 어둡게");
        assertTrue(hasText(moved, "Fit!"), "되돌리기 버튼이 눈에 띈다");

        e.resetView();
        assertTrue(paint(e).commands().stream().noneMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == SCREEN_FRAME));
    }

    @Test
    void fitButtonResetsTheView() throws Exception {
        EditorController e = editor(false, 700);
        e.mouseScrolled(230, 120, 1.0);
        DrawList l = paint(e);
        int fitX = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("Fit!"))
                .map(c -> ((DrawCommand.DrawText) c).x()).findFirst().orElseThrow();
        e.mousePressed(fitX, 5, 0);
        e.mouseReleased(fitX, 5, 0);
        assertTrue(pannedBy(paint(e), 0, 0));
    }

    // ---- 부드러운 이동 ----

    @Test
    void animatedPanSlidesToTheTargetAndHitTestsFollowTheCurrentPosition() throws Exception {
        EditorController e = editor(true);
        e.select(1);                                 // 가려진 패널을 찾도록 요청
        DrawList first = paint(e);
        assertFalse(pannedBy(first, -38, 0), "첫 프레임은 아직 목표에 도달하지 않았다");
        assertFalse(pannedBy(first, 0, 0), "하지만 움직이기 시작했다");
        DrawList last = first;
        for (int i = 0; i < 40; i++) last = paint(e);
        assertTrue(pannedBy(last, -38, 0), "여러 프레임 뒤에는 목표에 정확히 도착한다");
        e.mousePressed(150, 200, 0);                 // 빈 곳 → 루트
        e.mouseReleased(150, 200, 0);
        assertEquals(0, e.selectedIndex());
        e.mousePressed(210, 120, 0);                 // 도착한 뒤의 좌표계: 화면 (210,120) = 문서 (248,120) = 패널 p(240..290) 안
        e.mouseReleased(210, 120, 0);
        assertEquals(1, e.selectedIndex());
        e.mousePressed(160, 120, 0);                 // 화면 (160,120) = 문서 (198,120): 패널 p 왼쪽 밖 → 루트
        e.mouseReleased(160, 120, 0);
        assertEquals(0, e.selectedIndex());
    }
}
