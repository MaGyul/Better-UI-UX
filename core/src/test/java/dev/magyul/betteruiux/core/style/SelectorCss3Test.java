package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectorCss3Test {
    UiNode root, a, b, c, d, e;

    /** column > [label#a.x, button#b, label#c, row#d > label#e], 사이에 <style> 메타 요소. */
    @BeforeEach
    void tree() {
        root = new UiNode("column");
        a = node("label", "a", "x y");
        root.append(new UiNode("style"));
        b = node("button", "b", null);
        c = node("label", "c", null);
        d = node("row", "d", null);
        e = node("label", "e", null);
        root.append(a);
        root.append(b);
        root.append(c);
        root.append(d);
        d.append(e);
    }

    private static UiNode node(String tag, String id, String cls) {
        UiNode n = new UiNode(tag);
        n.attributes().put("id", id);
        if (cls != null) n.attributes().put("class", cls);
        return n;
    }

    private static boolean m(String sel, UiNode n) { return Selector.parse(sel).matches(n); }

    @Test
    void siblingCombinators() {
        assertTrue(m("label + button", b));
        assertFalse(m("button + label#a", a));
        assertTrue(m("label + button + label", c));       // 메타 요소(style)는 형제로 세지 않는다
        assertTrue(m("label ~ row", d));
        assertTrue(m("#a ~ #c", c));
        assertFalse(m("#c ~ #a", a));
        assertTrue(m("column > label + button", b));
    }

    @Test
    void notIsWhere() {
        assertTrue(m("label:not(.x)", c));
        assertFalse(m("label:not(.x)", a));
        assertTrue(m(":not(label, row)", b));
        assertTrue(m(":is(button, row)", d));
        assertFalse(m(":is(button, row)", c));
        assertTrue(m(":where(#a, #c)", c));
    }

    @Test
    void nthFamily() {
        assertTrue(m(":nth-child(1)", a));                // style 은 제외
        assertTrue(m(":nth-child(odd)", c));              // a=1,b=2,c=3,d=4
        assertTrue(m(":nth-child(2n)", d));
        assertTrue(m(":nth-child(-n+2)", b));
        assertFalse(m(":nth-child(-n+2)", c));
        assertTrue(m(":nth-child(n+3)", c));
        assertTrue(m(":nth-last-child(1)", d));
        assertTrue(m("label:nth-of-type(2)", c));
        assertTrue(m("label:first-of-type", a));
        assertTrue(m("label:last-of-type", c));
        assertTrue(m("button:only-of-type", b));
        assertTrue(m(":only-child", e));
        assertFalse(m(":only-child", b));
    }

    @Test
    void emptyAndChecked() {
        assertTrue(m("label:empty", a));
        assertFalse(m("row:empty", d));
        UiNode box = new UiNode("checkbox");
        root.append(box);
        assertFalse(m("checkbox:checked", box));
        box.attributes().put("checked", "true");
        assertTrue(m("checkbox:checked", box));
    }

    @Test
    void attributeExtras() {
        assertTrue(m("[class~=y]", a));
        assertTrue(m("[id|=a]", a));
        assertTrue(m("[id=A i]", a));
        assertFalse(m("[id=A]", a));
        assertFalse(m("[class^='']", a));                 // 빈 값은 항상 불일치(CSS 규정)
    }

    @Test
    void tabsAndNewlinesSeparateClasses() {
        UiNode n = node("label", "z", "p\tq\nr");
        root.append(n);
        assertTrue(m(".p.q.r", n));
        assertTrue(m("[class~=q]", n));
    }

    @Test
    void listsWithParensAreNotSplitByCommas() {
        assertEquals(List.of(":not(.a, .b)", "c"), Selector.splitList(":not(.a, .b), c"));
        Stylesheet s = Stylesheet.parse(":not(.a, .b), c { color: red }");
        assertEquals(2, s.rules().size());
        assertTrue(s.warnings().isEmpty());
    }

    @Test
    void invalidNewSyntax() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a + "));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a > > b"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse(":nth-child(foo)"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse(":not(:hover)"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a::before"));
    }
}
