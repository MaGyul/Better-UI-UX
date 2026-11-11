package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PseudoElementTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(300, 200);
        return d;
    }

    private static List<String> texts(UiDocument d) {
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText)
                .map(c -> ((DrawCommand.DrawText) c).text()).toList();
    }

    @Test
    void parsesAndDoesNotApplyToTheElementItself() {
        Selector s = Selector.parse("label.x::before");
        assertEquals("before", s.pseudoElement());
        assertEquals("after", Selector.parse("a:after").pseudoElement());          // 옛 한 줄 콜론
        assertNull(Selector.parse("a:hover").pseudoElement());
        assertTrue(s.specificity() > Selector.parse("label.x").specificity());
    }

    @Test
    void labelTextGetsPrefixAndSuffix() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                label.item::before { content: "• " }
                label.item::after { content: " (" attr(data-n) ")" }
              </style>
              <label class='item' id='l' data-n='3' text='apple'/>
            </screen>""");
        assertEquals(List.of("• apple (3)"), texts(d));
        assertEquals((("• apple (3)").length()) * 6, d.findById("l").w);          // 폭에도 반영
        assertEquals("apple", dev.magyul.betteruiux.core.layout.LayoutEngine.rawText(d.findById("l")));   // 편집용 원문은 그대로
    }

    @Test
    void containerGetsVirtualFirstAndLastChildren() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .box::before { content: "A"; color: #ff0000; margin: 0 2 0 0 }
                .box::after { content: "Z"; color: #00ff00 }
              </style>
              <row class='box' id='r' x='0' y='0' width='100' height='20' gap='1'>
                <label id='m' text='mid'/>
              </row>
            </screen>""");
        LayoutBox r = d.findById("r");
        assertEquals(3, r.children.size());
        assertTrue(r.children.get(0).synthetic && r.children.get(0).pseudo.equals("before"));
        assertTrue(r.children.get(2).synthetic && r.children.get(2).pseudo.equals("after"));
        assertEquals(6 + 2 + 1, d.findById("m").x - r.x);                          // 가상 상자 6 + 오른쪽 여백 2 + gap 1
        assertEquals("#ff0000", r.children.get(0).style.get("color"));
        assertEquals(List.of("A", "mid", "Z"), texts(d));
        assertEquals(0, r.node.children().size() - 1);                            // 문서(DOM)에는 자식이 늘지 않는다
    }

    @Test
    void noContentMeansNoBox() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>.a::before { color: red } .b::before { content: none } .c::before { content: normal }</style>
              <row id='a' class='a'><label text='x'/></row>
              <row id='b' class='b'><label text='x'/></row>
              <row id='c' class='c'><label text='x'/></row>
            </screen>""");
        for (String id : new String[] {"a", "b", "c"}) assertEquals(1, d.findById(id).children.size(), id);
    }

    @Test
    void syntheticBoxesAreNotClickTargetsAndRulesDoNotLeakToTheOwner() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .box::before { content: "ABCDEFGH"; background: #ff0000ff; width: 60; height: 20 }
                .box { background: #0000ffff }
              </style>
              <panel class='box' id='p' x='0' y='0' width='100' height='20'/>
            </screen>""");
        LayoutBox p = d.findById("p");
        assertEquals("#0000ffff", p.style.get("background"));                      // ::before 의 background 가 주인에게 새지 않는다
        assertSame(p, d.hitTest(5, 5));
        assertTrue(p.children.get(0).synthetic);
    }

    @Test
    void escapesAndAttrInContent() throws Exception {
        UiNode owner = new UiNode("x");
        owner.attributes().put("title", "T");
        assertEquals("\u201CT\u201D", ContentValue.eval("\"\\201C\" attr(title) \"\\201D\"", owner));
        assertEquals("a\nb", ContentValue.eval("\"a\\A b\"", owner));
        assertEquals("", ContentValue.eval("attr(missing)", owner));
        assertNull(ContentValue.eval("none", owner));
        assertNull(ContentValue.eval("counter(x)", owner));
        assertEquals("\u201Cx\u201D", ContentValue.eval("open-quote 'x' close-quote", owner));
    }
}
