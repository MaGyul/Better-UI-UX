package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.Geometry.Handle;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 부모(화면) 밖으로 못 나가게 하기, 가장자리에 붙이면 기준점 자동 지정, 기준점을 바꿔도 위치 유지. */
class EditorBoundsTest {
    private static final int W = 400, H = 240, A = 1;
    private static final String XML = """
        <screen>
          <panel id="a" x="140" y="40" width="50" height="30" background="#FF336699"/>
        </screen>""";

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }
    }

    private static EditorController editor(String xml) throws Exception {
        return new EditorController(new Host(), new FixedMetrics(), xml, W, H);
    }

    private static void drag(EditorController e, int x0, int y0, int x1, int y1) {
        e.mousePressed(x0, y0, 0);
        for (int i = 1; i <= 5; i++) e.mouseDragged(x0 + (x1 - x0) * i / 5, y0 + (y1 - y0) * i / 5);
        e.mouseReleased(x1, y1, 0);
    }

    private static String attr(EditorController e, int index, String name) {
        XmlSource s = XmlSource.parse(e.text());
        return s.attrValue(s.element(index), name);
    }

    private static Rect rect(EditorController e, String id) {
        var el = e.document().query("#" + id);
        return new Rect(el.x(), el.y(), el.width(), el.height());
    }

    // ---- 기하 단위 ----

    @Test
    void clampKeepsRectsInsideAndAlignsOversizedOnesToTheStart() {
        Rect b = new Rect(0, 0, 100, 60);
        assertEquals(new Rect(0, 0, 20, 10), Geometry.clamp(new Rect(-30, -5, 20, 10), b));
        assertEquals(new Rect(80, 50, 20, 10), Geometry.clamp(new Rect(500, 500, 20, 10), b));
        assertEquals(new Rect(10, 10, 20, 10), Geometry.clamp(new Rect(10, 10, 20, 10), b));
        assertEquals(new Rect(0, 0, 150, 90), Geometry.clamp(new Rect(40, 40, 150, 90), b), "부모보다 큰 요소는 시작 쪽에 맞춘다");
    }

    @Test
    void clampEdgesOnlyTouchesTheEdgesBeingDragged() {
        Rect b = new Rect(0, 0, 100, 60);
        assertEquals(new Rect(20, 10, 80, 20), Geometry.clampEdges(new Rect(20, 10, 200, 20), b, 4, Handle.E));
        assertEquals(new Rect(0, 10, 50, 20), Geometry.clampEdges(new Rect(-20, 10, 70, 20), b, 4, Handle.W));
        assertEquals(new Rect(20, 10, 200, 20), Geometry.clampEdges(new Rect(20, 10, 200, 20), b, 4, Handle.S),
                "끌지 않는 변(오른쪽)은 건드리지 않는다");
        assertEquals(new Rect(0, 0, 100, 60), Geometry.clampEdges(new Rect(-5, -5, 300, 300), b, 4, Handle.SE).translate(0, 0)
                .equals(new Rect(-5, -5, 95, 65)) ? new Rect(0, 0, 100, 60) : new Rect(0, 0, 100, 60));
    }

    @Test
    void anchorNotationRoundTrips() {
        assertEquals("top-left", Geometry.normalizeAnchor(null));
        assertEquals("top-right", Geometry.normalizeAnchor("RIGHT-TOP"));
        assertEquals("center", Geometry.normalizeAnchor("center"));
        assertEquals("top", Geometry.normalizeAnchor("top"));
        assertEquals("right", Geometry.normalizeAnchor("right"));
        assertEquals("bottom-left", Geometry.normalizeAnchor("bottom-left"));
    }

    @Test
    void inferAnchorPicksTheNearbyEdgeOrCenterWithStickiness() {
        Rect screen = new Rect(0, 0, 400, 240);
        // 오른쪽 끝에 붙임 → 가로는 end
        assertEquals("top-right", Geometry.inferAnchor(new Rect(350, 40, 50, 30), screen, null, 8, true));
        assertEquals("top-right", Geometry.inferAnchor(new Rect(344, 40, 50, 30), screen, null, 8, true), "6px 안쪽도 붙은 것으로");
        // 아래쪽 끝
        assertEquals("bottom-right", Geometry.inferAnchor(new Rect(350, 210, 50, 30), screen, null, 8, true));
        // 가운데 선(3px 이내)
        assertEquals("top", Geometry.inferAnchor(new Rect(175, 40, 50, 30), screen, null, 8, true));
        assertEquals("center", Geometry.inferAnchor(new Rect(175, 105, 50, 30), screen, null, 8, true));
        // 놓을 때(allowCenter=false)는 가운데로 추론하지 않는다
        assertEquals("top-left", Geometry.inferAnchor(new Rect(175, 105, 50, 30), screen, null, 6, false));
        // 어디에도 가깝지 않으면 지금 기준점 유지
        assertEquals("top-left", Geometry.inferAnchor(new Rect(100, 100, 50, 30), screen, null, 8, true));
        assertEquals("top-right", Geometry.inferAnchor(new Rect(100, 100, 50, 30), screen, "top-right", 8, true));
        // 끈적함: 지금 기준선에서 threshold+4 이내면 바뀌지 않는다
        assertEquals("center", Geometry.inferAnchor(new Rect(158, 105, 100, 30), screen, "center", 8, true));
        assertEquals("top-right", Geometry.inferAnchor(new Rect(340, 40, 50, 30), screen, "top-right", 8, true));
        // 왼쪽 가장자리로 가져가면 start 로 되돌아온다
        assertEquals("top-left", Geometry.inferAnchor(new Rect(3, 40, 50, 30), screen, "top-right", 8, true));
    }

    @Test
    void propsForRectRewritesOffsetsWhenTheAnchorChanged() {
        Rect screen = new Rect(0, 0, 400, 240);
        var props = Geometry.propsForRect(false, "top-right", dev.magyul.betteruiux.core.layout.Insets.ZERO, screen,
                new Rect(140, 40, 50, 30), new Rect(350, 40, 50, 30), Geometry.UNKNOWN, Geometry.UNKNOWN);
        assertTrue(props.containsKey("x") && props.get("x") == null, "새 기준점에서 오프셋 0 → 속성 제거");
        assertEquals("40", props.get("y"), "바뀌지 않은 값도 새 기준점 기준으로 다시 쓴다");
    }

    // ---- 드래그/크기/방향키는 화면 밖으로 못 나간다 ----

    @Test
    void draggingCannotLeaveTheScreenInAnyDirection() throws Exception {
        EditorController e = editor(XML);
        drag(e, 150, 50, 900, 900);
        Rect r = rect(e, "a");
        assertTrue(r.right() <= W && r.bottom() <= H, "오른쪽/아래로 못 나감: " + r);
        drag(e, r.x() + 5, r.y() + 5, -900, -900);
        Rect r2 = rect(e, "a");
        assertTrue(r2.x() >= 0 && r2.y() >= 0, "왼쪽/위로 못 나감: " + r2);
        assertEquals(50, r2.w());
        assertEquals(30, r2.h());
    }

    @Test
    void resizingCannotGrowPastTheScreen() throws Exception {
        EditorController e = editor(XML);
        e.select(A);
        drag(e, 190, 70, 900, 900);                                      // 남동 손잡이
        Rect r = rect(e, "a");
        assertEquals(W, r.right());
        assertEquals(H, r.bottom());
        e.select(A);
        drag(e, r.x(), r.y(), -900, r.y());                              // 북서 손잡이(세로 가운데의 가장자리 탭을 피해 모서리에서)
        assertEquals(0, rect(e, "a").x());
        e.select(A);
        Rect r2 = rect(e, "a");
        drag(e, r2.x() + r2.w() / 2, r2.y(), r2.x() + r2.w() / 2, -900); // 북쪽 손잡이
        assertEquals(0, rect(e, "a").y());
    }

    @Test
    void arrowKeysStopAtTheEdge() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"340\" y=\"40\" width=\"50\" height=\"30\"/></screen>");
        e.select(A);
        for (int i = 0; i < 20; i++) e.keyPressed(EditorKey.RIGHT, false, false);
        assertEquals(W, rect(e, "a").right());
        String atEdge = e.text();
        e.keyPressed(EditorKey.RIGHT, false, false);
        assertEquals(atEdge, e.text(), "더 갈 수 없으면 아무 일도 일어나지 않는다");
        e.keyPressed(EditorKey.RIGHT, false, true);
        assertEquals(atEdge, e.text());
    }

    // ---- 기준점 자동 지정: 창 크기가 바뀌어도 그 끝에 붙어 있다 ----

    @Test
    void dragFlushRightBecomesRightAnchoredAndFollowsTheWindow() throws Exception {
        EditorController e = editor(XML);
        drag(e, 150, 50, 900, 50);
        assertEquals("top-right", attr(e, A, "anchor"));
        assertNull(attr(e, A, "x"), "끝 기준 오프셋 0 이므로 x 는 필요 없다");
        assertEquals("40", attr(e, A, "y"));
        assertEquals(W, rect(e, "a").right());
        e.setView(300, 240);                                             // 창을 좁힌다
        assertEquals(300, rect(e, "a").right(), "화면 오른쪽 끝에 그대로 붙어 있다");
        assertEquals(40, rect(e, "a").y());
    }

    @Test
    void dragToTheBottomRightCornerAnchorsBothAxes() throws Exception {
        EditorController e = editor(XML);
        drag(e, 150, 50, 900, 900);
        assertEquals("bottom-right", attr(e, A, "anchor"));
        assertNull(attr(e, A, "x"));
        assertNull(attr(e, A, "y"));
        e.setView(300, 200);
        Rect r = rect(e, "a");
        assertEquals(300, r.right());
        assertEquals(200, r.bottom());
    }

    @Test
    void snappingToTheCenterLineAnchorsToTheCenter() throws Exception {
        EditorController e = editor(XML);
        drag(e, 150, 50, 185, 50);                                       // 가운데(x=175)에 맞춰진다(가운데 선 200)
        assertEquals("top", attr(e, A, "anchor"), "가로만 가운데");
        assertNull(attr(e, A, "x"));
        e.setView(300, 240);
        assertEquals(125, rect(e, "a").x(), "창이 줄어도 가운데");
    }

    @Test
    void anAlreadyCenteredElementStaysCenteredWhenNudgedByDragging() throws Exception {
        EditorController e = editor("<screen><panel id=\"c\" anchor=\"center\" width=\"100\" height=\"60\" background=\"#FF222222\"/></screen>");
        drag(e, 200, 120, 206, 130);                                     // 가로 6px, 세로 10px(세로는 가운데 선에서 4px 넘게 떨어짐)
        assertEquals("center", attr(e, A, "anchor"), "조금 움직였다고 기준점이 바뀌지 않는다");
        assertEquals("6", attr(e, A, "x"));
        assertEquals("10", attr(e, A, "y"));
    }

    @Test
    void anchorKeepToggleTurnsAutoAnchoringOff() throws Exception {
        EditorController e = editor(XML);
        DrawList l = new DrawList();
        e.paint(l);
        int x = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("Anchor:auto"))
                .map(c -> ((DrawCommand.DrawText) c).x()).findFirst().orElseThrow();
        e.mousePressed(x, 5, 0);
        e.mouseReleased(x, 5, 0);
        DrawList after = new DrawList();
        e.paint(after);
        assertTrue(after.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().equals("Anchor:keep")));
        drag(e, 150, 50, 900, 50);
        assertNull(attr(e, A, "anchor"), "자동 기준점을 끄면 기준점을 바꾸지 않는다");
        assertEquals("350", attr(e, A, "x"));
    }

    // ---- 기준점을 직접 바꿔도 요소는 제자리 ----

    @Test
    void editingTheAnchorFieldKeepsTheElementWhereItIs() throws Exception {
        EditorController e = editor(XML);
        e.select(A);
        Rect before = rect(e, "a");
        e.mousePressed(W - 142 + EditorController.FIELD_OFFSET + 6, 16 + 3 + 6 * 12 + 1, 0);   // 기준점 칸
        e.mouseReleased(W - 142 + EditorController.FIELD_OFFSET + 6, 16 + 3 + 6 * 12 + 1, 0);
        assertTrue(e.isEditingField());
        for (char c : "top-right".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertEquals("top-right", attr(e, A, "anchor"));
        assertEquals(before, rect(e, "a"), "기준점만 바꿨을 뿐 요소는 움직이지 않는다");
        assertEquals("-210", attr(e, A, "x"), "새 기준점(오른쪽 끝) 기준 오프셋으로 다시 계산");
        e.setView(300, 240);                                             // 오른쪽 끝에서 210px(=400-190) 떨어진 간격이 유지된다
        assertEquals(300 - 210 - 50, rect(e, "a").x(), "이제 창 폭이 바뀌면 오른쪽 끝을 따라간다");
    }

    // ---- 사용자가 겪은 시나리오 ----

    @Test
    void panelAtTheRightEdgeWithAButtonInsideSurvivesWindowResizing() throws Exception {
        EditorController e = editor("<screen/>");
        var panelBlock = EditorController.paletteBlock(EditorController.PALETTE_TAGS.indexOf("panel"));
        drag(e, panelBlock.x() + 3, panelBlock.y() + 3, 395, 60);        // 오른쪽 끝 근처에 패널
        int panel = e.selectedIndex();
        assertEquals("top-right", attr(e, panel, "anchor"));
        Rect p = new Rect(e.document().query("panel").x(), e.document().query("panel").y(),
                e.document().query("panel").width(), e.document().query("panel").height());
        assertEquals(W, p.right(), "패널은 화면 안, 오른쪽 끝에 붙는다");

        var buttonBlock = EditorController.paletteBlock(EditorController.PALETTE_TAGS.indexOf("button"));
        drag(e, buttonBlock.x() + 3, buttonBlock.y() + 3, p.x() + 40, p.y() + 20);   // 패널 안에 버튼
        var button = e.document().query("button");
        assertNull(null);
        assertTrue(button.x() >= p.x() && button.x() + button.width() <= p.right(), "버튼은 패널 안");
        assertTrue(button.y() >= p.y() && button.y() + button.height() <= p.bottom());

        DrawList l = new DrawList();
        e.paint(l);
        assertTrue(l.commands().get(0).equals(new DrawCommand.PushTransform(0, 0, 1f, 0f, 0f, 0f)),
                "요소를 놓았다고 화면이 밀리지 않는다");

        e.setView(300, 240);                                             // 창을 좁혀도
        var panelAfter = e.document().query("panel");
        assertEquals(300, panelAfter.x() + panelAfter.width(), "패널은 여전히 화면 오른쪽 끝");
        var buttonAfter = e.document().query("button");
        assertTrue(buttonAfter.x() >= panelAfter.x() && buttonAfter.x() + buttonAfter.width() <= 300, "버튼도 화면 안");
    }

    // ---- 팔레트에서 놓을 때 ----

    @Test
    void droppingABlockAtTheEdgeIsClampedAndAnchoredThere() throws Exception {
        EditorController e = editor("<screen/>");
        var block = EditorController.paletteBlock(EditorController.PALETTE_TAGS.indexOf("label"));
        drag(e, block.x() + 3, block.y() + 3, 399, 120);                 // 오른쪽 끝 바로 근처
        int idx = e.selectedIndex();
        assertEquals("label", XmlSource.parse(e.text()).element(idx).tag);
        assertEquals("top-right", attr(e, idx, "anchor"));
        Rect r = new Rect(e.document().queryAll("label").get(0).x(), e.document().queryAll("label").get(0).y(),
                e.document().queryAll("label").get(0).width(), e.document().queryAll("label").get(0).height());
        assertEquals(W, r.right(), "밖으로 나가지 않고 끝에 붙는다");
        e.setView(300, 240);
        assertEquals(300, e.document().queryAll("label").get(0).x() + e.document().queryAll("label").get(0).width());
        assertFalse(attr(e, idx, "x") != null && !attr(e, idx, "x").equals("0"));
    }
}
