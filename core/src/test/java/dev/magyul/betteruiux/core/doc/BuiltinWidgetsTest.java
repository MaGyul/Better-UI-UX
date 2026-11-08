package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 내장 입력/레이아웃 태그: checkbox radio slider progress textfield scroll tabs select grid separator item dialog + tooltip. */
class BuiltinWidgetsTest {
    private final List<String> actions = new ArrayList<>();

    @AfterEach
    void cleanup() { UiActions.unregister("changed"); }

    private UiDocument doc(String body) throws Exception {
        UiActions.register("changed", ctx -> actions.add(ctx.source().id() + ":" + ctx.source().attr("value") + ":" + ctx.source().attr("checked")));
        UiDocument d = UiDocument.parse("<screen>" + body + "</screen>", new FixedMetrics());
        d.layout(400, 300);
        return d;
    }

    private static void click(UiDocument d, int x, int y) {
        d.mousePressed(x, y, 0);
        d.mouseReleased(x, y, 0);
    }

    private static DrawList paint(UiDocument d, int mx, int my) {
        DrawList l = new DrawList();
        d.paint(l, mx, my);
        return l;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    // ---- checkbox / radio ----

    @Test
    void checkboxTogglesOnClickAndFiresChange() throws Exception {
        UiDocument d = doc("<checkbox id=\"c\" x=\"20\" y=\"20\" text=\"Sound\" onChange=\"changed\"/>");
        UiElement c = d.query("#c");
        assertNull(c.attr("checked"));
        click(d, c.x() + 3, c.y() + 3);
        assertEquals("true", c.attr("checked"));
        assertEquals(List.of("c:null:true"), actions);
        click(d, c.x() + 3, c.y() + 3);
        assertNull(c.attr("checked"));
        assertEquals(2, actions.size());
    }

    @Test
    void checkboxAutoWidthIncludesTheLabel() throws Exception {
        UiDocument d = doc("<checkbox id=\"c\" text=\"Sound\"/>");
        assertEquals(10 + 4 + 5 * 6, d.query("#c").width());
    }

    @Test
    void disabledCheckboxIgnoresClicks() throws Exception {
        UiDocument d = doc("<checkbox id=\"c\" x=\"20\" y=\"20\" text=\"S\" enabled=\"false\"/>");
        UiElement c = d.query("#c");
        click(d, c.x() + 3, c.y() + 3);
        assertNull(c.attr("checked"));
    }

    @Test
    void radiosInTheSameGroupAreExclusive() throws Exception {
        UiDocument d = doc("<column x=\"10\" y=\"10\"><radio id=\"a\" group=\"g\" text=\"A\" checked=\"true\"/>"
                + "<radio id=\"b\" group=\"g\" text=\"B\"/></column>");
        UiElement b = d.query("#b");
        click(d, b.x() + 3, b.y() + 3);
        assertEquals("true", b.attr("checked"));
        assertNull(d.query("#a").attr("checked"));
        click(d, b.x() + 3, b.y() + 3);
        assertEquals("true", b.attr("checked"), "이미 켜진 라디오는 꺼지지 않는다");
    }

    // ---- slider ----

    @Test
    void sliderFollowsTheMouseAndFiresChangeOnRelease() throws Exception {
        UiDocument d = doc("<slider id=\"s\" x=\"20\" y=\"20\" width=\"102\" min=\"0\" max=\"100\" step=\"10\" onChange=\"changed\"/>");
        UiElement s = d.query("#s");
        d.mousePressed(s.x() + 1 + 50, s.y() + 3, 0);
        assertEquals("50", s.attr("value"));
        d.mouseDragged(s.x() + 1 + 83, s.y() + 3);
        assertEquals("80", s.attr("value"), "step 10 으로 맞춰진다");
        assertTrue(actions.isEmpty(), "끄는 중에는 알리지 않는다");
        d.mouseReleased(s.x() + 1 + 83, s.y() + 3, 0);
        assertEquals(1, actions.size());
        d.mousePressed(s.x() - 50, s.y() + 3, 0);
    }

    @Test
    void sliderKeysStepWhenFocused() throws Exception {
        UiDocument d = doc("<slider id=\"s\" x=\"20\" y=\"20\" min=\"0\" max=\"10\" value=\"5\"/>");
        UiElement s = d.query("#s");
        d.mousePressed(s.x() + 1 + 49, s.y() + 3, 0);          // 포커스 + 값 설정(대략 중간)
        d.mouseReleased(s.x() + 1 + 49, s.y() + 3, 0);
        assertEquals(s, d.focusedElement());
        double before = Double.parseDouble(s.attr("value"));
        assertTrue(d.keyPressed(UiKey.RIGHT, false, false));
        assertEquals(before + 1, Double.parseDouble(s.attr("value")));
        d.keyPressed(UiKey.END, false, false);
        assertEquals("10", s.attr("value"));
    }

    // ---- progress ----

    @Test
    void progressFillsProportionallyAndFollowsBindings() throws Exception {
        UiDocument d = doc("<progress id=\"p\" x=\"10\" y=\"10\" width=\"102\" value=\"{hp}\" max=\"20\"/>");
        d.set("hp", 10);
        d.layout(400, 300);
        DrawList l = paint(d, -1, -1);
        long fill = l.commands().stream().filter(c -> c instanceof DrawCommand.FillRect f && f.w() == 50 && f.argb() == 0xFF55FF55).count();
        assertEquals(1, fill, "절반 채움(100 * 0.5)");
    }

    // ---- textfield ----

    @Test
    void textfieldTypesEditsAndFiresChange() throws Exception {
        UiDocument d = doc("<textfield id=\"t\" x=\"20\" y=\"20\" width=\"100\" onChange=\"changed\" onSubmit=\"changed\"/>");
        UiElement t = d.query("#t");
        paint(d, -1, -1);
        click(d, t.x() + 5, t.y() + 5);
        assertEquals(t, d.focusedElement());
        for (char c : "hello".toCharArray()) d.charTyped(c);
        assertEquals("hello", t.attr("value"));
        d.keyPressed(UiKey.BACKSPACE, false, false);
        assertEquals("hell", t.attr("value"));
        d.keyPressed(UiKey.A, true, false);
        d.charTyped('X');
        assertEquals("X", t.attr("value"), "전체 선택 후 입력하면 바뀐다");
        assertEquals(7, actions.size());
        d.keyPressed(UiKey.ENTER, false, false);
        assertEquals(8, actions.size(), "Enter 는 onSubmit");
    }

    @Test
    void textfieldClipboardAndMaxLength() throws Exception {
        UiDocument d = doc("<textfield id=\"t\" x=\"20\" y=\"20\" width=\"100\" maxlength=\"4\" value=\"ab\"/>");
        String[] clip = {""};
        d.setClipboardHandler(() -> clip[0], s -> clip[0] = s);
        UiElement t = d.query("#t");
        paint(d, -1, -1);
        click(d, t.x() + 5, t.y() + 5);
        d.keyPressed(UiKey.A, true, false);
        d.keyPressed(UiKey.C, true, false);
        assertEquals("ab", clip[0]);
        d.keyPressed(UiKey.END, false, false);
        clip[0] = "WXYZ";
        d.keyPressed(UiKey.V, true, false);
        assertEquals("abWX", t.attr("value"), "최대 길이에서 잘린다");
    }

    @Test
    void textfieldReportsTextInputAndTabMovesFocus() throws Exception {
        UiDocument d = doc("<textfield id=\"a\" x=\"20\" y=\"20\" width=\"100\"/><textfield id=\"b\" x=\"20\" y=\"60\" width=\"100\"/>");
        List<Boolean> events = new ArrayList<>();
        d.setTextInputHandler((on, x, y, w, h) -> events.add(on));
        paint(d, -1, -1);
        click(d, d.query("#a").x() + 5, d.query("#a").y() + 5);
        assertEquals(List.of(true), events);
        assertNotNull(d.caretScreen());
        d.keyPressed(UiKey.TAB, false, false);
        assertEquals(d.query("#b"), d.focusedElement());
        click(d, 300, 250);                                           // 빈 곳을 누르면 포커스 해제
        assertNull(d.focusedElement());
        assertEquals(false, events.get(events.size() - 1));
    }

    // ---- scroll ----

    private static final String LIST = "<scroll id=\"s\" x=\"20\" y=\"20\" width=\"100\" height=\"50\">"
            + "<label id=\"l1\" text=\"A\" height=\"20\"/><label id=\"l2\" text=\"B\" height=\"20\"/>"
            + "<label id=\"l3\" text=\"C\" height=\"20\"/><label id=\"l4\" text=\"D\" height=\"20\"/></scroll>";

    @Test
    void scrollMovesContentWithTheWheelAndClampsAtTheEnds() throws Exception {
        UiDocument d = doc(LIST);
        int y0 = d.query("#l1").y();
        assertTrue(d.mouseScrolled(40, 30, -1.0), "아래로 휠");
        assertEquals(y0 - 12, d.query("#l1").y());
        for (int i = 0; i < 20; i++) d.mouseScrolled(40, 30, -1.0);
        assertEquals(30, y0 - d.query("#l1").y(), "내용 80 - 보이는 50 = 30 까지만");
        for (int i = 0; i < 20; i++) d.mouseScrolled(40, 30, 1.0);
        assertEquals(y0, d.query("#l1").y());
    }

    @Test
    void scrollOnlyHitsContentInsideItsViewport() throws Exception {
        UiDocument d = doc(LIST + "<button id=\"b\" x=\"200\" y=\"200\" text=\"x\"/>");
        assertEquals("s", d.hitTest(40, 30).parent.id() == null ? d.hitTest(40, 30).id() : d.hitTest(40, 30).parent.id());
        assertNull(d.hitTest(40, 80).id() == null || !"l4".equals(d.hitTest(40, 80).id()) ? null : "found", "뷰포트 밖의 잘린 항목은 눌리지 않는다");
    }

    @Test
    void scrollbarThumbDragMovesTheContent() throws Exception {
        UiDocument d = doc(LIST);
        UiElement s = d.query("#s");
        d.mousePressed(s.x() + s.width() - 2, s.y() + 5, 0);
        d.mouseDragged(s.x() + s.width() - 2, s.y() + 200);
        d.mouseReleased(s.x() + s.width() - 2, s.y() + 200, 0);
        assertEquals(30, s.y() - d.query("#l1").y());
    }

    @Test
    void scrollContentIsClippedWhenPainted() throws Exception {
        UiDocument d = doc(LIST);
        DrawList l = paint(d, -1, -1);
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.PushClip p && p.w() == 100 && p.h() == 50));
    }

    // ---- grid ----

    @Test
    void gridPlacesItemsInColumnsAndRows() throws Exception {
        UiDocument d = doc("<grid id=\"g\" x=\"10\" y=\"10\" columns=\"3\" gap=\"2\">"
                + "<panel id=\"a\" width=\"10\" height=\"8\"/><panel id=\"b\" width=\"20\" height=\"8\"/><panel id=\"c\" width=\"10\" height=\"8\"/>"
                + "<panel id=\"d\" width=\"10\" height=\"12\"/></grid>");
        assertEquals(10, d.query("#a").x());
        assertEquals(10 + 10 + 2, d.query("#b").x());
        assertEquals(10 + 10 + 2 + 20 + 2, d.query("#c").x());
        assertEquals(10, d.query("#d").x(), "다음 줄은 처음 열부터");
        assertEquals(10 + 8 + 2, d.query("#d").y());
        assertEquals(10 + 20 + 10 + 4, d.query("#g").width(), "자동 너비 = 열 너비 합 + 간격");
        assertEquals(8 + 2 + 12, d.query("#g").height());
    }

    // ---- tabs ----

    @Test
    void tabsShowOnlyTheSelectedTabAndSwitchOnClick() throws Exception {
        UiDocument d = doc("<tabs id=\"t\" x=\"10\" y=\"10\" width=\"200\" height=\"100\">"
                + "<tab title=\"One\"><label id=\"l1\" text=\"first\"/></tab><tab title=\"Two\"><label id=\"l2\" text=\"second\"/></tab></tabs>");
        DrawList l = paint(d, -1, -1);
        assertTrue(hasText(l, "first"));
        assertFalse(hasText(l, "second"));
        UiElement t = d.query("#t");
        click(d, t.x() + 14 + 3 + 6 + 20, t.y() + 3);                   // 두 번째 탭 머리글
        l = paint(d, -1, -1);
        assertFalse(hasText(l, "first"));
        assertTrue(hasText(l, "second"));
        assertTrue(d.query("#l2").y() >= t.y() + 10, "머리글 아래에 놓인다");
    }

    // ---- select ----

    @Test
    void selectOpensAPopupAndPickingAnOptionSetsTheValue() throws Exception {
        UiDocument d = doc("<select id=\"s\" x=\"20\" y=\"20\" width=\"80\" options=\"red,green,blue\" value=\"red\" onChange=\"changed\"/>");
        UiElement s = d.query("#s");
        paint(d, -1, -1);
        click(d, s.x() + 5, s.y() + 5);
        DrawList l = paint(d, -1, -1);
        assertTrue(hasText(l, "green") && hasText(l, "blue"), "팝업에 선택지가 보인다");
        DrawCommand.DrawText green = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("green"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        click(d, green.x() + 2, green.y() + 2);
        assertEquals("green", s.attr("value"));
        assertEquals(1, actions.size());
        assertFalse(hasText(paint(d, -1, -1), "blue"), "고르면 팝업이 닫힌다");
    }

    // ---- separator / item / dialog / tooltip ----

    @Test
    void separatorDrawsALineAcrossAColumn() throws Exception {
        UiDocument d = doc("<column x=\"10\" y=\"10\" width=\"100\" height=\"50\"><label text=\"a\"/><separator id=\"s\"/><label text=\"b\"/></column>");
        assertEquals(100, d.query("#s").width());
        assertEquals(1, d.query("#s").height());
    }

    @Test
    void itemDrawsAnItemIconWithItsCount() throws Exception {
        UiDocument d = doc("<item id=\"i\" item=\"minecraft:diamond\" count=\"3\" x=\"10\" y=\"10\"/>");
        DrawList l = paint(d, -1, -1);
        assertTrue(l.commands().contains(new DrawCommand.DrawItem("minecraft:diamond", 10, 10, 3)));
        assertEquals(16, d.query("#i").width());
    }

    @Test
    void modalDialogBlocksClicksOutsideAndDimsTheScreen() throws Exception {
        List<String> clicks = new ArrayList<>();
        UiActions.register("hit", ctx -> clicks.add(ctx.source().id()));
        try {
            UiDocument d = UiDocument.parse("<screen><button id=\"under\" x=\"10\" y=\"10\" text=\"under\" onClick=\"hit\"/>"
                    + "<dialog id=\"dlg\" width=\"120\" height=\"60\"><button id=\"ok\" text=\"OK\" onClick=\"hit\"/></dialog></screen>", new FixedMetrics());
            d.layout(400, 300);
            click(d, 15, 15);
            assertTrue(clicks.isEmpty(), "대화상자 밖의 버튼은 눌리지 않는다");
            UiElement ok = d.query("#ok");
            click(d, ok.x() + 2, ok.y() + 2);
            assertEquals(List.of("ok"), clicks);
            DrawList l = paint(d, -1, -1);
            assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.w() == 400 && f.h() == 300 && f.argb() == 0x90000000));
        } finally {
            UiActions.unregister("hit");
        }
    }

    @Test
    void tooltipAppearsAfterHoveringForAWhile() throws Exception {
        UiDocument d = doc("<panel id=\"p\" x=\"20\" y=\"20\" width=\"50\" height=\"30\" tooltip=\"Hello tip\"/>");
        long[] now = {0};
        d.setClock(() -> now[0]);
        paint(d, 30, 30);
        assertFalse(hasText(paint(d, 30, 30), "Hello tip"), "바로는 나오지 않는다");
        now[0] += 600;
        assertTrue(hasText(paint(d, 30, 30), "Hello tip"));
        assertFalse(hasText(paint(d, 300, 200), "Hello tip"), "벗어나면 사라진다");
    }

    @Test
    void spaceTogglesAFocusedCheckbox() throws Exception {
        UiDocument d = doc("<checkbox id=\"c\" x=\"20\" y=\"20\" text=\"S\"/><button id=\"b\" x=\"100\" y=\"100\" text=\"b\"/>");
        d.keyPressed(UiKey.TAB, false, false);
        assertEquals(d.query("#c"), d.focusedElement());
        assertTrue(d.keyPressed(UiKey.SPACE, false, false));
        assertEquals("true", d.query("#c").attr("checked"));
        d.keyPressed(UiKey.SPACE, false, false);
        assertNull(d.query("#c").attr("checked"));
    }

    @Test
    void selectOpensWithEnterAndArrowsPickWithKeyboard() throws Exception {
        UiDocument d = doc("<select id=\"s\" x=\"20\" y=\"20\" width=\"80\" options=\"red,green,blue\" value=\"red\" onChange=\"changed\"/>");
        d.keyPressed(UiKey.TAB, false, false);
        assertTrue(d.keyPressed(UiKey.ENTER, false, false));
        assertTrue(hasText(paint(d, -1, -1), "blue"), "Enter 로 열린다");
        d.keyPressed(UiKey.DOWN, false, false);
        d.keyPressed(UiKey.DOWN, false, false);
        d.keyPressed(UiKey.ENTER, false, false);
        assertEquals("blue", d.query("#s").attr("value"));
        assertFalse(hasText(paint(d, -1, -1), "green"), "고르면 닫힌다");
        d.keyPressed(UiKey.SPACE, false, false);                     // 다시 열고 Esc 로 닫기
        assertTrue(hasText(paint(d, -1, -1), "green"));
        d.keyPressed(UiKey.ESCAPE, false, false);
        assertFalse(hasText(paint(d, -1, -1), "green"));
    }

    @Test
    void widgetBackgroundColorsFollowTheBackgroundStyle() throws Exception {
        for (String tag : new String[] {"slider", "progress", "textfield", "select", "checkbox"}) {
            UiDocument d = doc("<" + tag + " id=\"w\" x=\"20\" y=\"20\" width=\"80\" height=\"14\" text=\"t\" background=\"#FF123456\"/>");
            DrawList l = paint(d, -1, -1);
            assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFF123456), tag);
        }
    }

    @Test
    void cssVariablesCanBeChangedFromJava() throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>panel { background: var(--accent, #FF000000) }</style>"
                + "<panel id=\"p\" width=\"10\" height=\"10\"/></screen>", new FixedMetrics());
        d.layout(100, 100);
        assertTrue(paint(d, -1, -1).commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFF000000 && f.w() == 10));
        d.setVariable("accent", "#FF00FF00");
        assertTrue(paint(d, -1, -1).commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFF00FF00 && f.w() == 10));
        assertEquals("#FF00FF00", d.variable("--accent"));
        d.setVariable("--accent", null);
        paint(d, -1, -1);
        assertNull(d.variable("accent"));
    }
}
