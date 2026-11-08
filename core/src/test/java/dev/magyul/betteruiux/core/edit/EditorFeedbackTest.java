package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.Cursor;
import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 상단 바 아래 요소 꺼내기, row/column 최소 크기, 이벤트 자동 완성, 컬러피커, 호버/눌림/커서 피드백. */
class EditorFeedbackTest {
    private static final int W = 400, H = 240;
    private static final int FIELD_X = W - EditorController.RIGHT_W + 60 + 5;

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        int exits;

        @Override public void requestExit() { exits++; }

        java.util.Map<String, String> prefs = new java.util.HashMap<>();
        int saves;

        @Override public java.util.Map<String, String> loadPrefs() { return prefs; }

        @Override public void savePrefs(java.util.Map<String, String> p) {
            prefs = new java.util.HashMap<>(p);
            saves++;
        }

        @Override public List<String> suggestions(String key) {
            return key.equals("onClick") ? List.of("buy_item", "close", "open_shop") : List.of();
        }
    }

    private static Host lastHost;

    private static EditorController editor(String xml) throws Exception {
        lastHost = new Host();
        return new EditorController(lastHost, new FixedMetrics(), xml, W, H);
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static void drag(EditorController e, int x0, int y0, int x1, int y1) {
        e.mouseMoved(x0, y0);
        e.mousePressed(x0, y0, 0);
        for (int i = 1; i <= 5; i++) e.mouseDragged(x0 + (x1 - x0) * i / 5, y0 + (y1 - y0) * i / 5);
        e.mouseReleased(x1, y1, 0);
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    private static Cursor cursor(DrawList l) {
        DrawCommand last = l.commands().get(l.commands().size() - 1);
        return ((DrawCommand.RequestCursor) last).cursor();
    }

    private static Rect rect(EditorController e, String id) {
        var el = e.document().query("#" + id);
        return new Rect(el.x(), el.y(), el.width(), el.height());
    }

    private static void type(EditorController e, String s) {
        for (char c : s.toCharArray()) e.charTyped(c);
    }

    /** 속성 패널에서 n 번째 줄(0부터)의 값 칸을 누른다. */
    private static void clickRow(EditorController e, int n) {
        click(e, FIELD_X, EditorController.TOOLBAR_H + 3 + n * (EditorController.ROW_H + 1) + 3);
    }

    // ---- (a) 상단 바 안의 요소 ----

    @Test
    void anElementUnderTheToolbarCanBePickedAndDraggedOut() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"90\" y=\"0\" width=\"40\" height=\"30\" background=\"#FF336699\"/></screen>");
        click(e, 105, 5);                                          // 상단 바의 빈 곳 = 아래 캔버스를 누른 것
        assertEquals(1, e.selectedIndex());
        drag(e, 105, 5, 105, 80);
        assertEquals(75, rect(e, "a").y(), "바 밑에서 끌어 내릴 수 있다");
    }

    @Test
    void selectingFromTheTreeRevealsAnElementHiddenUnderTheToolbar() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"150\" y=\"0\" width=\"60\" height=\"30\" background=\"#FF336699\"/></screen>");
        e.select(1);
        for (int i = 0; i < 40; i++) paint(e);
        Rect r = rect(e, "a");
        DrawList l = paint(e);
        assertTrue(r.y() + 0 >= 0);
        // 화면이 아래로 밀려 요소 윗면이 도구 모음 밑에 온다
        click(e, 180, EditorController.TOOLBAR_H + 10);
        assertEquals(1, e.selectedIndex());
        assertNotEquals(Cursor.NOT_ALLOWED, cursor(l));
    }

    // ---- (b) row/column 최소 크기 ----

    @Test
    void aColumnCannotBeResizedSmallerThanItsContent() throws Exception {
        EditorController e = editor("""
            <screen>
              <column id="c" x="150" y="40" width="200" height="120">
                <panel width="80" height="20" background="#FF336699"/>
              </column>
            </screen>""");
        e.select(1);
        Rect r = rect(e, "c");
        drag(e, r.right(), r.y() + 10, r.x() - 100, r.y() + 10);  // 동쪽 손잡이를 안쪽으로 끝까지 — 아니면 몸통을 끈다
        Rect after = rect(e, "c");
        assertTrue(after.w() >= 80, "내용(80)보다 작아지면 안 된다: " + after.w());
    }

    @Test
    void typingASmallerSizeIsRaisedToTheContentSize() throws Exception {
        EditorController e = editor("""
            <screen>
              <column id="c" x="20" y="40" width="200" height="120">
                <panel width="80" height="20" background="#FF336699"/>
              </column>
            </screen>""");
        e.select(1);
        paint(e);
        clickRow(e, 3);                                            // 너비
        type(e, "10");
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(rect(e, "c").w() >= 80, "내용보다 작게 입력해도 내용 크기로 맞춘다: " + rect(e, "c").w());
    }

    @Test
    void addingToAFixedSizeColumnGrowsItToFitItsContent() throws Exception {
        EditorController e = editor("""
            <screen>
              <column id="c" x="20" y="40" width="20" height="20"/>
            </screen>""");
        e.select(1);
        e.add("button");
        var c = e.document().query("#c");
        var kid = e.document().layout().children.get(0).children.get(0);
        assertTrue(kid.x + kid.w <= c.x() + c.width() && kid.y + kid.h <= c.y() + c.height(),
                "넣은 요소가 컨테이너 안에 들어간다");
    }

    // ---- (c) 클릭 이벤트 라벨 + 자동 완성 ----

    private static final String BUTTON = "<screen><button id=\"b\" x=\"20\" y=\"40\" width=\"60\" height=\"20\" text=\"hi\"/></screen>";

    @Test
    void onClickIsLabelledAndSuggestsRegisteredEvents() throws Exception {
        EditorController e = editor(BUTTON);
        e.select(1);
        assertTrue(hasText(paint(e), "클릭 이벤트"));
        clickRow(e, 4);                                            // 클릭 이벤트 칸
        assertTrue(hasText(paint(e), "buy_item"), "빈 칸이면 전체 후보");
        type(e, "op");
        DrawList l = paint(e);
        assertTrue(hasText(l, "open_shop"));
        assertFalse(hasText(l, "buy_item"), "입력한 글자로 걸러진다");
    }

    @Test
    void tabAcceptsTheFirstSuggestionAndArrowsPickOthers() throws Exception {
        EditorController e = editor(BUTTON);
        e.select(1);
        paint(e);
        clickRow(e, 4);
        paint(e);
        e.keyPressed(EditorKey.DOWN, false, false);
        paint(e);
        e.keyPressed(EditorKey.DOWN, false, false);                // 두 번째 후보(close)
        e.keyPressed(EditorKey.ENTER, false, false);               // 선택된 후보를 채운다(확정은 아님)
        paint(e);
        e.keyPressed(EditorKey.ENTER, false, false);               // 이제 확정
        assertTrue(e.text().contains("onClick=\"close\""), e.text());
    }

    @Test
    void clickingASuggestionFillsTheField() throws Exception {
        EditorController e = editor(BUTTON);
        e.select(1);
        paint(e);
        clickRow(e, 4);
        type(e, "shop");
        DrawList l = paint(e);
        DrawCommand.DrawText t = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals("open_shop"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        click(e, t.x() + 2, t.y() + 2);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("onClick=\"open_shop\""), e.text());
    }

    // ---- (d) 컬러피커 ----

    private static final String COLORED = "<screen><panel id=\"a\" x=\"170\" y=\"40\" width=\"60\" height=\"30\" background=\"#FF336699\"/></screen>";
    private static final int BG_ROW = 13;

    @Test
    void colorFieldsOpenAPickerAndTheTextStillWorks() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        paint(e);
        clickRow(e, BG_ROW);
        DrawList l = paint(e);
        assertTrue(presetRect(l, 0xFFFF5555) != null, "컬러피커의 기본 색 칸이 보인다");
        e.keyPressed(EditorKey.A, true, false);
        type(e, "#00FF00");
        assertTrue(e.text().contains("#00FF00"), "직접 입력하면 바로 화면에 미리 보인다");
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("background=\"#00FF00\"") || e.text().contains("#00FF00"));
    }

    @Test
    void pickingAColorPreviewsItAndEscapeRestoresTheOldOne() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        paint(e);
        String before = e.text();
        clickRow(e, BG_ROW);
        Rect p = presetRect(paint(e), 0xFFFF5555);
        click(e, p.x() + p.w() / 2, p.y() + p.h() / 2);
        assertTrue(e.text().contains("#FF5555"), "고르면 바로 반영: " + e.text());
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(before, e.text());
        assertFalse(e.document().layout() == null);
    }

    @Test
    void pickingAColorAndConfirmingIsOneUndoStep() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        paint(e);
        String before = e.text();
        clickRow(e, BG_ROW);
        Rect p = presetRect(paint(e), 0xFFFF5555);
        click(e, p.x() + p.w() / 2, p.y() + p.h() / 2);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("#FF5555"));
        e.undo();
        assertEquals(before, e.text());
    }

    private static Rect presetRect(DrawList l, int argb) {
        for (DrawCommand c : l.commands()) {
            if (c instanceof DrawCommand.FillRect f && f.w() == 12 && f.h() == 8 && f.argb() == argb) {
                return new Rect(f.x(), f.y(), f.w(), f.h());
            }
        }
        return null;
    }

    // ---- (e) 호버/눌림/커서 ----

    private static int fillColorAt(DrawList l, int x, int y) {
        int found = 0;
        for (DrawCommand c : l.commands()) {
            if (c instanceof DrawCommand.FillRect f && f.x() <= x && x < f.x() + f.w() && f.y() <= y && y < f.y() + f.h()) found = f.argb();
        }
        return found;
    }

    @Test
    void toolbarButtonsGetHoverAndPressedShading() throws Exception {
        EditorController e = editor(COLORED);
        int x = 8, y = 8;                                          // "Save" 버튼 위
        e.mouseMoved(300, 120);
        int idle = fillColorAt(paint(e), x, y);
        e.mouseMoved(x, y);
        int hover = fillColorAt(paint(e), x, y);
        e.mousePressed(x, y, 0);
        int pressed = fillColorAt(paint(e), x, y);
        e.mouseReleased(x, y, 0);
        assertNotEquals(idle, hover, "호버하면 색이 바뀐다");
        assertNotEquals(hover, pressed, "누르면 또 바뀐다");
    }

    @Test
    void thePointerChangesWithWhatIsUnderIt() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.mouseMoved(300, 120);                                    // 빈 캔버스... (오른쪽 패널 위일 수 있으니 왼쪽 위쪽으로)
        e.mouseMoved(100, 200);
        assertEquals(Cursor.DEFAULT, cursor(paint(e)));
        e.mouseMoved(8, 8);                                        // 도구 모음 버튼
        assertEquals(Cursor.POINTER, cursor(paint(e)));
        Rect r = rect(e, "a");
        e.mouseMoved(r.x() + r.w() / 2, r.y() + r.h() / 2);        // 선택한 요소 몸통
        assertEquals(Cursor.MOVE, cursor(paint(e)));
        e.mouseMoved(r.x() + r.w(), r.y() + r.h() / 2);            // 동쪽 손잡이
        assertEquals(Cursor.RESIZE_H, cursor(paint(e)));
        e.mouseMoved(r.x() + r.w() / 2, r.y() + r.h());            // 남쪽 손잡이
        assertEquals(Cursor.RESIZE_V, cursor(paint(e)));
        clickRow(e, BG_ROW);
        e.mouseMoved(FIELD_X, EditorController.TOOLBAR_H + 3 + BG_ROW * (EditorController.ROW_H + 1) + 3);
        assertEquals(Cursor.TEXT, cursor(paint(e)), "편집 중인 칸 위에서는 글자 커서");
    }

    // ---- 닫기 / 템플릿 / IME ----

    @Test
    void theEditorCanBeClosedWithTheExitButtonOrEscWithNothingSelected() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(0, lastHost.exits, "선택이 있으면 Esc 는 선택만 푼다");
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(1, lastHost.exits);
        DrawList l = paint(e);
        DrawCommand.DrawText t = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals("Exit"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        click(e, t.x() + 2, t.y() + 2);
        assertEquals(2, lastHost.exits);
    }

    @Test
    void useElementsAppearInTheTreeAndTheirParametersAreEditable() throws Exception {
        EditorController e = editor("""
            <screen>
              <template id="b"><button text="${text}"/></template>
              <row id="r" x="150" y="40"><use template="b" text="Hello"/></row>
            </screen>""");
        e.select(4);                                               // <use>
        DrawList l = paint(e);
        assertTrue(hasText(l, "use"), "속성 패널에 use 가 보인다");
        assertTrue(hasText(l, "Hello"));
    }

    @Test
    void caretPositionIsAvailableWhileEditingForTheImeWindow() throws Exception {
        EditorController e = editor(BUTTON);
        e.select(1);
        paint(e);
        assertEquals(null, e.caretScreen());
        clickRow(e, 3);
        paint(e);
        assertTrue(e.caretScreen() != null);
    }

    // ---- 저장 확인 / 툴바 숨기기 / 임시 패널 탭 ----

    private static Rect textRect(DrawList l, String t) {
        DrawCommand.DrawText d = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText x && x.text().equals(t))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        return new Rect(d.x(), d.y(), 4, 4);
    }

    @Test
    void closingWithUnsavedChangesAsksFirst() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);                  // 변경 만들기
        e.requestExit();
        assertEquals(0, lastHost.exits);
        assertTrue(hasText(paint(e), "저장하지 않은 변경이 있습니다."));
        e.keyPressed(EditorKey.ESCAPE, false, false);                 // 취소
        assertEquals(0, lastHost.exits);
        assertFalse(hasText(paint(e), "저장하지 않은 변경이 있습니다."));
        e.requestExit();
        Rect no = textRect(paint(e), "저장 안 함");
        click(e, no.x() + 2, no.y() + 2);
        assertEquals(1, lastHost.exits);
    }

    @Test
    void saveAndCloseSavesThenExits() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);
        e.requestExit();
        paint(e);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertEquals(1, lastHost.exits);
        assertFalse(e.isDirty());
    }

    @Test
    void hiddenToolbarShowsAfterHoveringTheTopEdgeForAWhile() throws Exception {
        lastHost = new Host();
        EditorController e = new EditorController(lastHost, new FixedMetrics(), COLORED, 700, H);   // 좁은 창에서는 Hide 버튼을 생략한다
        long[] now = {0};
        e.clock = () -> now[0];
        Rect hide = textRect(paint(e), "Hide");
        click(e, hide.x() + 2, hide.y() + 2);
        e.mouseMoved(300, 120);
        assertFalse(hasText(paint(e), "Save"), "숨겼다");
        e.mouseMoved(200, 1);
        paint(e);
        now[0] += 500_000_000L;
        assertFalse(hasText(paint(e), "Save"), "아직 짧게 올려 뒀다");
        now[0] += 1_000_000_000L;
        assertTrue(hasText(paint(e), "Save"), "1~2초 올려 두면 나타난다");
        e.mouseMoved(200, 100);
        assertFalse(hasText(paint(e), "Save"), "벗어나면 다시 숨는다");
    }

    @Test
    void toolbarIsHiddenWhileMovingAnElement() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        paint(e);
        e.mousePressed(200, 55, 0);
        e.mouseDragged(210, 70);
        assertFalse(hasText(paint(e), "Save"));
        e.mouseReleased(210, 70, 0);
        assertTrue(hasText(paint(e), "Save"));
    }

    @Test
    void theTabOfATemporarilyShownPanelStaysAndDismissesIt() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        int tabY = EditorController.tabY(H) + 5, rightTabX = W - 142 - 10 + 3;
        click(e, rightTabX, tabY);                                    // 접기
        e.select(-1);
        assertFalse(hasText(paint(e), "너비"));
        e.select(1);
        assertTrue(hasText(paint(e), "너비"), "선택하면 임시로 보인다");
        click(e, rightTabX, tabY);                                    // 임시로 보이는 동안에도 탭이 있어 닫을 수 있다
        assertFalse(hasText(paint(e), "너비"));
        e.select(-1);
        paint(e);
        e.select(1);
        assertTrue(hasText(paint(e), "너비"), "다시 선택하면 또 임시로 보인다");
    }

    @Test
    void longHintTextScrollsAndToastsFadeAway() throws Exception {
        assertEquals(0, EditorController.marqueeOffset(100, 0));
        assertEquals(100, EditorController.marqueeOffset(100, 4_700_000_000L));
        EditorController e = editor(COLORED);
        long[] now = {0};
        e.clock = () -> now[0];
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);
        e.save();
        assertTrue(hasText(paint(e), "저장됨"));
        now[0] += 1_000_000_000L;
        assertTrue(hasText(paint(e), "저장됨"));
        now[0] += 10_000_000_000L;
        assertFalse(hasText(paint(e), "저장됨"), "시간이 지나면 사라진다");
    }

    @Test
    void thingsBehindTheExitDialogGetNoHover() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);
        e.requestExit();
        e.mouseMoved(8, 8);                                       // 뒤에 있는 Save 버튼 자리
        int c = 0;
        for (DrawCommand d : paint(e).commands()) {
            if (d instanceof DrawCommand.FillRect f && f.x() == 2 && f.y() == 2) c = f.argb();
        }
        e.requestExit();
        assertNotEquals(0xFF3A3F55 + 0x202020, c, "호버 색이 아니다");
    }

    @Test
    void elementsBehindTheExitDialogGetNoHoverOutline() throws Exception {
        EditorController e = editor(COLORED);
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);
        Rect r = rect(e, "a");
        e.mouseMoved(r.x() + 3, r.y() + 3);
        e.select(-1);
        assertTrue(paint(e).commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0x9055FFFF),
                "대화상자가 없으면 호버 윤곽이 보인다");
        e.requestExit();
        assertFalse(paint(e).commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0x9055FFFF),
                "대화상자가 떠 있으면 없다");
    }

    @Test
    void collapsedSidePanelsPeekOutWhenTheMouseRestsOnTheEdge() throws Exception {
        EditorController e = editor(COLORED);
        long[] now = {0};
        e.clock = () -> now[0];
        e.select(-1);
        click(e, EditorController.LEFT_W + 3, EditorController.tabY(H) + 5);   // 왼쪽 패널 접기
        e.mouseMoved(300, 120);
        assertFalse(hasText(paint(e), "구조"));
        e.mouseMoved(1, 100);
        paint(e);
        now[0] += 500_000_000L;
        assertFalse(hasText(paint(e), "구조"), "아직");
        now[0] += 1_000_000_000L;
        assertTrue(hasText(paint(e), "구조"), "기다리면 나타난다");
        e.mouseMoved(300, 120);
        assertFalse(hasText(paint(e), "구조"), "벗어나면 다시 숨는다");
    }

    @Test
    void panelAndToolbarFoldingIsSavedAndRestored() throws Exception {
        EditorController e = editor(COLORED);
        click(e, EditorController.LEFT_W + 3, EditorController.tabY(H) + 5);   // 왼쪽 접기
        paint(e);
        assertEquals("true", lastHost.prefs.get("leftCollapsed"));
        assertEquals(1, lastHost.saves);
        paint(e);
        assertEquals(1, lastHost.saves, "바뀌지 않으면 다시 쓰지 않는다");
        Host h = lastHost;
        EditorController again = new EditorController(h, new FixedMetrics(), COLORED, W, H);
        assertFalse(hasText(paint(again), "구조"), "다시 열어도 접혀 있다");
    }
}
