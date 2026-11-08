package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.edit.Geometry.Handle;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.Insets;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EditorOpsTest {
    // ---- 순수 기하 ----

    @Test
    void dragHandlesResizeAndRespectMinimumSize() {
        Rect r = new Rect(10, 10, 100, 50);
        assertEquals(new Rect(15, 8, 100, 50), Geometry.drag(r, Handle.BODY, 5, -2, 4));
        assertEquals(new Rect(10, 10, 120, 50), Geometry.drag(r, Handle.E, 20, 99, 4));
        assertEquals(new Rect(30, 10, 80, 50), Geometry.drag(r, Handle.W, 20, 0, 4), "왼쪽 손잡이: x 가 오른쪽으로, 너비는 줄어든다");
        assertEquals(new Rect(10, 25, 100, 35), Geometry.drag(r, Handle.N, 0, 15, 4));
        assertEquals(new Rect(10, 10, 110, 60), Geometry.drag(r, Handle.SE, 10, 10, 4));
        assertEquals(new Rect(106, 10, 4, 50), Geometry.drag(r, Handle.W, 500, 0, 4), "최소 너비에서 멈추고 오른쪽 끝은 고정");
        assertEquals(new Rect(10, 56, 100, 4), Geometry.drag(r, Handle.N, 0, 500, 4));
        assertArrayEquals(new int[] {10, 35}, Geometry.handleCenter(r, Handle.W));
        assertArrayEquals(new int[] {110, 60}, Geometry.handleCenter(r, Handle.SE));
    }

    private static void assertArrayEquals(int[] a, int[] b) { org.junit.jupiter.api.Assertions.assertArrayEquals(a, b); }

    @Test
    void snapPullsEdgesAndCentersToNearestTargetWithinThreshold() {
        Rect moving = new Rect(48, 21, 20, 10);        // left=48 center=58 right=68, top=21 center=26 bottom=31
        Geometry.Snap s = Geometry.snap(moving, List.of(50, 100), List.of(20, 200), 3);
        assertEquals(2, s.dx(), "left(48) → 50");
        assertEquals(-1, s.dy(), "top(21) → 20");
        assertEquals(List.of(new Geometry.Guide(true, 50), new Geometry.Guide(false, 20)), s.guides());

        Geometry.Snap none = Geometry.snap(moving, List.of(90), List.of(90), 3);
        assertEquals(0, none.dx());
        assertEquals(0, none.dy());
        assertTrue(none.guides().isEmpty());

        Geometry.Snap center = Geometry.snap(new Rect(40, 0, 20, 10), List.of(52), List.of(), 3);
        assertEquals(2, center.dx(), "가운데(50) → 52 가 가장 가깝다");
    }

    @Test
    void snapTargetsAndFlowInsertIndex() {
        List<Rect> others = List.of(new Rect(0, 0, 10, 10), new Rect(20, 0, 10, 10));
        assertEquals(List.of(0, 5, 10, 20, 25, 30, 100, 150, 200), Geometry.targets(others, new Rect(100, 0, 100, 40), true));
        // 형제 중심이 5, 25, 45 → 마우스 x 가 지난 개수
        List<Rect> sib = List.of(new Rect(0, 0, 10, 10), new Rect(20, 0, 10, 10), new Rect(40, 0, 10, 10));
        assertEquals(0, Geometry.flowInsertIndex(sib, true, 2, 0));
        assertEquals(2, Geometry.flowInsertIndex(sib, true, 30, 0));
        assertEquals(3, Geometry.flowInsertIndex(sib, true, 99, 0));
        assertEquals(3, Geometry.flowInsertIndex(sib, false, 0, 7), "세로 축 기준: 형제 중심 y=5 를 모두 지났다");
        assertEquals(0, Geometry.flowInsertIndex(sib, false, 99, 3), "세로 축 기준에서는 x 는 보지 않는다");
        assertEquals(16, Geometry.toGrid(14, 8));
        assertEquals(14, Geometry.toGrid(14, 1));
    }

    // ---- 속성을 어디에 기록하는가 ----

    private static final String DOC = """
        <screen>
          <panel id="a" width="100" style="height: 20; color: red"/>
          <panel id="b"/>
        </screen>""";

    private static XmlSource.Element el(XmlSource s, String id) {
        return s.all().stream().filter(e -> id.equals(s.attrValue(e, "id"))).findFirst().orElseThrow();
    }

    @Test
    void setPropWritesWhereItTakesEffect() {
        XmlSource s = XmlSource.parse(DOC);
        // 인라인 style 에 이미 있는 값 → 인라인 수정
        assertTrue(EditorOps.setProp(s, el(s, "a"), "height", "30", false).contains("style=\"height: 30; color: red\""));
        // 속성에 이미 있는 값 → 속성 수정(원본 모양 유지)
        assertTrue(EditorOps.setProp(s, el(s, "a"), "width", "120", false).contains("width=\"120\""));
        // 아무 데도 없고 시트도 정하지 않음 → 새 속성
        assertTrue(EditorOps.setProp(s, el(s, "b"), "width", "50", false).contains("<panel id=\"b\" width=\"50\"/>"));
        // 스타일시트가 정하고 있음 → 인라인 style 로 덮어쓴다(속성은 시트에 지므로)
        assertTrue(EditorOps.setProp(s, el(s, "b"), "width", "50", true).contains("<panel id=\"b\" style=\"width: 50\"/>"));
        // 제거: 인라인/속성은 지워지고, 없던 값은 변화 없음
        assertTrue(EditorOps.setProp(s, el(s, "a"), "color", null, false).contains("style=\"height: 20\""));
        assertFalse(EditorOps.setProp(s, el(s, "a"), "width", null, false).contains("width="));
        assertEquals(DOC, EditorOps.setProp(s, el(s, "b"), "width", null, true));
        // 마지막 인라인 값을 지우면 style 속성 자체가 사라진다
        String one = EditorOps.setProp(s, el(s, "a"), "color", null, false);
        XmlSource s1 = XmlSource.parse(one);
        assertFalse(EditorOps.setProp(s1, el(s1, "a"), "height", null, false).contains("style="));
    }

    @Test
    void setPropsAppliesSeveralAndTextEditingPicksTheRightPlace() {
        String out = EditorOps.setProps(DOC, 2, Map.of("width", "10"), p -> false);
        assertTrue(out.contains("<panel id=\"b\" width=\"10\"/>"));
        XmlSource s = XmlSource.parse("<screen><label id='a' text='x'/><button id='b'>Old</button><label id='c'/></screen>");
        assertTrue(EditorOps.setText(s, el(s, "a"), "y").contains("text='y'"));
        assertTrue(EditorOps.setText(s, el(s, "b"), "New").contains(">New</button>"));
        assertTrue(EditorOps.setText(s, el(s, "c"), "Z").contains("<label id='c' text=\"Z\"/>"));
    }

    // ---- 화면 좌표 ↔ XML 왕복(실제 레이아웃과 일치하는지) ----

    private static UiDocument load(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(200, 100);
        return d;
    }

    private static Rect rect(UiDocument d, String id) {
        LayoutBox b = d.query("#" + id).box();
        return new Rect(b.x, b.y, b.w, b.h);
    }

    /** 부모 안쪽 영역(패딩 없음 가정). */
    private static Rect content(UiDocument d, String id) {
        UiElement p = d.query("#" + id).parent();
        return new Rect(p.x(), p.y(), p.width(), p.height());
    }

    private static String applyRect(String xml, String id, Rect desired) throws Exception {
        UiDocument d = load(xml);
        UiElement e = d.query("#" + id);
        Rect before = rect(d, id);
        boolean flow = e.parent().tag().equals("row") || e.parent().tag().equals("column");
        String anchor = e.style("anchor");
        Insets margin = Insets.parse(e.style("margin"), Insets.ZERO);
        Map<String, String> props = Geometry.propsForRect(flow, anchor, margin, content(d, id), before, desired,
                e.box().style.integer("x", 0), e.box().style.integer("y", 0));
        return EditorOps.setProps(xml, e.node().sourceIndex(), props, p -> false);
    }

    @Test
    void rectEditsRoundTripForEveryAnchor() throws Exception {
        for (String anchor : List.of("top-left", "top", "top-right", "left", "center", "right", "bottom-left", "bottom", "bottom-right")) {
            String xml = "<screen><panel id=\"p\" anchor=\"" + anchor + "\" width=\"40\" height=\"20\" margin=\"4\"/></screen>";
            UiDocument d = load(xml);
            Rect before = rect(d, "p");
            Rect desired = new Rect(before.x() - 7, before.y() + 5, before.w() + 10, before.h() + 6);
            String patched = applyRect(xml, "p", desired);
            assertEquals(desired, rect(load(patched), "p"), "anchor=" + anchor + " → " + patched);
        }
    }

    @Test
    void unchangedRectWritesNothingAndOffsetsAreRemovedWhenBackAtAnchor() throws Exception {
        String xml = "<screen><panel id=\"p\" anchor=\"center\" width=\"40\" height=\"20\"/></screen>";
        UiDocument d = load(xml);
        assertTrue(Geometry.propsForRect(false, "center", Insets.ZERO, content(d, "p"), rect(d, "p"), rect(d, "p"), 0, 0).isEmpty());
        String moved = applyRect(xml, "p", rect(d, "p").translate(10, 4));
        assertTrue(moved.contains("x=\"10\"") && moved.contains("y=\"4\""), moved);
        String back = applyRect(moved, "p", rect(d, "p"));
        assertFalse(back.contains("x=") || back.contains("y="), "앵커 위치로 돌아오면 오프셋 속성이 사라진다: " + back);
    }

    @Test
    void flowParentsNeverGetPositionOffsets() throws Exception {
        String xml = "<screen><column><panel id=\"p\" width=\"40\" height=\"20\"/></column></screen>";
        UiDocument d = load(xml);
        Rect before = rect(d, "p");
        String patched = applyRect(xml, "p", new Rect(before.x() + 30, before.y() + 30, 60, 25));
        assertTrue(patched.contains("width=\"60\"") && patched.contains("height=\"25\""), patched);
        assertFalse(patched.contains(" x=") || patched.contains(" y="), patched);
    }

    // ---- 추가/삭제/복제/이동의 선택 번호 ----

    private static final String TREE = "<screen>\n  <column id=\"c\">\n    <label id=\"a\" text=\"a\"/>\n    <label id=\"b\" text=\"b\"/>\n  </column>\n</screen>";

    private static String idAt(EditorOps.Added r) {
        XmlSource s = XmlSource.parse(r.text());
        return s.attrValue(s.element(r.index()), "id");
    }

    @Test
    void addDeleteDuplicateMoveReturnTheRightSelection() {
        EditorOps.Added added = EditorOps.addChild(TREE, 1, 1, "button");
        XmlSource s = XmlSource.parse(added.text());
        assertEquals("button", s.element(added.index()).tag);
        assertEquals(List.of("a", "button", "b"),
                s.element(1).children().stream().map(e -> e.tag.equals("button") ? "button" : s.attrValue(e, "id")).toList());

        EditorOps.Added deleted = EditorOps.delete(TREE, 2);                         // a 삭제 → 이웃 b 선택
        assertEquals("b", idAt(deleted));
        EditorOps.Added last = EditorOps.delete(TREE, 3);                            // 마지막 b 삭제 → 앞 이웃 a
        assertEquals("a", idAt(last));
        EditorOps.Added onlyChild = EditorOps.delete("<screen><label id=\"x\"/></screen>", 1);
        assertEquals(0, onlyChild.index(), "자식이 없어지면 부모를 선택");

        assertEquals("a_copy", idAt(EditorOps.duplicate(TREE, 2)));

        EditorOps.Added moved = EditorOps.move(TREE, 2, 1, 2);                       // a 를 b 뒤로
        assertEquals("a", idAt(moved));
        XmlSource ms = XmlSource.parse(moved.text());
        assertEquals(List.of("b", "a"), ms.element(1).children().stream().map(e -> ms.attrValue(e, "id")).toList());
        assertNull(null);
    }

    @Test
    void everySnippetProducesValidXmlThatLoads() throws Exception {
        for (String tag : List.of("label", "button", "panel", "column", "row", "image", "spacer")) {
            EditorOps.Added a = EditorOps.addChild("<screen/>", 0, 0, tag);
            UiDocument d = UiDocument.parse(a.text(), new FixedMetrics());
            assertEquals(tag, d.root().children().get(0).tag());
            assertEquals(1, a.index());
        }
    }
}
