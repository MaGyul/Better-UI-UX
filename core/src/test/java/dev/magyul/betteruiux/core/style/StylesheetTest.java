package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StylesheetTest {
    @Test
    void parsesRulesCommentsAndSelectorLists() {
        Stylesheet s = Stylesheet.parse("""
            /* 주석 */
            .a, #b > c { color: red; width : 10px }
            label { margin: 4 }
            """);
        assertEquals(3, s.rules().size());               // .a, "#b > c", label
        assertEquals(2, s.rules().get(0).declarations().size());
        assertEquals("10px", s.rules().get(0).declarations().get(1).value());
        assertTrue(s.warnings().isEmpty());
    }

    @Test
    void importantAndVariablesAreKept() {
        var d = Stylesheet.parseDeclarations("--accent: #ff0; color: var(--accent, red) !important");
        assertEquals("--accent", d.get(0).prop());
        assertEquals("var(--accent, red)", d.get(1).value());
        assertTrue(d.get(1).important());
    }

    @Test
    void semicolonInsideParensIsNotASeparator() {
        var d = Stylesheet.parseDeclarations("a: f(1;2); b: 3");
        assertEquals(2, d.size());
        assertEquals("f(1;2)", d.get(0).value());
    }

    @Test
    void unsupportedAtRuleAndBadSelectorsOnlyWarn() {
        Stylesheet s = Stylesheet.parse("""
            @font-face { font-family: x }
            label:nonsense { color: red }
            button { color: blue }
            """);
        assertEquals(1, s.rules().size());
        assertEquals(2, s.warnings().size());
    }

    @Test
    void unclosedRuleWarns() {
        Stylesheet s = Stylesheet.parse("button { color: red");
        assertTrue(s.rules().isEmpty());
        assertFalse(s.warnings().isEmpty());
    }

    @Test
    void selectorSpecificityAndDynamicPseudos() {
        assertEquals(1, Selector.parse("button").specificity());
        assertEquals(11, Selector.parse("button.primary").specificity());
        assertEquals(101, Selector.parse("#ok button").specificity());
        assertEquals(Set.of("hover"), Selector.parse("button:hover").dynamicPseudos());
        assertEquals(List.of("active", "hover"), List.copyOf(Selector.parse("button:hover:active").dynamicPseudos()));
        assertTrue(Selector.parse("button:first-child").dynamicPseudos().isEmpty());
    }

    @Test
    void invalidSelectorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("> a"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a >"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a:bogus"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a:hover b"));
    }
}
