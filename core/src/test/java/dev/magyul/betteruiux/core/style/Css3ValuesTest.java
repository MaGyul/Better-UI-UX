package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class Css3ValuesTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(400, 200);
        return d;
    }

    private static String prop(UiDocument d, String id, String key) { return d.findById(id).style.get(key); }

    // ---- 색 ----

    @Test
    void hexFormatsUseCssAlphaLast() {
        assertEquals(0xFF112233, Colors.parse("#112233", 0));
        assertEquals(0x80112233, Colors.parse("#11223380", 0));
        assertEquals(0xFF112233, Colors.parse("#123", 0));
        assertEquals(0x88112233, Colors.parse("#1238", 0));
        assertEquals(7, Colors.parse("#12", 7));
        assertEquals(7, Colors.parse("#GGGGGG", 7));
    }

    @Test
    void colorFunctionsAndNames() {
        assertEquals(0xFFFF0000, Colors.parse("rgb(255, 0, 0)", 0));
        assertEquals(0xFFFF0000, Colors.parse("rgb(255 0 0)", 0));
        assertEquals(0x80FF0000, Colors.parse("rgba(255, 0, 0, 0.5)", 0));
        assertEquals(0x80FF0000, Colors.parse("rgb(100% 0% 0% / 50%)", 0));
        assertEquals(0xFF00FF00, Colors.parse("hsl(120, 100%, 50%)", 0));
        assertEquals(0xFF0000FF, Colors.parse("hsl(240deg 100% 50%)", 0));
        assertEquals(0xFF808080, Colors.parse("hsl(0, 0%, 50%)", 0));
        assertEquals(0xFFFF0000, Colors.parse("hwb(0 0% 0%)", 0));
        assertEquals(0xFF663399, Colors.parse("RebeccaPurple", 0));
        assertEquals(0xFFFF0000, Colors.parse("red", 0));            // CSS 값 (옛 MC 팔레트가 아님)
        assertEquals(0xFFFF5555, Colors.parse("mc-red", 0));
        assertEquals(0, Colors.parse("transparent", 9));
        assertEquals(9, Colors.parse("rgb(1,2)", 9));
        assertEquals(9, Colors.parse("not-a-color", 9));
    }

    @Test
    void cssStringRoundTrip() {
        assertEquals("#112233", Colors.toCss(0xFF112233));
        assertEquals("#11223380", Colors.toCss(0x80112233));
        assertEquals("#112233FF", Colors.toCssFull(0xFF112233));
        assertEquals(0x80112233, Colors.parse(Colors.toCss(0x80112233), 0));
    }

    // ---- 단위 / calc ----

    @Test
    void unitsAndCalc() {
        CssMath.Env env = new CssMath.Env(400, 200);
        assertEquals("16", CssMath.resolve("2em", env));
        assertEquals("8", CssMath.resolve("1rem", env));
        assertEquals("200", CssMath.resolve("50vw", env));
        assertEquals("100", CssMath.resolve("50vh", env));
        assertEquals("12", CssMath.resolve("9pt", env));
        assertEquals("110", CssMath.resolve("calc(100px + 10px)", env));
        assertEquals("380", CssMath.resolve("calc(100vw - 20px)", env));
        assertEquals("40", CssMath.resolve("calc((10px + 10px) * 2)", env));
        assertEquals("5", CssMath.resolve("calc(10px / 2)", env));
        assertEquals("20", CssMath.resolve("min(20px, 50px)", env));
        assertEquals("50", CssMath.resolve("max(20px, 50px)", env));
        assertEquals("30", CssMath.resolve("clamp(10px, 30px, 50px)", env));
        assertEquals("10 16 10px 16", CssMath.resolve("10 2em 10px 2em", env));
        // 퍼센트가 섞이면 손대지 않는다
        assertEquals("calc(100% - 20px)", CssMath.resolve("calc(100% - 20px)", env));
        assertEquals("50%", CssMath.resolve("50%", env));
        assertEquals("12px", CssMath.resolve("12px", env));
    }

    @Test
    void viewportUnitsStayWhenViewportUnknown() {
        assertEquals("10vw", CssMath.resolve("10vw", new CssMath.Env(0, 0)));
    }

    @Test
    void lengthsInStylesAreResolvedAndLaidOut() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #a { width: calc(50vw - 10px); height: 2em; margin: 1em 2px }
                #b { width: 25vw; padding: calc(2px * 2) }
              </style>
              <panel id='a' x='0' y='0'/>
              <panel id='b' x='0' y='30'/>
            </screen>""");
        assertEquals("190", prop(d, "a", "width"));
        assertEquals("16", prop(d, "a", "height"));
        assertEquals("8 2px", prop(d, "a", "margin"));
        assertEquals(190, d.findById("a").w);
        assertEquals(100, d.findById("b").w);
        assertEquals("4", prop(d, "b", "padding"));
    }

    // ---- 숏핸드 ----

    @Test
    void borderShorthand() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .a { border: 2px solid red }
                .b { border: none }
                .c { border: 1px dashed #00f; border-color: green }
                .d { border: blue }
              </style>
              <panel id='a' class='a'/><panel id='b' class='b'/><panel id='c' class='c'/><panel id='d' class='d'/>
            </screen>""");
        assertEquals("2px", prop(d, "a", "border"));
        assertEquals("red", prop(d, "a", "border-color"));
        assertEquals("0", prop(d, "b", "border"));
        assertEquals("green", prop(d, "c", "border-color"));       // 뒤의 롱핸드가 이긴다
        assertEquals("blue", prop(d, "d", "border-color"));
        assertEquals("1", prop(d, "d", "border"));
    }

    @Test
    void sideLonghandsMergeIntoMarginAndPadding() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .a { margin: 4; margin-left: 10 }
                .b { padding-top: 3; padding-right: 5 }
                .c { margin: 1 2; margin-bottom: 9 }
              </style>
              <panel id='a' class='a'/><panel id='b' class='b'/><panel id='c' class='c'/>
            </screen>""");
        assertEquals("4 4 4 10", prop(d, "a", "margin"));
        assertEquals("3 5 0 0", prop(d, "b", "padding"));
        assertEquals("1 2 9 2", prop(d, "c", "margin"));
    }

    // ---- inherit / initial / unset / currentColor ----

    @Test
    void cssWideKeywords() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #p { width: 77; color: red }
                #c1 { width: inherit; color: initial }
                #c2 { color: unset }
              </style>
              <panel id='p'><panel id='c1'/><panel id='c2'/></panel>
            </screen>""");
        assertEquals("77", prop(d, "c1", "width"));
        assertNull(prop(d, "c1", "color"));                         // initial → 값 없음
        assertEquals("red", prop(d, "c2", "color"));                // unset(상속 속성) → inherit
    }

    @Test
    void currentColor() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .box { color: #0f0; border-color: currentColor; background: currentcolor }
                .inh > .kid { color: currentColor }
              </style>
              <panel id='a' class='box'/>
              <panel class='inh' style='color: blue'><panel id='k' class='kid'/></panel>
            </screen>""");
        assertEquals("#0f0", prop(d, "a", "border-color"));
        assertEquals("#0f0", prop(d, "a", "background"));
        assertEquals("blue", prop(d, "k", "color"));
    }

    // ---- 우선순위 ----

    @Test
    void sheetImportantBeatsInlineButInlineImportantBeatsSheet() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>#a { width: 11 !important } #b { width: 12 !important } #c { width: 13 }</style>
              <panel id='a' style='width: 99'/>
              <panel id='b' style='width: 98 !important'/>
              <panel id='c' style='width: 97'/>
            </screen>""");
        assertEquals("11", prop(d, "a", "width"));
        assertEquals("98", prop(d, "b", "width"));
        assertEquals("97", prop(d, "c", "width"));
    }
}
