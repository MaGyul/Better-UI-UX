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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 글자 입력 켜기/끄기, 화면 이동(팬), 드래그 중 패널 숨김, 패널 접기, 한글 라벨. */
class EditorViewTest {
    private static final int W = 400, H = 240;
    private static final int RIGHT_X = W - 142;
    private static final int A = 1, L1 = 3;
    private static final int C_SEL = 0xFFFFFF55;
    private static final String XML = """
        <screen>
          <panel id="a" x="140" y="40" width="50" height="30" background="#FF336699"/>
          <column id="col" x="140" y="100" gap="2">
            <label id="l1" text="One"/>
            <button id="b" text="Go" onClick="close"/>
          </column>
        </screen>""";

    /** 호스트가 받은 글자 입력 on/off 호출을 기록한다. */
    private static final class RecordingHost implements EditorHost {
        final List<String> textInputCalls = new ArrayList<>();

        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public void textInput(boolean active, int x, int y, int w, int h) {
            textInputCalls.add(active ? "on@" + x + "," + y + " " + w + "x" + h : "off");
        }
    }

    private RecordingHost host;
    private EditorController ed;

    @BeforeEach
    void setUp() throws Exception {
        host = new RecordingHost();
        ed = new EditorController(host, new FixedMetrics(), XML, W, H);
    }

