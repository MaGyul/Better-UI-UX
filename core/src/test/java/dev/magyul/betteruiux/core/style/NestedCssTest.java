package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CSS 중첩: {@code a { &:hover { … } }} 같은 구조. */
class NestedCssTest {
    private static List<String> selectors(String css) {
        return Stylesheet.parse(css).rules().stream().map(r -> r.selector().toString()).toList();
    }

    @Test
    void ampersandPseudoBecomesACompound() {
        Stylesheet s = Stylesheet.parse("a { color: red; &:hover { color: blue; } }");
        assertEquals(List.of("a", "a:hover"), s.rules().stream().map(r -> r.selector().toString()).toList());
        assertEquals("blue", s.rules().get(1).declarations().get(0).value());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    void emptyNestedBlockIsFineAndAddsNothingExtra() {
        Stylesheet s = Stylesheet.parse("a { &:hover { } }");
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
        assertEquals(List.of("a:hover"), s.rules().stream().map(r -> r.selector().toString()).toList());
    }

    @Test
    void descendantAndRelativeCombinators() {
        assertEquals(List.of(".card", ".card .title", ".card > .x", ".card + .card"),
                selectors(".card { padding: 4; .title { color: red } > .x { color: blue } & + & { margin: 2 } }"));
    }

    @Test
    void listsOnEitherSide() {
        assertEquals(List.of(":is(a, b):hover", ":is(a, b) .x"), selectors("a, b { &:hover { color: red } .x { color: blue } }"));
        assertEquals(List.of("a .x", "a .y"), selectors("a { .x, .y { color: red } }"));
    }

    @Test
    void complexParentIsSubstitutedWhenAmpersandLeadsAndWrappedOtherwise() {
        assertEquals(List.of(".a .b:hover"), selectors(".a .b { &:hover { color: red } }"));
        assertEquals(List.of(":is(.a .b) + :is(.a .b)"), selectors(".a .b { & + & { color: red } }"));
        assertEquals(List.of(".x :is(.a .b)"), selectors(".a .b { .x & { color: red } }"));
    }

    @Test
    void deeplyNested() {
        assertEquals(List.of("a", "a b", "a b:hover"), selectors("a { color: red; b { color: blue; &:hover { color: green } } }"));
    }

    @Test
    void nestedMediaAppliesToTheParentSelector() {
        Stylesheet s = Stylesheet.parse(".card { padding: 4; @media (max-width: 100) { padding: 1 } }");
        assertEquals(2, s.rules().size());
        assertEquals(List.of(".card", ".card"), s.rules().stream().map(r -> r.selector().toString()).toList());
        assertEquals(1, s.rules().get(1).media().size(), "두 번째 규칙에만 @media 조건");
        assertTrue(s.rules().get(0).media().isEmpty());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    void semicolonsInsideUrlAndStringsDoNotSplit() {
        Stylesheet s = Stylesheet.parse("a { content: \"x;{y}\"; &:hover { color: red } }");
        assertEquals(List.of("a", "a:hover"), s.rules().stream().map(r -> r.selector().toString()).toList());
        assertEquals("\"x;{y}\"", s.rules().get(0).declarations().get(0).value());
    }

    @Test
    void unsupportedNestedAtRuleWarns() {
        Stylesheet s = Stylesheet.parse("a { color: red; @font-face { x: y } }");
        assertEquals(1, s.rules().size());
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("@font-face")), s.warnings().toString());
    }

    @Test
    void nestedRulesAreAppliedToTheDocument() throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>.card { background: #FF0000; .title { background: #00FF00 } &:hover { background: #0000FF } }</style>"
                + "<panel class='card' id='c' width='40' height='20'><panel class='title' id='t' width='10' height='10'/></panel></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList idle = new DrawList();
        d.paint(idle, -1, -1);
        assertTrue(idle.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFFFF0000 && r.w() == 40));
        assertTrue(idle.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFF00FF00 && r.w() == 10), "자식 규칙");
        DrawList over = new DrawList();
        d.paint(over, 35, 15);                                          // 부모 위(자식 밖)
        assertTrue(over.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFF0000FF && r.w() == 40), "&:hover");
    }

    @Test
    void combineHelper() {
        assertEquals("a:hover", NestedCss.combine("a", "&:hover"));
        assertEquals("a b", NestedCss.combine("a", "b"));
        assertEquals("a > b", NestedCss.combine("a", "> b"));
        assertEquals("a.x", NestedCss.combine("a", "&.x"));
    }
}
