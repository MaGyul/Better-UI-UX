package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.Size;
import org.junit.jupiter.api.Test;

class PercentCalcAndWarningsTest {
    @Test
    void calcWithPercentParsesToPercentPlusOffset() {
        Size a = Size.parse("calc(100% - 20px)", Size.AUTO);
        assertEquals(Size.Kind.PERCENT, a.kind());
        assertEquals(280f, a.percentOf(300), 1e-3);
        assertEquals(0.5f * 300 + 16, Size.parse("calc(50% + 2em)", Size.AUTO).percentOf(300), 1e-3);
        assertEquals(75f, Size.parse("calc(100% / 4)", Size.AUTO).percentOf(300), 1e-3);
        assertEquals(2 * (30f + 5), Size.parse("calc(2 * (10% + 5px))", Size.AUTO).percentOf(300), 1e-3);
        assertEquals(30f, Size.parse("calc(10px * 3)", Size.AUTO).value(), 1e-3);
        assertEquals(Size.Kind.PX, Size.parse("calc(10px * 3)", Size.AUTO).kind());
        assertEquals(Size.FILL, Size.parse("calc(1% * 2%)", Size.FILL));          // 퍼센트끼리 곱은 해석 불가
        assertEquals(Size.FILL, Size.parse("calc(100% / 0)", Size.FILL));
    }

    @Test
    void calcWithPercentLaysOut() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <panel id='a' width='calc(100% - 20px)' height='5'/>
              <panel id='b' y='10' width='10' min-width='calc(50% + 10px)' height='5'/>
              <row y='30' width='200' height='10'><panel id='c' width='calc(50% - 4px)' height='5'/></row>
            </screen>""", new FixedMetrics());
        d.layout(400, 200);
        assertEquals(380, d.findById("a").w);
        assertEquals(210, d.findById("b").w);
        assertEquals(96, d.findById("c").w);
    }

    @Test
    void unknownPropertiesWarnWithASuggestion() {
        Stylesheet s = Stylesheet.parse("a { colour: red; width: 1 } b { colour: blue; backgrond: red; zzzzzz: 1; --x: 1; -webkit-foo: 2 }");
        assertEquals(3, s.warnings().size(), s.warnings().toString());           // colour 는 한 번만
        assertTrue(s.warnings().get(0).contains("'colour'") && s.warnings().get(0).contains("'color'"));
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("backgrond") && w.contains("background")));
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("zzzzzz") && !w.contains("혹시")));
    }

    @Test
    void knownPropertiesDoNotWarn() {
        Stylesheet s = Stylesheet.parse("""
            .a { width: 1; min-width: 2; border-radius: 3; box-shadow: 0 0 1 red; font-size: 9; white-space: normal;
                 grid-template-columns: 1fr; flex: 1; justify-content: center; bar-color: red; transition: width 1s }
            """);
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }
}