    private static boolean hasText(DrawList l, String text) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().startsWith(text));
    }

    private DrawList paint() {
        DrawList l = new DrawList();
        ed.paint(l);
        return l;
    }

    private void click(int x, int y) {
        ed.mousePressed(x, y, 0);
        ed.mouseReleased(x, y, 0);
    }

    private void drag(int x0, int y0, int x1, int y1) {
        ed.mousePressed(x0, y0, 0);
        for (int i = 1; i <= 5; i++) ed.mouseDragged(x0 + (x1 - x0) * i / 5, y0 + (y1 - y0) * i / 5);
        ed.mouseReleased(x1, y1, 0);
    }

    private int fieldY(int row) { return 16 + 3 + row * 12 + 1; }

    private int fieldX() { return RIGHT_X + EditorController.FIELD_OFFSET + 6; }

    /** 선택 윤곽의 위쪽 변은 문서 좌표로 그려지고, 화면 이동은 맨 앞의 PushTransform 으로 표현된다. */
    private static boolean outlineTop(DrawList l, int x, int y, int w) {
        return l.commands().contains(new DrawCommand.FillRect(x, y, w, 1, C_SEL));
    }

    private static boolean pannedBy(DrawList l, int tx, int ty) {
        return l.commands().get(0).equals(new DrawCommand.PushTransform(tx, ty, 1f, 0f, 0f, 0f));
    }

    // ---- 글자 입력(SDL TextInput) 켜기/끄기 ----

    @Test
    void startingAndEndingFieldEditingTurnsTextInputOnAndOff() {
        ed.select(A);
        click(fieldX(), fieldY(3));                                       // 너비 칸
        assertEquals(1, host.textInputCalls.size());
        assertTrue(host.textInputCalls.get(0).startsWith("on@"), host.textInputCalls.toString());
        ed.keyPressed(EditorKey.ENTER, false, false);
        assertEquals("off", host.textInputCalls.get(host.textInputCalls.size() - 1));

        click(fieldX(), fieldY(3));
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals("off", host.textInputCalls.get(host.textInputCalls.size() - 1));

        click(fieldX(), fieldY(3));
        click(230, 220);                                                  // 칸 밖을 눌러 확정
        assertEquals("off", host.textInputCalls.get(host.textInputCalls.size() - 1));
        assertEquals(6, host.textInputCalls.size(), "시작 3번, 끝 3번 — 짝이 맞는다");
    }

    @Test
    void closingTheEditorWhileEditingStopsTextInputWithoutChangingTheDocument() {
        ed.select(A);
        click(fieldX(), fieldY(3));
        ed.charTyped('9');
        ed.close();
        assertEquals("off", host.textInputCalls.get(host.textInputCalls.size() - 1));
        assertFalse(ed.isEditingField());
        assertEquals(XML, ed.text());
        ed.close();
        assertEquals(2, host.textInputCalls.size(), "편집 중이 아닐 때 close 는 호출을 더하지 않는다");
    }

    @Test
    void charactersAreIgnoredUnlessAFieldIsBeingEdited() {
        assertFalse(ed.charTyped('x'));
        ed.select(A);
        click(fieldX(), fieldY(3));
        assertTrue(ed.charTyped('7'));
        assertFalse(ed.charTyped('\n'));
    }

    // ---- 화면 이동(팬) ----

    @Test
    void selectingAnElementHiddenBehindASidePanelBringsItIntoTheWorkAreaWithMinimalMove() throws Exception {
        String xml = "<screen><panel id=\"p\" x=\"330\" y=\"100\" width=\"40\" height=\"30\" background=\"#FF336699\"/></screen>";
        EditorController e = new EditorController(host, new FixedMetrics(), xml, W, H);
        e.select(1);
        DrawList l = new DrawList();
        e.paint(l);
        // 작업 영역(패널 사이) = x 140..258. 요소(330..370)는 가려져 있으므로 오른쪽 끝이 여유 6px 을 두고 보이도록 -118 만큼만 옮긴다.
        // 세로(100..130)는 이미 보이므로 건드리지 않는다.
        assertTrue(pannedBy(l, -118, 0), "필요한 만큼만 옮겨진다(가운데 정렬이 아니라)");
        assertTrue(outlineTop(l, 330, 100, 40), "윤곽은 문서 좌표 그대로(이동 변환이 적용된다)");
        DrawList doc = new DrawList();
        e.paintDocument(doc);
        assertEquals(new DrawCommand.PushTransform(-118, 0, 1f, 0f, 0f, 0f), doc.commands().get(0), "문서도 같이 옮겨 그린다");
    }

    @Test
    void visibleOrOversizedElementsDoNotMoveTheView() {
        ed.select(A);                                                      // 이미 작업 영역 안
        assertTrue(pannedBy(paint(), 0, 0));
        ed.select(0);                                                      // 루트(작업 영역보다 큼)
        paint();
        ed.select(A);
        assertTrue(pannedBy(paint(), 0, 0), "루트를 거쳐도 화면이 밀리지 않는다");
    }

    @Test
    void mouseWheelAndMiddleDragPanTheViewAndFitResetsIt() {
        ed.select(A);
        ed.mouseScrolled(230, 120, 1.0);                                   // 캔버스 위 휠
        assertTrue(pannedBy(paint(), 0, 16), "휠 한 칸 = 16px");
        ed.keyPressed(EditorKey.HOME, false, false);
        assertTrue(pannedBy(paint(), 0, 0), "Home 은 처음 위치로");

        ed.mousePressed(230, 120, 1);                                      // 가운데 버튼 드래그
        ed.mouseDragged(250, 140);
        ed.mouseReleased(250, 140, 1);
        assertTrue(pannedBy(paint(), 20, 20));
        ed.resetView();
        assertTrue(pannedBy(paint(), 0, 0));
    }

    @Test
    void mouseWheelOverThePanelsDoesNotPanTheCanvas() {
        ed.select(A);
        assertFalse(ed.mouseScrolled(RIGHT_X + 5, 100, 1.0), "오른쪽 패널 위 휠은 캔버스를 움직이지 않는다");
        assertTrue(pannedBy(paint(), 0, 0));
    }

    @Test
    void hitTestingAndDraggingFollowThePan() {
        ed.mouseScrolled(230, 120, 1.0);                                   // 화면이 아래로 16px
        click(150, 66);                                                    // 화면 좌표 → 문서 좌표 (150,50) = 패널 a
        assertEquals(A, ed.selectedIndex());
        drag(150, 66, 160, 76);
        XmlSource s = XmlSource.parse(ed.text());
        assertEquals("150", s.attrValue(s.element(A), "x"));
        assertEquals("50", s.attrValue(s.element(A), "y"));
        click(150, 50);                                                    // 화면 y=50 → 문서 y=34: 패널 밖
        assertEquals(0, ed.selectedIndex());
    }

    @Test
    void paletteDropIsPlacedUnderTheCursorEvenWhenThePanIsNotZero() {
        ed.mouseScrolled(230, 120, 1.0);                                   // 아래로 16px
        var r = EditorController.paletteBlock(0);
        drag(r.x() + 3, r.y() + 3, 165, 55 + 16);                          // 화면 (165,71) = 문서 (165,55)
        XmlSource s = XmlSource.parse(ed.text());
        assertEquals("label", s.element(ed.selectedIndex()).tag);
        assertEquals("10", s.attrValue(s.element(ed.selectedIndex()), "x"));
        assertEquals("11", s.attrValue(s.element(ed.selectedIndex()), "y"));
    }

    // ---- 끄는 동안 패널 숨김 / 접기 ----

    @Test
    void sidePanelsHideWhileDraggingAnElementAndComeBackAfterwards() {
        ed.select(A);
        assertTrue(hasText(paint(), "구조"));
        assertTrue(hasText(paint(), "너비"));
        ed.mousePressed(150, 50, 0);
        assertTrue(hasText(paint(), "구조"), "아직 끌기 전에는 그대로");
        ed.mouseDragged(170, 60);
        DrawList during = paint();
        assertFalse(hasText(during, "구조"));
        assertFalse(hasText(during, "블록 (끌어다"));
        assertFalse(hasText(during, "너비"));
        assertFalse(hasText(during, "Save"), "요소를 끄는 동안은 도구 모음도 숨는다");
        assertTrue(hasText(during, "삭제 칸"), "왼쪽 아래에 삭제 칸이 나타난다");
        ed.mouseReleased(170, 60, 0);
        DrawList after = paint();
        assertTrue(hasText(after, "구조"));
        assertTrue(hasText(after, "너비"));
        assertTrue(hasText(after, "Save"));
    }

    @Test
    void onlyThePropertyPanelHidesWhileDraggingABlockFromThePalette() {
        ed.select(A);
        var r = EditorController.paletteBlock(0);
        ed.mousePressed(r.x() + 3, r.y() + 3, 0);
        ed.mouseDragged(230, 120);
        DrawList l = paint();
        assertFalse(hasText(l, "너비"), "속성 패널은 숨긴다");
        assertTrue(hasText(l, "구조"), "왼쪽(블록/트리)은 남긴다 — 트리에도 놓을 수 있어서");
        ed.keyPressed(EditorKey.ESCAPE, false, false);
        ed.mouseReleased(230, 120, 0);
        assertTrue(hasText(paint(), "너비"));
    }

    @Test
    void sidePanelsCanBeCollapsedFromTheEdgeTabsAndRestored() {
        ed.select(A);
        int leftTabX = EditorController.LEFT_W + 3, tabY = EditorController.tabY(H) + 5;
        click(leftTabX, tabY);                                             // 왼쪽 패널 접기
        DrawList l = paint();
        assertFalse(hasText(l, "구조"));
        assertTrue(hasText(l, "너비"), "오른쪽은 그대로");
        click(150, 220);                                                   // 접힌 자리(전에는 패널)를 눌러도 캔버스 클릭으로 처리
        assertEquals(0, ed.selectedIndex());
        click(3, tabY);                                                    // 가장자리 탭으로 다시 펼치기
        assertTrue(hasText(paint(), "구조"));

        ed.select(A);
        int rightTabX = W - 142 - 10 + 3;
        click(rightTabX, tabY);                                            // 오른쪽 패널 접기
        assertTrue(hasText(paint(), "너비"), "요소를 고르고 있는 동안은 잠깐 펼쳐 보인다");
        ed.select(-1);
        assertFalse(hasText(paint(), "너비"), "선택이 풀리면 다시 접힌다");
        ed.select(A);
        click(W - 10 + 3, tabY);
        assertTrue(hasText(paint(), "너비"));
    }

    @Test
    void collapsedPanelsWidenTheWorkAreaForAutoPan() throws Exception {
        String xml = "<screen><panel id=\"p\" x=\"330\" y=\"100\" width=\"40\" height=\"30\" background=\"#FF336699\"/></screen>";
        EditorController e = new EditorController(host, new FixedMetrics(), xml, W, H);
        e.mousePressed(W - 142 - 10 + 3, EditorController.tabY(H) + 5, 0);                   // 오른쪽 패널 접기
        e.mouseReleased(W - 142 - 10 + 3, EditorController.tabY(H) + 5, 0);
        e.select(1);
        DrawList l = new DrawList();
        e.paint(l);
        assertTrue(outlineTop(l, 330, 100, 40), "오른쪽이 접혀 요소가 이미 작업 영역 안이므로 움직이지 않는다");
    }

    @Test
    void tabKeyHidesEverythingAndPanelsStateSurvives() {
        ed.keyPressed(EditorKey.TAB, false, false);
        DrawList l = paint();
        assertFalse(hasText(l, "Save"));
        assertFalse(hasText(l, "구조"));
        ed.keyPressed(EditorKey.TAB, false, false);
        assertTrue(hasText(paint(), "Save"));
    }

    // ---- 한글 라벨 ----

    @Test
    void propertyLabelsAreShownInKorean() {
        ed.select(L1);                                                     // 컬럼 안의 라벨: 위치는 배치가 정하므로 기준점/x/y 행이 없다
        DrawList l = paint();
        for (String label : List.of("종류", "글자", "너비", "높이", "바깥여백", "배경", "글자색", "테두리", "투명도")) {
            assertTrue(hasText(l, label), label);
        }
        assertFalse(hasText(l, "기준점"));
        ed.select(A);                                                      // 화면(stack) 안의 패널은 기준점이 있다
        assertTrue(hasText(paint(), "기준점"));
        ed.select(4);                                                      // 버튼
        DrawList b = paint();
        assertTrue(hasText(b, "클릭"));
        ed.select(2);                                                      // 컬럼
        DrawList c = paint();
        assertTrue(hasText(c, "간격"));
        assertTrue(hasText(c, "안쪽여백"));
    }

    @Test
    void koreanLabelStillEditsTheRealAttribute() {
        ed.select(L1);
        click(fieldX(), fieldY(3));                                        // 글자 칸(text)
        for (int i = 0; i < 3; i++) ed.keyPressed(EditorKey.BACKSPACE, false, false);
        for (char c : "Two".toCharArray()) ed.charTyped(c);
        ed.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(ed.text().contains("text=\"Two\""), ed.text());
    }
}
