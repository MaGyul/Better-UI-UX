package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class HasSelectorTest {
    private static UiDocument doc(String body) throws Exception {
        UiDocument d = UiDocument.parse("<screen>" + body + "</screen>", new FixedMetrics());
        d.layout(300, 200);
        return d;
    }

    @Test
    void descendantChildAndSiblingForms() throws Exception {
        UiDocument d = doc("<column id='p'><row><label id='deep' class='x'/></row></column>"
                + "<column id='q'><label id='c'/></column><panel id='a'/><label id='b' class='y'/><panel id='z'/>");
        assertTrue(d.queryAll(":has(.x)").stream().anyMatch(e -> "p".equals(e.id())), "후손");
        assertFalse(d.queryAll("column:has(> .x)").stream().anyMatch(e -> "p".equals(e.id())), "자식만: p 의 자식은 row");
        assertTrue(d.queryAll("column:has(> label)").stream().anyMatch(e -> "q".equals(e.id())));
        assertTrue(d.queryAll("panel:has(+ .y)").stream().anyMatch(e -> "a".equals(e.id())), "바로 다음 형제");
        assertTrue(d.queryAll("panel:has(~ .y)").stream().anyMatch(e -> "a".equals(e.id())), "뒤따르는 형제");
        assertFalse(d.queryAll("panel:has(~ .y)").stream().anyMatch(e -> "z".equals(e.id())));
    }

    @Test
    void worksInNotAndLists() throws Exception {
        UiDocument d = doc("<column id='p'><label class='x'/></column><column id='q'/>");
        assertEquals(1, d.queryAll("column:not(:has(.x))").size());
        assertEquals("q", d.queryAll("column:not(:has(.x))").get(0).id());
        assertEquals(1, d.queryAll("column:has(.nope, .x)").size());
    }

    @Test
    void invalidArgumentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a:has()"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a:has(>)"));
    }

    @Test
    void styleRuleWithHasApplies() throws Exception {
        UiDocument d = doc("<style>column:has(> .x) { background: #FF0000 } column { width: 20; height: 20 }</style>"
                + "<column id='p'><label class='x'/></column><column id='q'><label/></column>");
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        long red = l.commands().stream().filter(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFFFF0000).count();
        assertEquals(1, red, "p 만 빨강");
    }

    @Test
    void specificityTakesTheMostSpecificArgument() {
        assertEquals(Selector.parse("a:has(#x)").specificity(), Selector.parse("a:is(#x)").specificity());
    }
}
