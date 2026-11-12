package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.core.doc.UiDocument;
import org.junit.jupiter.api.Test;

class LayoutCss3Test {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(400, 200);
        return d;
    }

    @Test
    void minAndMaxInStack() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='a' width='500' height='10' max-width='120'/>
              <panel id='b' width='20' height='10' min-width='60' y='20'/>
              <panel id='c' width='50%' height='10' max-width='100' y='40'/>
              <panel id='d' width='100' height='10' min-width='80' max-width='50' y='60'/>
            </screen>""");
        assertEquals(120, d.findById("a").w);
        assertEquals(60, d.findById("b").w);
        assertEquals(100, d.findById("c").w);               // 50% of 400 = 200 → max 100
        assertEquals(80, d.findById("d").w);                // min 이 max 보다 우선
    }

    @Test
    void minAndMaxHeightAndPercentLimits() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='a' width='10' height='300' max-height='50%'/>
              <panel id='b' x='100' width='10' height='5' min-height='25'/>
            </screen>""");
        assertEquals(100, d.findById("a").h);               // 화면 높이 200 의 50%
        assertEquals(25, d.findById("b").h);
    }

    @Test
    void minAndMaxInFlowAndFill() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row id='r' width='300' height='20'>
                <panel id='a' width='fill' max-width='50'/>
                <panel id='b' width='fill'/>
                <panel id='c' width='10' min-width='30'/>
              </row>
            </screen>""");
        assertEquals(50, d.findById("a").w);
        assertEquals(30, d.findById("c").w);
        assertEquals(220, d.findById("b").w);              // 마지막 fill 이 나머지를 받는다
    }

    @Test
    void crossAxisClamp() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row width='200' height='100' align='stretch'>
                <panel id='a' width='10' max-height='40'/>
                <panel id='b' width='10' height='5' min-height='20'/>
              </row>
            </screen>""");
        assertEquals(40, d.findById("a").h);
        assertEquals(20, d.findById("b").h);
    }

    @Test
    void boxSizingContentBoxAddsPadding() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='a' width='100' height='50' padding='10'/>
              <panel id='b' x='150' width='100' height='50' padding='10' box-sizing='content-box'/>
              <panel id='c' x='0' y='100' width='100' height='50' padding='10 20' box-sizing='content-box' max-width='100'/>
            </screen>""");
        assertEquals(100, d.findById("a").w);
        assertEquals(120, d.findById("b").w);
        assertEquals(70, d.findById("b").h);
        assertEquals(140, d.findById("c").w);               // max-width 도 content-box 기준(100 + 40)
    }

    @Test
    void unitsResolveBeforeLayout() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>#a { width: calc(25vw + 2em); min-height: 3em; height: 1 }</style>
              <panel id='a'/>
            </screen>""");
        assertEquals(116, d.findById("a").w);
        assertEquals(24, d.findById("a").h);
    }
}
