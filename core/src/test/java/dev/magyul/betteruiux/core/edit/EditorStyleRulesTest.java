package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 속성 패널의 "스타일 규칙" 구역: 적용된 규칙을 보여 주고 값을 고친다. */
class EditorStyleRulesTest {
    private static final int W = 700, H = 700;
    private static final String XML = "<screen><style>.card { padding: 8; }</style><panel id=\"a\" class=\"card\" width=\"80\" height=\"40\"/></screen>";

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.uiml"; }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).reduce((x, y) -> y).orElse(null);   // 같은 글자가 여럿이면 마지막(스타일 구역은 맨 아래)
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static EditorController selected() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), XML, W, H);
        int idx = e.document().layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow().node.sourceIndex();
        e.select(idx);
        paint(e);
        return e;
    }

    @Test
    void showsMatchingRuleAndEditsItsValue() throws Exception {
        EditorController e = selected();
        DrawList l = paint(e);
        assertTrue(find(l, "- 스타일 규칙") != null, "구역 제목");
        assertTrue(find(l, "- .card") != null, "선택자");
        DrawCommand.DrawText prop = find(l, "padding");
        assertTrue(prop != null, "선언 이름");
        click(e, W - EditorController.RIGHT_W + 60 + 5, prop.y() + 2);          // 값 칸
        assertTrue(e.isEditingField());
        for (int i = 0; i < 4; i++) e.keyPressed(EditorKey.BACKSPACE, false, false);
        e.charTyped('1');
        e.charTyped('6');
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains(".card { padding: 16; }"), e.text());
        assertTrue(e.isDirty());
    }

    @Test
    void addRowAddsADeclaration() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        assertTrue(add != null);
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "color: #FFFFFF".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("padding: 8; color: #FFFFFF;"), e.text());
    }

    @Test
    void elementWithoutRulesStillOffersANewRule() throws Exception {
        String plain = "<screen><panel id=\"a\" width=\"80\" height=\"40\"/></screen>";
        EditorController e = new EditorController(new Host(), new FixedMetrics(), plain, W, H);
        int idx = e.document().layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow().node.sourceIndex();
        e.select(idx);
        paint(e);
        assertTrue(find(paint(e), "- 스타일 규칙") != null, "규칙이 없어도 구역은 보인다");
        DrawCommand.DrawText add = find(paint(e), "+ 새 규칙");
        assertTrue(add != null);
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "padding: 6".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("#a { padding: 6; }"), e.text());
        assertTrue(find(paint(e), "- #a") != null, "만든 규칙이 바로 목록에 나온다");
    }

    @Test
    void invalidAddShowsAnErrorAndChangesNothing() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "엉터리".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertEquals(XML, e.text());
        assertTrue(e.status().contains("속성"));
    }

    @Test
    void addingATypoedPropertyStillAppliesButWarns() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "colour: #FFFFFF".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("colour: #FFFFFF"), "적용은 된다");
        assertTrue(e.status().contains("주의") && e.status().contains("혹시 'color'"), e.status());
    }

    @Test
    void addingAnInvalidValueWarns() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "opacity: red".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("opacity: red"));
        assertTrue(e.status().contains("주의") && e.status().contains("opacity"), e.status());
    }

    @Test
    void validEditHasNoWarning() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "opacity: 0.5".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(!e.status().contains("주의"), e.status());
    }

    @Test
    void lintHelperReadsThePropertyFromTheKey() {
        assertEquals(null, StyleText.lint("css:0:padding", "8"));
        assertTrue(StyleText.lint("css:0:padding", "8") == null && StyleText.lint("css:0:width", "abc").contains("width"));
        assertTrue(StyleText.lint("xcss:a.uicss:0:opacity", "red").contains("opacity"));
        assertEquals(null, StyleText.lint("css+:0", ""));
    }

    @Test
    void propertyNamesAreCompletedWithTab() throws Exception {
        EditorController e = selected();
        DrawCommand.DrawText add = find(paint(e), "+ 추가");
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "opac".toCharArray()) e.charTyped(c);
        paint(e);                                                      // 후보 목록이 그려져야 Tab 이 받는다
        e.keyPressed(EditorKey.TAB, false, false);
        for (char c : "0.5".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("opacity: 0.5"), e.text());
    }

    @Test
    void ruleSuggestionsForNamesAndValues() {
        assertEquals(java.util.List.of(), EditorController.ruleSuggestions(""));
        assertTrue(EditorController.ruleSuggestions("box-s").contains("box-shadow: "));
        assertTrue(EditorController.ruleSuggestions("box-s").contains("box-sizing: "));
        assertEquals("position: ", EditorController.ruleSuggestions("posi").get(0), "접두사로 시작하는 것이 먼저");
        assertTrue(EditorController.ruleSuggestions("position: ").contains("position: absolute"));
        assertEquals(java.util.List.of("position: relative"), EditorController.ruleSuggestions("position: rel"));
        assertEquals(java.util.List.of(), EditorController.ruleSuggestions("position: absolute"), "이미 완성이면 닫는다");
        assertTrue(EditorController.ruleSuggestions("color: red; displ").contains("color: red; display: "), "앞선 선언은 그대로 둔다");
        assertEquals(java.util.List.of(), EditorController.ruleSuggestions("made-up: x"));
    }

    @Test
    void valueSuggestionsComeFromTheTypeTable() {
        assertTrue(dev.magyul.betteruiux.core.style.Stylesheet.valueSuggestions("display").contains("flex"));
        assertTrue(dev.magyul.betteruiux.core.style.Stylesheet.valueSuggestions("width").contains("auto"));
        assertTrue(dev.magyul.betteruiux.core.style.Stylesheet.valueSuggestions("color").contains("transparent"));
        assertEquals(java.util.List.of(), dev.magyul.betteruiux.core.style.Stylesheet.valueSuggestions("made-up"));
    }
}
