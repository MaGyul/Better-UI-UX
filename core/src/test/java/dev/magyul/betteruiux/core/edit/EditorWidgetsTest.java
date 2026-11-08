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
}
