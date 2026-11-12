package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiApiTest {
    private static final String XML = """
        <screen>
          <column id='list' gap='2'>
            <label id='title' class='t' text='Hi'/>
            <button id='a' class='item' text='A' width='40' height='10'/>
            <button id='b' class='item' text='B' width='40' height='10'/>
          </column>
        </screen>""";

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void tearDown() { cleanup.forEach(Runnable::run); }

    private UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(200, 100);
        return d;
    }

    private static List<DrawCommand> paint(UiDocument d, int mx, int my) {
        DrawList l = new DrawList();
        d.paint(l, mx, my);
        return l.commands();
    }

    // ---- 조회/수정 ----

    @Test
    void queryWorksBeforeLayoutAndReturnsDocumentOrder() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());   // layout() 호출 전
        assertEquals("title", d.query("#title").id());
        assertEquals(List.of("a", "b"), d.queryAll(".item").stream().map(UiElement::id).toList());
        assertEquals("b", d.query("column > button:last-child").id());
        assertNull(d.query("#none"));
        assertThrows(IllegalArgumentException.class, () -> d.query("a:bogus"));
    }

    @Test
    void textClassesAndStyleChangesApplyOnNextPaint() throws Exception {
        UiDocument d = doc("<screen><style>.hot { color: #FF0000 }</style><label id='l' text='old'/></screen>");
        UiElement l = d.query("#l");
        l.setText("new").addClass("hot");
        assertTrue(l.hasClass("hot"));
        List<DrawCommand> c = paint(d, -1, -1);
        assertTrue(c.contains(new DrawCommand.DrawText("new", 0, 0, 0xFFFF0000, true)), c.toString());

        l.removeClass("hot").setStyle("color", "#00FF00");
        assertTrue(paint(d, -1, -1).contains(new DrawCommand.DrawText("new", 0, 0, 0xFF00FF00, true)));
        l.setStyle("color", null);
        assertNull(l.attr("style"));
        assertEquals("#00FF00", l.document().query("#l").style("color"), "computed 값은 재레이아웃 전의 값");
        paint(d, -1, -1);
        assertNull(d.query("#l").style("color"));
    }

    @Test
    void appendChildAndRemoveRelayout() throws Exception {
        UiDocument d = doc(XML);
        UiElement list = d.query("#list");
        int h0 = list.height();
        UiElement added = list.appendChild("<button id='c' text='C' width='40' height='10'/>");
        paint(d, -1, -1);
        assertEquals(h0 + 12, list.height());
        assertEquals("c", d.query("#c").id());
        added.remove();
        paint(d, -1, -1);
        assertEquals(h0, list.height());
        assertNull(d.query("#c"));
    }

    @Test
    void setVisibleAndEnabled() throws Exception {
        UiDocument d = doc(XML);
        d.query("#a").setVisible(false);
        paint(d, -1, -1);
        assertEquals(0, d.query("#b").y() - d.query("#list").y() - 9 - 2, "a 가 사라지면 b 가 label 바로 아래로 올라온다");
        d.query("#b").setEnabled(false);
        assertEquals("false", d.query("#b").attr("enabled"));
        d.query("#b").setEnabled(true);
        assertNull(d.query("#b").attr("enabled"));
    }

    // ---- 이벤트 ----

    @Test
    void clickBubblesAndStopPropagationWorks() throws Exception {
        UiDocument d = doc(XML);
        List<String> log = new ArrayList<>();
        d.query("#a").on(UiEvent.Type.CLICK, e -> log.add("a:" + e.currentTarget().id() + "<-" + e.target().id()));
        d.query("#list").on(UiEvent.Type.CLICK, e -> log.add("list"));
        var a = d.query("#a");
        int cx = a.x() + 2, cy = a.y() + 2;
        assertTrue(d.mousePressed(cx, cy, 0));
        assertTrue(d.mouseReleased(cx, cy, 0));
        assertEquals(List.of("a:a<-a", "list"), log);

        log.clear();
        d.query("#a").on(UiEvent.Type.CLICK, UiEvent::stopPropagation);
        d.mousePressed(cx, cy, 0);
        d.mouseReleased(cx, cy, 0);
        assertEquals(List.of("a:a<-a"), log, "stopPropagation 이후에는 list 핸들러가 호출되지 않는다");
    }

    @Test
    void delegatedHandlerSeesLaterAddedElementsAndCanBeRemoved() throws Exception {
        UiDocument d = doc(XML);
        List<String> log = new ArrayList<>();
        Runnable off = d.on(".item", UiEvent.Type.CLICK, e -> log.add(e.currentTarget().id()));
        UiElement c = d.query("#list").appendChild("<button id='c' class='item' text='C' width='40' height='10'/>");
        paint(d, -1, -1);
        d.mousePressed(c.x() + 1, c.y() + 1, 0);
        d.mouseReleased(c.x() + 1, c.y() + 1, 0);
        assertEquals(List.of("c"), log);
        off.run();
        d.mousePressed(c.x() + 1, c.y() + 1, 0);
        d.mouseReleased(c.x() + 1, c.y() + 1, 0);
        assertEquals(1, log.size());
    }

    @Test
    void releasingOnAnotherElementDoesNotClickButFiresRelease() throws Exception {
        UiDocument d = doc(XML);
        List<String> log = new ArrayList<>();
        d.on("button", UiEvent.Type.CLICK, e -> log.add("click:" + e.target().id()));
        d.on("button", UiEvent.Type.RELEASE, e -> log.add("release:" + e.target().id()));
        var a = d.query("#a");
        var b = d.query("#b");
        d.mousePressed(a.x() + 1, a.y() + 1, 0);
        d.mouseReleased(b.x() + 1, b.y() + 1, 0);
        assertEquals(List.of("release:b"), log);
    }

    @Test
    void hoverEnterAndLeaveFireOncePerChange() throws Exception {
        UiDocument d = doc(XML);
        List<String> log = new ArrayList<>();
        d.on("button", UiEvent.Type.HOVER_ENTER, e -> log.add("in:" + e.target().id()));
        d.on("button", UiEvent.Type.HOVER_LEAVE, e -> log.add("out:" + e.target().id()));
        var a = d.query("#a");
        paint(d, a.x() + 1, a.y() + 1);
        paint(d, a.x() + 2, a.y() + 2);   // 같은 요소 안에서 이동 → 이벤트 없음
        paint(d, -1, -1);
        assertEquals(List.of("in:a", "out:a"), log);
    }

    @Test
    void throwingHandlerBecomesWarningAndDoesNotBreakDispatch() throws Exception {
        UiDocument d = doc(XML);
        AtomicInteger second = new AtomicInteger();
        d.on("#a", UiEvent.Type.CLICK, e -> { throw new IllegalStateException("boom"); });
        d.on("#a", UiEvent.Type.CLICK, e -> second.incrementAndGet());
        var a = d.query("#a");
        d.mousePressed(a.x() + 1, a.y() + 1, 0);
        d.mouseReleased(a.x() + 1, a.y() + 1, 0);
        assertEquals(1, second.get());
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("boom")));
    }

    // ---- 액션 ----

    @Test
    void registeredActionRunsWithContextAndUnknownActionWarns() throws Exception {
        UiDocument d = doc("<screen><button id='x' text='X' onClick='test:act' width='30' height='10'/>"
                + "<button id='y' text='Y' onClick='test:nope' y='20' width='30' height='10'/></screen>");
        List<String> log = new ArrayList<>();
        UiActions.register("test:act", ctx -> log.add(ctx.action() + "@" + ctx.source().id()));
        cleanup.add(() -> UiActions.unregister("test:act"));
        var x = d.query("#x");
        d.mousePressed(x.x() + 1, x.y() + 1, 0);
        d.mouseReleased(x.x() + 1, x.y() + 1, 0);
        assertEquals(List.of("test:act@x"), log);

        var y = d.query("#y");
        d.mousePressed(y.x() + 1, y.y() + 1, 0);
        d.mouseReleased(y.x() + 1, y.y() + 1, 0);
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("test:nope")));
    }

    @Test
    void closeActionCallsOnClose() throws Exception {
        UiDocument d = doc("<screen><button id='c' text='C' onClick='close' width='30' height='10'/></screen>");
        AtomicInteger closed = new AtomicInteger();
        d.setOnClose(closed::incrementAndGet);
        var c = d.query("#c");
        d.mousePressed(c.x() + 1, c.y() + 1, 0);
        d.mouseReleased(c.x() + 1, c.y() + 1, 0);
        assertEquals(1, closed.get());
    }

    // ---- 바인딩 ----

    @Test
    void bindingsInterpolateAndRelayoutOnlyWhenValueChanges() throws Exception {
        UiDocument d = doc("<screen><label id='l' text='Coins: {coins} ({x})'/></screen>");
        int[] coins = {5};
        d.bind("coins", () -> coins[0]);
        List<DrawCommand> c = paint(d, -1, -1);
        assertTrue(c.contains(new DrawCommand.DrawText("Coins: 5 ({x})", 0, 0, 0xFFFFFFFF, true)), c.toString());
        int w1 = d.query("#l").width();

        coins[0] = 12345;
        c = paint(d, -1, -1);
        assertTrue(c.contains(new DrawCommand.DrawText("Coins: 12345 ({x})", 0, 0, 0xFFFFFFFF, true)));
        assertTrue(d.query("#l").width() > w1, "텍스트가 길어지면 auto 너비가 다시 계산된다");

        d.set("x", "ok");
        assertTrue(paint(d, -1, -1).contains(new DrawCommand.DrawText("Coins: 12345 (ok)", 0, 0, 0xFFFFFFFF, true)));
        d.unbind("x");
        assertTrue(paint(d, -1, -1).contains(new DrawCommand.DrawText("Coins: 12345 ({x})", 0, 0, 0xFFFFFFFF, true)));
    }

    // ---- 커스텀 위젯 ----

    @Test
    void customWidgetGetsIntrinsicSizeAndPaintsWithFrame() throws Exception {
        List<UiWidget.Frame> frames = new ArrayList<>();
        UiWidgets.register("gauge", el -> new UiWidget() {
            @Override public int intrinsicWidth() { return 30; }

            @Override public int intrinsicHeight() { return 8; }

            @Override public void paint(dev.magyul.betteruiux.api.UiGfx g, int x, int y, int w, int h, Frame f) {
                frames.add(f);
                g.fillRect(x, y, w / 2, h, 0xFF00FF00);
            }
        });
        cleanup.add(() -> UiWidgets.unregister("gauge"));
        UiDocument d = doc("<screen><gauge id='g' margin='5'/></screen>");
        UiElement g = d.query("#g");
        assertEquals(30, g.width());
        assertEquals(8, g.height());
        List<DrawCommand> c = paint(d, 7, 7);
        assertTrue(c.contains(new DrawCommand.FillRect(5, 5, 15, 8, 0xFF00FF00)));
        assertEquals(7, frames.get(0).mouseX());
        assertTrue(frames.get(0).hovered());
        assertEquals("g", frames.get(0).element().id());
    }

    @Test
    void widgetInstanceSurvivesRelayoutAndBuiltinTagsCannotBeRegistered() throws Exception {
        List<Object> seen = new ArrayList<>();
        UiWidgets.register("keep", el -> new UiWidget() {
            @Override public void paint(dev.magyul.betteruiux.api.UiGfx g, int x, int y, int w, int h, Frame f) {
                seen.add(this);
            }
        });
        cleanup.add(() -> UiWidgets.unregister("keep"));
        UiDocument d = doc("<screen><keep width='5' height='5'/></screen>");
        paint(d, -1, -1);
        d.layout(300, 200);
        paint(d, -1, -1);
        assertEquals(2, seen.size());
        assertTrue(seen.get(0) == seen.get(1), "재레이아웃 후에도 같은 위젯 인스턴스를 유지");
        assertThrows(IllegalArgumentException.class, () -> UiWidgets.register("label", el -> null));
        assertThrows(IllegalArgumentException.class, () -> UiWidgets.register("Bad Tag", el -> null));
        assertNotNull(UiActions.isRegistered("close"));
        assertFalse(UiActions.isRegistered("never-registered"));
    }
}
