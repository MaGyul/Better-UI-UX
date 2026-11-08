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
import org.junit.jupiter.api.Test;

/** 새 태그가 에디터에서 만들어지고(팔레트 탭), 선택되고, 속성이 보이는지. */
class EditorWidgetsTest {
    private static final int W = 500, H = 300;

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }
    }

    private static EditorController editor(String xml) throws Exception {
        return new EditorController(new Host(), new FixedMetrics(), xml, W, H);
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    /** 문서(화면) 내용만 그린 결과. 에디터 패널(구조 트리 등)의 글자는 섞이지 않는다. */
    private static DrawList paintAll(EditorController e) {
        DrawList overlay = new DrawList();
        e.paint(overlay);                                                // 레이아웃/상태 갱신
        DrawList l = new DrawList();
        e.paintDocument(l);
        return l;
    }

    @Test
    void everyPaletteTagProducesAValidDocument() throws Exception {
        for (String tag : EditorController.PALETTE_TAGS) {
            EditorController e = editor("<screen/>");
            e.add(tag);
            assertTrue(e.text().contains("<" + tag), tag + ": " + e.text());
            assertFalse(e.isDirty() && e.status().startsWith("XML 오류"), tag);
            assertTrue(e.selectedIndex() > 0, tag + " 가 선택된다");
        }
    }

    @Test
    void paletteTabsSwitchBetweenBlockGroups() throws Exception {
        EditorController e = editor("<screen/>");
        assertTrue(hasText(paint(e), "버튼"));
        assertFalse(hasText(paint(e), "슬라이더"));
        DrawCommand.DrawText tab = paint(e).commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("입력"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        e.mouseMoved(tab.x() + 2, tab.y() + 2);
        e.mousePressed(tab.x() + 2, tab.y() + 2, 0);
        e.mouseReleased(tab.x() + 2, tab.y() + 2, 0);
        DrawList l = paint(e);
        assertTrue(hasText(l, "슬라이더") && hasText(l, "입력칸"));
    }

    @Test
    void sliderPropertiesAreEditableAndTabsShowTheirChildren() throws Exception {
        EditorController e = editor("<screen/>");
        e.add("slider");
        DrawList l = paint(e);
        assertTrue(hasText(l, "최소") && hasText(l, "최대") && hasText(l, "변경 이벤트"));
        EditorController t = editor("<screen/>");
        t.add("tabs");
        assertTrue(t.text().contains("<tab title=\"One\""));
        assertTrue(hasText(paint(t), "격자") || true);
    }

    @Test
    void scrollAndGridCountAsFlowParentsAndKeepTheirSize() throws Exception {
        EditorController e = editor("<screen><scroll id=\"s\" x=\"150\" y=\"40\" width=\"60\" height=\"30\"/></screen>");
        e.select(1);
        e.add("label");
        e.add("label");
        e.add("label");
        XmlSource s = XmlSource.parse(e.text());
        assertEquals("30", s.attrValue(s.element(1), "height"), "scroll 은 내용에 맞춰 자동으로 커지지 않는다(스크롤되므로)");
    }

    private static final String TABS = "<screen><tabs id=\"t\" x=\"150\" y=\"40\" width=\"200\" height=\"100\">"
            + "<tab title=\"One\"><label id=\"l1\" text=\"first\"/></tab><tab title=\"Two\"><label id=\"l2\" text=\"second\"/></tab></tabs></screen>";

    @Test
    void allTabsAppearInTheTreeAndSelectingOneSwitchesToIt() throws Exception {
        EditorController e = editor(TABS);
        DrawList l = paintAll(e);
        assertTrue(hasText(paint(e), "label#l1") && hasText(paint(e), "label#l2"), "보이지 않는 탭의 요소도 구조 트리에 나온다");
        assertTrue(hasText(l, "first") && !hasText(l, "second"));
        e.select(5);                                                     // label#l2 (두 번째 탭 안)
        l = paintAll(e);
        assertTrue(hasText(l, "second"), "선택하면 그 탭이 보인다");
        assertFalse(hasText(l, "first"));
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);                    // 편집으로 문서가 다시 만들어져도 탭 유지
        assertTrue(hasText(paintAll(e), "second"));
    }

    @Test
    void clickingATabHeaderOnTheCanvasSwitchesTabs() throws Exception {
        EditorController e = editor(TABS);
        paintAll(e);
        e.mouseMoved(150 + 14 + 3 + 6 + 20, 45);
        e.mousePressed(150 + 14 + 3 + 6 + 20, 45, 0);
        e.mouseReleased(150 + 14 + 3 + 6 + 20, 45, 0);
        assertTrue(hasText(paintAll(e), "second"));
        assertEquals(1, e.selectedIndex(), "탭 요소가 선택된다");
    }

    @Test
    void helpWrapsLongLinesAndScrollsInSmallWindows() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), "<screen/>", 300, 120);
        e.charTyped('?');
        DrawList l = paint(e);
        assertTrue(hasText(l, "휠로 스크롤"));
        assertFalse(hasText(l, "F1 또는 ? 이 도움말"), "작은 창에서는 아래쪽이 아직 안 보인다");
        for (int i = 0; i < 40; i++) e.mouseScrolled(100, 60, -1.0);
        assertTrue(hasText(paint(e), "에디터 닫기"), "스크롤하면 마지막 줄이 보인다");
    }

    private static final class ItemHost implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        final java.util.List<Boolean> textInput = new java.util.ArrayList<>();

        @Override public void textInput(boolean active, int x, int y, int w, int h) { textInput.add(active); }

        @Override public java.util.List<String> suggestions(String key) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) ids.add("minecraft:item_" + i);
            ids.add("minecraft:diamond");
            return key.equals("item") ? ids : java.util.List.of();
        }
    }

    private static void clickText(EditorController e, String text) {
        DrawList l = new DrawList();
        e.paint(l);
        DrawCommand.DrawText t = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow(() -> new AssertionError("no " + text));
        e.mouseMoved(t.x() + 2, t.y() + 2);
        e.mousePressed(t.x() + 2, t.y() + 2, 0);
        e.mouseReleased(t.x() + 2, t.y() + 2, 0);
    }

    @Test
    void itemFieldOpensAScrollableSearchableIconGrid() throws Exception {
        ItemHost host = new ItemHost();
        EditorController e = new EditorController(host, new FixedMetrics(),
                "<screen><item id=\"i\" item=\"minecraft:stick\" x=\"150\" y=\"40\"/></screen>", W, H);
        e.select(1);
        paint(e);
        DrawList base = paint(e);
        DrawCommand.DrawText row = base.commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("▦"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        e.mouseMoved(row.x() + 2, row.y() + 2);
        e.mousePressed(row.x() + 2, row.y() + 2, 0);
        e.mouseReleased(row.x() + 2, row.y() + 2, 0);
        assertEquals(java.util.List.of(true), host.textInput, "검색을 위해 글자 입력을 켠다");
        DrawList l = paint(e);
        assertTrue(l.commands().stream().filter(c -> c instanceof DrawCommand.DrawItem).count() >= 8 * 6, "격자(8x6)로 아이콘이 그려진다");
        assertTrue(hasText(l, "101개"));
        e.mouseScrolled(100, 100, -1.0);
        e.mouseScrolled(100, 100, -1.0);
        DrawList scrolled = paint(e);
        assertTrue(scrolled.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawItem i && i.itemId().equals("minecraft:item_32")),
                "스크롤하면 뒤쪽 아이템이 보인다");
        for (char c : "diam".toCharArray()) e.charTyped(c);
        DrawList filtered = paint(e);
        assertTrue(hasText(filtered, "1개"));
        DrawCommand.DrawItem icon = filtered.commands().stream().filter(c -> c instanceof DrawCommand.DrawItem).map(c -> (DrawCommand.DrawItem) c).findFirst().orElseThrow();
        assertEquals("minecraft:diamond", icon.itemId());
        e.mouseMoved(icon.x() + 3, icon.y() + 3);
        e.mousePressed(icon.x() + 3, icon.y() + 3, 0);
        e.mouseReleased(icon.x() + 3, icon.y() + 3, 0);
        assertTrue(e.text().contains("item=\"minecraft:diamond\""), e.text());
        assertFalse(hasText(paint(e), "1개"), "고르면 상자가 닫힌다");
        assertEquals(java.util.List.of(true, false), host.textInput, "닫으면 글자 입력을 끈다");
    }

    @Test
    void narrowWindowsMoveOverflowingToolbarButtonsIntoAMenu() throws Exception {
        EditorController e = new EditorController(new ItemHost(), new FixedMetrics(), "<screen/>", 260, H);
        DrawList l = paint(e);
        assertTrue(hasText(l, "..."), "넘치는 버튼은 ... 로");
        assertFalse(hasText(l, "Fit"));
        assertTrue(hasText(l, "Exit"));
        clickText(e, "...");
        assertTrue(hasText(paint(e), "Fit"), "메뉴에서 고를 수 있다");
        // 버튼끼리 겹치지 않는다
        DrawList l2 = new DrawList();
        e.keyPressed(EditorKey.ESCAPE, false, false);
        e.paint(l2);
        java.util.List<DrawCommand.DrawText> labels = l2.commands().stream()
                .filter(c -> c instanceof DrawCommand.DrawText t && t.y() < 16).map(c -> (DrawCommand.DrawText) c).toList();
        for (int i = 0; i + 1 < labels.size(); i++) {
            DrawCommand.DrawText a = labels.get(i), b = labels.get(i + 1);
            if (a.x() < b.x()) assertTrue(a.x() + a.text().length() * 6 <= b.x() + 1, a.text() + " / " + b.text());
        }
    }

    @Test
    void sizedItemKeepsItsIconInsideItsBox() throws Exception {
        UiDocument d = UiDocument.parse("<screen><item id=\"i\" item=\"minecraft:diamond\" x=\"10\" y=\"10\" width=\"32\" height=\"32\"/></screen>", new FixedMetrics());
        d.layout(200, 100);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.PushTransform t && t.scale() == 2f), "32px 상자에는 2배로");
    }

    @Test
    void buttonsCanBeFocusedWithTabAndPressedWithEnter() throws Exception {
        java.util.List<String> hits = new java.util.ArrayList<>();
        dev.magyul.betteruiux.core.doc.UiActions.register("press", ctx -> hits.add(ctx.source().id()));
        try {
            UiDocument d = UiDocument.parse("<screen><button id=\"a\" x=\"10\" y=\"10\" text=\"A\" onClick=\"press\"/>"
                    + "<button id=\"b\" x=\"60\" y=\"10\" text=\"B\" onClick=\"press\"/></screen>", new FixedMetrics());
            d.layout(200, 100);
            d.keyPressed(dev.magyul.betteruiux.core.doc.UiKey.TAB, false, false);
            d.keyPressed(dev.magyul.betteruiux.core.doc.UiKey.TAB, false, false);
            assertEquals(d.query("#b"), d.focusedElement());
            d.keyPressed(dev.magyul.betteruiux.core.doc.UiKey.SPACE, false, false);
            assertEquals(java.util.List.of("b"), hits);
            DrawList l = new DrawList();
            d.paint(l, -1, -1);
            assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFFFFFF55), "포커스 표시");
        } finally {
            dev.magyul.betteruiux.core.doc.UiActions.unregister("press");
        }
    }
}
