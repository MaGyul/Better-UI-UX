package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import org.junit.jupiter.api.Test;

class StyleCascadeTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(320, 240);
        return d;
    }

    private static String prop(UiDocument d, String id, String key) {
        return d.findById(id).style.get(key);
    }

    @Test
    void attributeThenSheetThenInlineOverride() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>panel { width: 50 } .wide { width: 80 }</style>
              <panel id='a' width='10'/>
              <panel id='b' class='wide' width='10'/>
              <panel id='c' class='wide' style='width: 99'/>
            </screen>""");
        assertEquals("50", prop(d, "a", "width"));   // 속성 < 시트
        assertEquals("80", prop(d, "b", "width"));   // 클래스가 타입보다 특이도 높음
        assertEquals("99", prop(d, "c", "width"));   // 인라인 최우선
        assertEquals(80, d.findById("b").w);          // 레이아웃에도 반영
    }

    @Test
    void laterRuleWinsOnEqualSpecificityAndImportantBeatsAll() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .x { color: red }
                .x { color: green }
                #i { color: blue !important }
                #i.x { color: yellow }
              </style>
              <label id='p' class='x' text='a'/>
              <label id='i' class='x' text='a'/>
            </screen>""");
        assertEquals("green", prop(d, "p", "color"));
        assertEquals("blue", prop(d, "i", "color"));
    }

    @Test
    void combinatorsAndStructuralPseudos() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                column > label { margin: 1 }
                panel label { padding: 2 }
                label:first-child { color: red }
                label:last-child { color: blue }
                [kind=warn] { background: #f00 }
              </style>
              <panel id='pn'><column id='col'>
                <label id='l1' text='a'/>
                <label id='l2' text='b' kind='warn'/>
              </column></panel>
            </screen>""");
        assertEquals("1", prop(d, "l1", "margin"));
        assertEquals("2", prop(d, "l1", "padding"));   // 자손 결합자
        assertEquals("red", prop(d, "l1", "color"));
        assertEquals("blue", prop(d, "l2", "color"));
        assertEquals("#f00", prop(d, "l2", "background"));
        assertNull(prop(d, "l1", "background"));
        assertNull(prop(d, "col", "margin"));          // column 자신은 label 이 아님
    }

    @Test
    void inheritanceAndVariables() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                :root { --accent: #FF0; --gap: 7 }
                panel { color: var(--accent) }
                column { gap: var(--gap) }
                label.b { color: var(--missing, white) }
              </style>
              <panel id='p'><column id='c'>
                <label id='a' text='x'/>
                <label id='b' class='b' text='y'/>
              </column></panel>
            </screen>""");
        assertEquals("#FF0", prop(d, "a", "color"));     // 부모 panel 의 color 상속
        assertEquals("7", prop(d, "c", "gap"));
        assertEquals("white", prop(d, "b", "color"));    // 변수 없음 → fallback
        assertEquals("#FF0", prop(d, "a", "--accent"));  // 변수 자체도 상속
    }

    @Test
    void variablesCanReferenceOtherVariables() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                :root { --base: #123456; --alias: var(--base) }
                label { color: var(--alias) }
              </style>
              <label id='a' text='x'/>
            </screen>""");
        assertEquals("#123456", prop(d, "a", "color"));
    }

    @Test
    void backgroundColorAlias() throws Exception {
        UiDocument d = doc("<screen><style>panel { background-color: #abc }</style><panel id='p'/></screen>");
        assertEquals("#abc", prop(d, "p", "background"));
    }

    @Test
    void hoverOverlayOnlyAffectsEffectiveStyle() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>button:hover { background: #fff; width: 999 } button:active { background: #000 }</style>
              <button id='b' text='x' width='40'/>
            </screen>""");
        LayoutBox b = d.findById("b");
        assertEquals(40, b.w);                               // 레이아웃은 상태 무관
        assertNull(b.style.get("background"));
        assertEquals("#fff", b.effective(true, false).get("background"));
        assertEquals("#000", b.effective(true, true).get("background"));
        assertNull(b.effective(false, false).get("background"));
    }
}
